package com.yahyafati.mnemo.core.model

/** Picking [SourceSection]s: which ones a link's `#fragment` means. */
object SourceSections {
    /**
     * The ids of the section the link's [fragment] names, with the subsections under it (the ones that follow
     * with a deeper level), or null when the fragment is blank or names none. A fragment is a title with
     * underscores for spaces, possibly percent-encoded (`Hemispheric_specializations`, `Gef%C3%BChl`); it is
     * compared ignoring case.
     */
    fun forFragment(sections: List<SourceSection>, fragment: String): Set<Int>? {
        val wanted = normalize(decode(fragment.removePrefix("#")))
        if (wanted.isEmpty()) return null
        val index = sections.indexOfFirst { it.title != null && normalize(it.title) == wanted }
        if (index < 0) return null
        val level = sections[index].level
        val ids = mutableSetOf(sections[index].id)
        for (next in sections.drop(index + 1)) {
            if (next.level <= level) break
            ids += next.id
        }
        return ids
    }

    /** [sections]' text in order, the selected ones only, one blank line between them. */
    fun join(source: SourceText, selected: Set<Int>): String =
        source.sections.filter { it.id in selected }.joinToString("\n\n") { source.textOf(it) }

    private fun normalize(text: String) = text.replace('_', ' ').trim().replace(Regex("\\s+"), " ").lowercase()

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** Percent-decoding as UTF-8; a malformed escape stays as written. */
    private fun decode(text: String): String {
        if ('%' !in text) return text
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val code = if (c == '%' && i + 2 < text.length) text.substring(i + 1, i + 3).takeIf { h -> h.all { it.isHexDigit() } }?.toInt(16) else null
            if (code != null) {
                out.write(code)
                i += 3
            } else {
                out.write(c.toString().encodeToByteArray())
                i++
            }
        }
        return out.toByteArray().decodeToString()
    }
}
