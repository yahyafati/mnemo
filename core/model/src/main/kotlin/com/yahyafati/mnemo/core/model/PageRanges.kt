package com.yahyafati.mnemo.core.model

/**
 * A set of pages of a PDF, as the Pages field of Smart Extract writes them (docs/pdf/ROADMAP.md, P1): `1-10, 14, 20-25`.
 * Pages are positions in the file, 1-based. Kept as sorted runs that don't touch, so two sets with the same pages are
 * equal however they were typed.
 */
class PageRanges private constructor(val runs: List<IntRange>) {
    /** How many pages. */
    val count: Int = runs.sumOf { it.last - it.first + 1 }

    val isEmpty: Boolean get() = runs.isEmpty()

    /** Every page in order. */
    val pages: List<Int> get() = runs.flatMap { it.toList() }

    operator fun contains(page: Int): Boolean = runs.any { page in it }

    /** The first [n] pages: what a limit lets through. */
    fun first(n: Int): PageRanges {
        var left = n
        val kept = mutableListOf<IntRange>()
        for (run in runs) {
            if (left <= 0) break
            val size = run.last - run.first + 1
            kept += if (size <= left) run else run.first until run.first + left
            left -= size
        }
        return PageRanges(kept)
    }

    /** Whether every page of [other] is in this set. */
    fun containsAll(other: PageRanges): Boolean = other.runs.all { run -> runs.any { run.first >= it.first && run.last <= it.last } }

    /** These pages and those of [other]. */
    operator fun plus(other: PageRanges): PageRanges = merge(runs + other.runs)

    /** These pages without those of [other]. */
    operator fun minus(other: PageRanges): PageRanges {
        val kept = runs.flatMap { run ->
            var pieces = listOf(run)
            for (cut in other.runs) {
                pieces = pieces.flatMap { piece ->
                    if (cut.last < piece.first || cut.first > piece.last) {
                        listOf(piece)
                    } else {
                        listOfNotNull(
                            (piece.first until cut.first).takeIf { !it.isEmpty() },
                            (cut.last + 1..piece.last).takeIf { !it.isEmpty() },
                        )
                    }
                }
            }
            pieces
        }
        return PageRanges(kept)
    }

    /** The shortest way to write these pages that [parse] reads back: `1-10, 14, 20-25`. */
    fun format(): String = runs.joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }

    override fun equals(other: Any?): Boolean = other is PageRanges && other.runs == runs

    override fun hashCode(): Int = runs.hashCode()

    override fun toString(): String = "PageRanges(${format()})"

    companion object {
        val Empty = PageRanges(emptyList())

        /** Pages 1 to [pageCount]. */
        fun all(pageCount: Int): PageRanges = if (pageCount < 1) Empty else PageRanges(listOf(1..pageCount))

        /** The given pages, in any order and with repeats. */
        fun of(pages: Collection<Int>): PageRanges = merge(pages.filter { it >= 1 }.distinct().sorted().map { it..it })

        fun of(vararg pages: Int): PageRanges = of(pages.toList())

        /** The pages of [range], or none for an empty or non-positive one. */
        fun of(range: IntRange): PageRanges = if (range.isEmpty() || range.last < 1) Empty else PageRanges(listOf(maxOf(range.first, 1)..range.last))

        /** Sorted runs from [ranges], joined where they overlap or touch. */
        private fun merge(ranges: List<IntRange>): PageRanges {
            val merged = mutableListOf<IntRange>()
            for (range in ranges.sortedBy { it.first }) {
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.last + 1) {
                    merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    merged += range
                }
            }
            return PageRanges(merged)
        }

        /**
         * Reads `1-10, 14, 20-` for a document of [pageCount] pages. Parts are separated by commas; a part is a page
         * or two pages joined by a dash (`-`, `–` or `—`); an open end (`20-`) runs to the last page; spaces anywhere
         * are ignored; repeated and overlapping parts are fine.
         */
        fun parse(text: String, pageCount: Int): PageRangesResult {
            if (text.isBlank()) return PageRangesResult.Invalid(PageRangeError.Empty)
            val ranges = mutableListOf<IntRange>()
            var index = 0
            while (true) {
                val part = parsePart(text, index, pageCount)
                if (part.error != null) return PageRangesResult.Invalid(part.error)
                ranges += part.range!!
                index = part.end
                index = skipSpaces(text, index)
                if (index >= text.length) break
                if (text[index] != ',') return PageRangesResult.Invalid(PageRangeError.Malformed(index))
                index++
            }
            return PageRangesResult.Valid(merge(ranges))
        }

        private class Part(val range: IntRange?, val error: PageRangeError?, val end: Int)

        private fun parsePart(text: String, from: Int, pageCount: Int): Part {
            var index = skipSpaces(text, from)
            val first = number(text, index) ?: return Part(null, PageRangeError.Malformed(index), index)
            index = first.end
            val afterFirst = skipSpaces(text, index)
            if (afterFirst >= text.length || text[afterFirst] != '-' && text[afterFirst] != '–' && text[afterFirst] != '—') {
                // A single page.
                if (first.value !in 1..pageCount) return Part(null, PageRangeError.OutOfRange(first.value), index)
                return Part(first.value..first.value, null, index)
            }
            index = skipSpaces(text, afterFirst + 1)
            val second = number(text, index)
            val start = first.value
            val end = second?.value ?: pageCount
            if (start !in 1..pageCount) return Part(null, PageRangeError.OutOfRange(start), index)
            if (second != null && second.value !in 1..pageCount) return Part(null, PageRangeError.OutOfRange(second.value), index)
            if (end < start) return Part(null, PageRangeError.Reversed(start, end), index)
            return Part(start..end, null, second?.end ?: index)
        }

        private class Number(val value: Int, val end: Int)

        /** The digits at [from], or null when there are none. A number too big for an [Int] is [Int.MAX_VALUE]. */
        private fun number(text: String, from: Int): Number? {
            var index = from
            var value = 0L
            while (index < text.length && text[index] in '0'..'9') {
                value = minOf(value * 10 + (text[index] - '0'), Int.MAX_VALUE.toLong())
                index++
            }
            return if (index == from) null else Number(value.toInt(), index)
        }

        private fun skipSpaces(text: String, from: Int): Int {
            var index = from
            while (index < text.length && text[index].isWhitespace()) index++
            return index
        }
    }
}

/** What is wrong with a Pages field. [Malformed]'s `at` is the position in the text of the first character that isn't understood. */
sealed interface PageRangeError {
    data object Empty : PageRangeError

    data class Malformed(val at: Int) : PageRangeError

    data class OutOfRange(val page: Int) : PageRangeError

    data class Reversed(val start: Int, val end: Int) : PageRangeError
}

sealed interface PageRangesResult {
    data class Valid(val ranges: PageRanges) : PageRangesResult

    data class Invalid(val error: PageRangeError) : PageRangesResult
}
