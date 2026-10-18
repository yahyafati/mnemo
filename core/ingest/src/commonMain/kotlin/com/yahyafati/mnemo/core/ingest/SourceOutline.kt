package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount

/**
 * The Markdown headings of Smart Extract's text as a tree (ADR 0015, C2), for [SourcePlanner]. Lines inside fenced
 * blocks are never headings, as in [MarkdownSections], which keeps its own job (the section picker and its minimum
 * of three headings). A text without headings is the [root] alone.
 */
class SourceOutline private constructor(val root: Section) {
    /**
     * A heading, what it says before its first subsection, and its subsections. The [root] has no heading: its
     * [title] is null, its [level] 0 and its [body] the text before the first heading.
     */
    class Section internal constructor(
        /** The heading without its marks (`## **Nash** equilibrium ##` → `Nash equilibrium`). */
        val title: String?,
        /** 1–6 for `#`–`######`; 0 for the root. */
        val level: Int,
        /** The text between the heading line and the first subsection (or the section's end). */
        val body: String,
        /** The heading line and [body]: what this section says itself. */
        val own: String,
        /** The whole section: heading, body and every subsection, as written. */
        val text: String,
        val children: List<Section>,
    ) {
        val bodyWords: Int = WordCount.count(body)
        val ownWords: Int = WordCount.count(own)
        val words: Int = WordCount.count(text)
    }

    companion object {
        fun of(markdown: String): SourceOutline {
            val text = markdown.replace("\r\n", "\n")
            val root = Builder(title = null, level = 0, start = 0, bodyStart = 0)
            val open = ArrayDeque<Builder>().apply { addLast(root) }
            var fence: String? = null
            var offset = 0
            for (line in text.split('\n')) {
                val mark = FENCE.find(line)?.groupValues?.get(1)
                val current = fence
                if (current != null) {
                    if (mark != null && mark[0] == current[0] && mark.length >= current.length && line.trim() == mark) fence = null
                } else if (mark != null) {
                    fence = mark
                } else {
                    HEADING.matchEntire(line)?.let { match ->
                        val level = match.groupValues[1].length
                        while (open.last().level >= level) open.removeLast().end = offset
                        val parent = open.last()
                        if (parent.children.isEmpty()) parent.bodyEnd = offset
                        val section = Builder(title(match.groupValues[2]), level, start = offset, bodyStart = minOf(offset + line.length + 1, text.length))
                        parent.children += section
                        open.addLast(section)
                    }
                }
                offset += line.length + 1
            }
            while (open.isNotEmpty()) open.removeLast().end = text.length
            return SourceOutline(root.build(text))
        }

        /**
         * A heading's text as a title: no closing hashes, links reduced to their text, no emphasis or code marks. Not
         * [MarkdownText.plain], which would take `1.` off `1. Introduction` as a list marker.
         */
        private fun title(raw: String): String = raw.replace(CLOSING_HASHES, "")
            .replace(LINK, "$1")
            .replace(ESCAPES, "$1")
            .replace(INLINE_MARKS, "")
            .replace(SPACES, " ")
            .trim()
            .ifEmpty { raw.trim() }

        private val HEADING = Regex("""^(#{1,6}) +(.+)$""")
        private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
        private val CLOSING_HASHES = Regex("""(?:^|\s+)#+\s*$""")
        private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
        private val ESCAPES = Regex("""\\([!-/:-@\[-`{-~])""")
        private val INLINE_MARKS = Regex("""\*\*|__|~~|[*`]""")
        private val SPACES = Regex("""\s+""")
    }

    private class Builder(val title: String?, val level: Int, val start: Int, val bodyStart: Int) {
        var bodyEnd: Int = -1
        var end: Int = -1
        val children = mutableListOf<Builder>()

        fun build(text: String): Section {
            val bodyEnd = if (children.isEmpty()) end else bodyEnd
            return Section(
                title = title,
                level = level,
                body = text.substring(bodyStart.coerceAtMost(bodyEnd), bodyEnd).trim(),
                own = text.substring(start, bodyEnd).trim(),
                text = text.substring(start, end).trim(),
                children = children.map { it.build(text) },
            )
        }
    }
}
