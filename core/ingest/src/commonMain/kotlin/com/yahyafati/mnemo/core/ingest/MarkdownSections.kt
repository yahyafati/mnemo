package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceSection

/** The sections of a Markdown text, from its headings (the generic extractor's, which has no structure but these). */
internal object MarkdownSections {
    /** A page needs this many headings to be offered as sections: with fewer there is nothing to pick. */
    const val MIN_HEADINGS = 3

    /**
     * One section per heading, from its line to the line before the next, plus the lead (level 0, no title) when text
     * comes first. Ranges of [text], which must be tidy (no leading or trailing blank lines). Empty with fewer than
     * [MIN_HEADINGS] headings.
     */
    fun of(text: String): List<SourceSection> {
        val headings = ArrayList<Heading>()
        var fence: String? = null
        var offset = 0
        for (line in text.split('\n')) {
            val open = fence
            val mark = FENCE.find(line)?.groupValues?.get(1)
            if (open != null) {
                if (mark != null && mark[0] == open[0] && mark.length >= open.length && line.trim() == mark) fence = null
            } else if (mark != null) {
                fence = mark
            } else {
                HEADING.matchEntire(line)?.let { headings += Heading(offset, it.groupValues[1].length, it.groupValues[2].trim()) }
            }
            offset += line.length + 1
        }
        if (headings.size < MIN_HEADINGS) return emptyList()

        val sections = ArrayList<SourceSection>()
        if (headings.first().start > 0) sections += SourceSection(0, null, 0, 0, end(text, 0, headings.first().start))
        for ((i, heading) in headings.withIndex()) {
            val next = headings.getOrNull(i + 1)?.start ?: text.length
            sections += SourceSection(sections.size, heading.title, heading.level, heading.start, end(text, heading.start, next))
        }
        return sections
    }

    /** Where the section ends before the blank lines that separate it from the next. */
    private fun end(text: String, start: Int, limit: Int): Int {
        var end = limit
        while (end > start && text[end - 1].isWhitespace()) end--
        return end
    }

    private class Heading(val start: Int, val level: Int, val title: String)

    private val HEADING = Regex("""^(#{1,6}) +(.+)$""")
    private val FENCE = Regex("""^ {0,3}(`{3,}|~{3,})""")
}
