package com.yahyafati.mnemo.core.ingest.site

import com.yahyafati.mnemo.core.ingest.MarkdownText
import com.yahyafati.mnemo.core.ingest.TextCleanup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A Wikipedia article (Parsoid HTML) as Markdown, with the sections it is made of.
 *
 * Left out: references and their markers, infobox, navboxes, hatnotes, message boxes, edit links, hidden
 * metadata, images with their captions (a caption without its picture says little) and the end matter:
 * sections named References, Notes, Citations, Sources, Further reading, External links or See also, and,
 * because those titles differ by language, any section that holds only a list of links or nothing at all.
 * A disambiguation page is all lists of links, so none of its sections is dropped for that.
 */
internal class WikipediaArticle(
    /** From the page's `<title>`, so a redirect names its target; null when the page has none. */
    val title: String?,
    val text: String,
    /** In document order; a subsection follows its parent and starts where its own text starts. */
    val sections: List<Section>,
) {
    /**
     * One section's own text (its heading and what is above its first subsection) as the range [start], [end]
     * of [WikipediaArticle.text]. [title] is null for the lead, whose [level] is 0; an `h2` section is 2.
     */
    class Section(val title: String?, val level: Int, val start: Int, val end: Int)

    companion object {
        fun of(document: Document): WikipediaArticle {
            val title = document.title().replace('_', ' ').trim().takeIf { it.isNotEmpty() }
            // Decided before the chrome goes: the message box that says it is removed with it.
            val disambiguation = document.selectFirst(DISAMBIGUATION) != null
            document.select(CHROME).remove()
            val body = document.body()
            if (body.selectFirst(SECTION) == null) return WikipediaArticle(title, tidy(MarkdownText.of(body)), emptyList())

            // Subsections first, so a section whose only content was dropped is empty by the time it is asked.
            for (section in body.select(SECTION).asReversed()) if (unwanted(section, disambiguation)) section.remove()

            val text = StringBuilder()
            val sections = ArrayList<Section>()
            for (section in body.select(SECTION)) {
                val own = section.clone()
                for (nested in own.select(SECTION)) if (nested !== own) nested.remove()
                val markdown = tidy(MarkdownText.of(own))
                if (markdown.isEmpty()) continue
                if (text.isNotEmpty()) text.append("\n\n")
                val start = text.length
                text.append(markdown)
                val heading = heading(section)
                sections += Section(heading?.text()?.trim(), heading?.normalName()?.drop(1)?.toIntOrNull() ?: 0, start, text.length)
            }
            return WikipediaArticle(title, text.toString(), sections)
        }

        private fun tidy(markdown: String) = TextCleanup.normalizeMarkdown(markdown).trim()

        private fun heading(section: Element) = section.children().firstOrNull { it.normalName() in HEADINGS }

        /** What the section holds besides its heading and its subsections. */
        private fun ownContent(section: Element) = section.children().filter { it.normalName() !in HEADINGS && !it.`is`(SECTION) }

        private fun unwanted(section: Element, keepLists: Boolean): Boolean {
            val hasSubsections = section.select(SECTION).any { it !== section }
            if (!keepLists && heading(section)?.text()?.trim()?.lowercase() in END_MATTER) return true
            if (hasSubsections) return false
            val own = ownContent(section)
            return own.all { it.text().isBlank() } || (!keepLists && onlyLinks(own))
        }

        /** Every list item is mostly a link, and nothing else is written: "See also", "External links" in any language. */
        private fun onlyLinks(own: List<Element>): Boolean {
            val items = own.flatMap { it.select("li") }
            if (items.isEmpty()) return false
            val outside = own.map { it.clone().also { copy -> copy.select("li").remove() } }
            if (outside.any { it.text().isNotBlank() }) return false
            return items.all { item -> item.select("a").sumOf { it.text().length } * 2 >= item.text().length }
        }

        private const val SECTION = "section[data-mw-section-id]"
        private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        private val END_MATTER = setOf(
            "references", "notes", "citations", "sources", "further reading", "external links", "see also",
            "footnotes", "bibliography",
        )

        private const val DISAMBIGUATION =
            "meta[property=\"mw:PageProp/disambiguation\"], .dmbox-disambig, link[rel=\"mw:PageProp/Category\"][href\$=Disambiguation_pages]"

        private const val CHROME =
            "style, link, meta, " +
                // citations and the lists they point to
                "sup.reference, .mw-ref, .mw-cite-backlink, ol.references, .references, .reflist, .mw-references-wrap, .refbegin, " +
                // boxes around and beside the article
                ".infobox, .navbox, .navbox-styles, .vertical-navbox, .sidebar, .side-box, .sistersitebox, .hatnote, .metadata, .ambox, " +
                // hidden or screen-only bits, and "[edit]", "[citation needed]"
                ".shortdescription, .noprint, .mw-editsection, .mw-empty-elt, #coordinates, .toc, #toc, .tocright, " +
                // pictures, whose captions are no use without them
                "figure, .thumb, .gallery"
    }
}
