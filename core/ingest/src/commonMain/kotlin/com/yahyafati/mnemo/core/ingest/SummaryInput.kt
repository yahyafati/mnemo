package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.SourceOutline.Section
import com.yahyafati.mnemo.core.model.WordCount

/**
 * What the context summary request reads (ADR 0015, C4): the whole text when it has at most [BUDGET_WORDS] words,
 * else a **skim** that costs the same however long the source is.
 *
 * A text with three or more headings is skimmed as its outline (every heading, as many as fit in a third of the
 * budget) plus the opening of each section, the text before the first heading and the last section getting a
 * larger share. A text without headings is skimmed as its first [HEAD_WORDS] words, its last [TAIL_WORDS] and evenly
 * spaced pieces between. A skim is never over the budget, and says where it left text out with `[…]`.
 */
data class SummaryInput(val text: String, val skimmed: Boolean) {
    companion object {
        /** The most words the request reads (ADR 0015, C4). */
        const val BUDGET_WORDS = 3_000

        const val HEAD_WORDS = 1_500
        const val TAIL_WORDS = 500

        /** Fewer headings than this are not an outline worth skimming by. */
        const val MIN_HEADINGS = 3

        /** A section's opening is not worth sending if it can't have this many words; then some sections are left out. */
        private const val MIN_OPENING_WORDS = 25

        /** The text before the first heading counts this many sections, the last section this many. */
        private const val LEAD_WEIGHT = 3
        private const val LAST_WEIGHT = 2

        /** The pieces a text without headings is sampled in. */
        private const val PIECE_WORDS = 100
        private const val MAX_PIECE_WORDS = PIECE_WORDS + PIECE_WORDS / 4

        private const val GAP = "[…]"

        fun of(text: String, budget: Int = BUDGET_WORDS): SummaryInput {
            require(budget > 0)
            val whole = text.trim()
            if (WordCount.count(whole) <= budget) return SummaryInput(whole, skimmed = false)
            val root = SourceOutline.of(whole).root
            val sections = flatten(root.children)
            val skim = if (sections.size >= MIN_HEADINGS) byOutline(root, sections, budget) else byPieces(whole, budget)
            return SummaryInput(skim, skimmed = true)
        }

        private fun flatten(sections: List<Section>): List<Section> = sections.flatMap { listOf(it) + flatten(it.children) }

        /** The outline and the openings of the sections. */
        private fun byOutline(root: Section, sections: List<Section>, budget: Int): String {
            val headings = sections.map { "#".repeat(it.level) + " " + it.title }
            val outlineBudget = budget / 3
            val keepHeadings = spread(headings.indices.toList(), fitting(headings, outlineBudget)).toSet()
            val outlineWords = keepHeadings.sumOf { WordCount.count(headings[it]) }

            // Entries that can show an opening: the lead (-1) and every section with a body of its own.
            val candidates = buildList {
                if (root.body.isNotBlank()) add(LEAD)
                sections.indices.filter { it in keepHeadings && sections[it].body.isNotBlank() }.forEach { add(it) }
            }
            val last = candidates.lastOrNull { it != LEAD }
            fun weight(entry: Int) = when {
                entry == LEAD -> LEAD_WEIGHT
                entry == last -> LAST_WEIGHT
                else -> 1
            }

            // Each opening ends in a gap marker, which is a word: it is reserved here so the total stays in the budget.
            val room = (budget - outlineWords - candidates.size).coerceAtLeast(0)
            var shown = candidates
            while (shown.size > 2 && room / shown.sumOf(::weight) < MIN_OPENING_WORDS) {
                // Thin out evenly, keeping the lead and the last section.
                val keep = (room / MIN_OPENING_WORDS - (LEAD_WEIGHT + LAST_WEIGHT - 2)).coerceIn(2, shown.size - 1)
                shown = spread(shown, keep)
            }
            val perWeight = if (shown.isEmpty()) 0 else room / shown.sumOf(::weight)

            val out = mutableListOf<String>()
            fun opening(body: String, entry: Int) {
                val allowance = perWeight * weight(entry)
                if (allowance > 0 && entry in shown) out += excerpt(body, allowance)
            }
            opening(root.body, LEAD)
            for (index in sections.indices) {
                if (index !in keepHeadings) continue
                out += headings[index]
                opening(sections[index].body, index)
            }
            return out.joinToString("\n\n")
        }

        /** The first paragraphs of [body] up to [allowance] words (the first one cut at a sentence if it alone is longer), with a gap marker if anything is left. */
        private fun excerpt(body: String, allowance: Int): String {
            val taken = StringBuilder()
            var words = 0
            var complete = true
            for (paragraph in body.split(PARAGRAPH).filter { it.isNotBlank() }) {
                val size = WordCount.count(paragraph)
                if (words + size <= allowance) {
                    if (taken.isNotEmpty()) taken.append("\n\n")
                    taken.append(paragraph.trim())
                    words += size
                    continue
                }
                // A chunk may take a short tail on, up to a quarter more, so ask for a smaller one to stay inside the allowance.
                if (taken.isEmpty()) taken.append(TextChunker.chunk(paragraph, (allowance * 4 / 5).coerceAtLeast(1)).first().trim())
                complete = false
                break
            }
            return if (complete) taken.toString() else "$taken $GAP"
        }

        /** A text without headings: its start, its end and pieces spaced evenly between. */
        private fun byPieces(text: String, budget: Int): String {
            val pieces = TextChunker.chunk(text, PIECE_WORDS)
            val sizes = pieces.map(WordCount::count)

            var head = 0
            var headWords = 0
            while (head < pieces.size && headWords + sizes[head] <= HEAD_WORDS) headWords += sizes[head++]
            var tail = pieces.size
            var tailWords = 0
            while (tail > head && tailWords + sizes[tail - 1] <= TAIL_WORDS) tailWords += sizes[--tail]

            val middle = (head until tail).toList()
            var samples = centered(middle, ((budget - headWords - tailWords) / (MAX_PIECE_WORDS + 1)).coerceAtLeast(0))
            fun compose(samples: List<Int>): String {
                val chosen = (0 until head).toList() + samples + (tail until pieces.size)
                val sb = StringBuilder()
                var previous = -1
                for (index in chosen) {
                    if (sb.isNotEmpty()) sb.append("\n\n")
                    if (previous >= 0 && index != previous + 1) sb.append(GAP).append("\n\n")
                    sb.append(pieces[index].trim())
                    previous = index
                }
                return sb.toString()
            }
            var text = compose(samples)
            while (samples.isNotEmpty() && WordCount.count(text) > budget) {
                samples = centered(middle, samples.size - 1)
                text = compose(samples)
            }
            return text
        }

        /** The largest number of [lines] from the start of the list that fit in [budget] words, or all of them. */
        private fun fitting(lines: List<String>, budget: Int): Int {
            var words = 0
            return lines.count { line -> (words + WordCount.count(line)).also { words = it } <= budget }.coerceAtLeast(2)
        }

        /** [count] items of [items] spaced evenly, always including the first and, when there are two or more, the last. */
        private fun <T> spread(items: List<T>, count: Int): List<T> {
            if (count >= items.size) return items
            if (count <= 0) return emptyList()
            if (count == 1) return listOf(items.first())
            return (0 until count).map { items[(it.toLong() * (items.size - 1) / (count - 1)).toInt()] }
        }

        /** [count] items of [items], each from the middle of its share of the list, so none sits next to the ends. */
        private fun <T> centered(items: List<T>, count: Int): List<T> {
            if (count >= items.size) return items
            if (count <= 0) return emptyList()
            return (0 until count).map { items[((2L * it + 1) * items.size / (2L * count)).toInt()] }
        }

        private const val LEAD = -1
        private val PARAGRAPH = Regex("""\n\s*\n""")
    }
}
