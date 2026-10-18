package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.SourceOutline.Section
import com.yahyafati.mnemo.core.model.SourcePart
import com.yahyafati.mnemo.core.model.WordCount

/**
 * Splits Smart Extract's text into the parts sent one request each, along its Markdown headings (ADR 0015, C2), and
 * says where each part is. It walks the [SourceOutline] in order:
 *
 * 1. a section that fits in the room left in the current part goes in whole;
 * 2. one that doesn't, but fits in an empty part, closes the current part and starts the next;
 * 3. one bigger than a part is opened up: its own text, then its subsections, packed the same way one level down;
 * 4. a section's own text bigger than a part is cut by [TextChunker.chunk], and the pieces after the first are
 *    *continued* parts of that section;
 * 5. something outside the family of what the current part holds (a cousin, not a sibling or a child) closes the
 *    part instead when it is already half full;
 * 6. a tiny text before a section's first subsection goes with that subsection, and a short last part joins the
 *    one before it, unless rule 5 keeps them apart (a short last chapter is not added to the previous chapter's end).
 *
 * A part's path is the root, then the headings of the deepest section that holds all of it; the headings of the
 * sections it holds whole are in its text. Sizes are [WordCount]. The same text always gives the same parts.
 */
object SourcePlanner {
    /** A title in a path is cut to this many characters. */
    const val MAX_TITLE_CHARS = 80

    /** A longer path keeps its first entry and its last ones, with "…" between. */
    const val MAX_PATH_LEVELS = 6

    /**
     * [text] in parts of at most [maxWords] (a folded tail or a cut text's last piece may hold a quarter more, as with
     * [TextChunker], and the tiny texts before a first subsection an eighth). [root] is where the text itself is: the book and the chapter, or the page's or document's
     * title; empty for pasted text. A first heading that repeats the root's last entry is left out of the path.
     */
    fun plan(text: String, root: List<String> = emptyList(), maxWords: Int = TextChunker.DEFAULT_MAX_WORDS): List<SourcePart> {
        require(maxWords > 0)
        if (text.isBlank()) return emptyList()
        val packer = Packer(maxWords)
        packer.section(SourceOutline.of(text).root, emptyList())
        val parts = packer.finish()
        val rootPath = cleanRoot(root)
        return parts.map { part ->
            val headings = part.context.mapNotNull { it.title }.map(::cut)
            SourcePart(part.text, path(rootPath, headings), part.continued)
        }
    }

    private fun cleanRoot(root: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (title in root.map(::cut).filter { it.isNotEmpty() }) if (out.lastOrNull()?.let { same(it, title) } != true) out += title
        return out
    }

    private fun path(root: List<String>, headings: List<String>): List<String> {
        val inner = if (root.isNotEmpty() && headings.isNotEmpty() && same(root.last(), headings.first())) headings.drop(1) else headings
        val path = root + inner
        return if (path.size <= MAX_PATH_LEVELS) path else listOf(path.first(), ELLIPSIS) + path.takeLast(MAX_PATH_LEVELS - 2)
    }

    private fun cut(title: String): String {
        val tidy = title.replace(SPACES, " ").trim()
        return if (tidy.length <= MAX_TITLE_CHARS) tidy else tidy.take(MAX_TITLE_CHARS - 1).trimEnd() + ELLIPSIS
    }

    private fun same(a: String, b: String) = a.equals(b, ignoreCase = true)

    /**
     * A piece of text placed in a part. [context] is the chain of sections from the root down to the deepest one that
     * holds it: a whole section's parent, or for a section's own text that section itself.
     */
    private class Item(val text: String, val words: Int, val context: List<Section>, val continued: Boolean = false)

    private class Part(val items: List<Item>) {
        val context: List<Section> = items.map { it.context }.reduce(::common)
        val continued: Boolean get() = items.first().continued
        val words: Int get() = items.sumOf { it.words }
        val text: String get() = items.joinToString("\n\n") { it.text }
    }

    private class Packer(private val maxWords: Int) {
        private val parts = mutableListOf<Part>()
        private var current = mutableListOf<Item>()
        private var words = 0

        /** Tiny texts before a first subsection, waiting to go in with it (rule 6). */
        private var pending: Item? = null

        /** Packs [section], whose parents (root first) are [parents]. */
        fun section(section: Section, parents: List<Section>) {
            val item = withPending(Item(section.text, section.words, parents))
            when {
                fits(item) -> add(item.also { pending = null }) // 1
                // 2; the tiny texts in front may take it over by an eighth of a part, which is better than cutting it.
                section.words <= maxWords -> startWith(item.also { pending = null })
                else -> open(section, parents + section) // 3: the pending texts wait for what comes first inside
            }
        }

        private fun open(section: Section, chain: List<Section>) {
            when {
                section.own.isEmpty() -> Unit
                section.children.isNotEmpty() && section.bodyWords <= maxWords / TINY_FRACTION -> pending = withPending(Item(section.own, section.ownWords, chain)) // 6
                else -> own(section, chain).also { pending = null }
            }
            for (child in section.children) section(child, chain)
        }

        /** A section's heading and the text before its subsections, which the section didn't fit around. */
        private fun own(section: Section, chain: List<Section>) {
            val item = withPending(Item(section.own, section.ownWords, chain))
            when {
                fits(item) -> add(item)
                section.ownWords <= maxWords -> startWith(item)
                else -> cut(item, chain)
            }
        }

        /** 4: too long for any part. TextChunker keeps paragraphs, fences and tables whole where it can. */
        private fun cut(item: Item, chain: List<Section>) {
            for ((index, piece) in TextChunker.chunk(item.text, maxWords).withIndex()) {
                val words = WordCount.count(piece)
                if (index == 0) {
                    val first = Item(piece, words, item.context)
                    if (fits(first)) add(first) else startWith(first)
                } else {
                    startWith(Item(piece, words, chain, continued = true))
                }
            }
        }

        /**
         * [item] with the pending tiny texts in front of it; whoever places it clears [pending]. The texts are only the
         * way into [item], which is inside their sections, so the part is where [item] is.
         */
        private fun withPending(item: Item): Item {
            val before = pending ?: return item
            return Item(before.text + "\n\n" + item.text, before.words + item.words, item.context)
        }

        /** Rules 1 and 5: [item] fits in the room left, and doesn't take a cousin into a part that is half full. */
        private fun fits(item: Item): Boolean {
            if (words + item.words > maxWords) return false
            if (current.isEmpty()) return true
            val held = current.map { it.context }.reduce(::common)
            return item.context.startsWith(held) || words < maxWords / 2
        }

        private fun add(item: Item) {
            current += item
            words += item.words
        }

        private fun startWith(item: Item) {
            close()
            add(item)
        }

        private fun close() {
            if (current.isEmpty()) return
            parts += Part(current)
            current = mutableListOf()
            words = 0
        }

        fun finish(): List<Part> {
            pending?.let { add(it) }
            pending = null
            close()
            // 6: a short tail joins the part before it, which may grow by a quarter (as in TextChunker), but rule 5 holds.
            if (parts.size >= 2) {
                val last = parts.last()
                val before = parts[parts.size - 2]
                val family = last.context.startsWith(before.context) || before.words < maxWords / 2
                if (family && last.words < maxWords / TAIL_FRACTION && before.words + last.words <= maxWords + maxWords / TAIL_FRACTION) {
                    parts[parts.size - 2] = Part(before.items + last.items)
                    parts.removeAt(parts.lastIndex)
                }
            }
            return parts
        }
    }

    /** The chain both [a] and [b] start with: their deepest common section. */
    private fun common(a: List<Section>, b: List<Section>): List<Section> {
        var n = 0
        while (n < a.size && n < b.size && a[n] === b[n]) n++
        return a.subList(0, n)
    }

    private fun List<Section>.startsWith(prefix: List<Section>) = size >= prefix.size && common(this, prefix).size == prefix.size

    /** A text before a first subsection is tiny when it has at most an eighth of a part (150 words): a sentence or two. */
    private const val TINY_FRACTION = 8

    /** A last part under a quarter of a part joins the part before it. */
    private const val TAIL_FRACTION = 4

    private const val ELLIPSIS = "…"
    private val SPACES = Regex("""\s+""")
}
