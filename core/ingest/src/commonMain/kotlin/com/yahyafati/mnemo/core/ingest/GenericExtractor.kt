package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceSection
import com.yahyafati.mnemo.core.model.SourceText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Turns the text an extractor read into a [SourceResult]: tidied, limited to [WebPageExtractor.MAX_CHARS], or [SourceProblem.NoText]. */
internal object PageText {
    /** Markdown from [MarkdownText]: leading indentation and fenced blocks are kept. */
    fun markdown(raw: String, title: String?): SourceResult = finish(TextCleanup.normalizeMarkdown(raw), title)

    /**
     * Markdown with [sections], ranges of [raw]. They are kept only if tidying leaves [raw] as it is (the offsets
     * would not fit otherwise), and cut where [WebPageExtractor.MAX_CHARS] cuts the text.
     */
    fun markdown(raw: String, title: String?, sections: List<SourceSection>): SourceResult {
        val text = TextCleanup.normalizeMarkdown(raw)
        val result = finish(text, title)
        if (result !is SourceResult.Success || text != raw) return result
        val length = result.source.text.length
        val kept = sections.filter { it.start < length }.map { it.copy(end = minOf(it.end, length)) }
        return SourceResult.Success(result.source.copy(sections = kept))
    }

    /** Plain text (a text file): [TextCleanup.normalize]. */
    fun plain(raw: String, title: String?): SourceResult = finish(TextCleanup.normalize(raw), title)

    private fun finish(text: String, title: String?): SourceResult {
        if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
        val max = WebPageExtractor.MAX_CHARS
        return SourceResult.Success(SourceText(text.take(max), title, truncated = text.length > max))
    }
}

/**
 * Any HTML page, without site rules: the element [ContentFinder] scores best (else the biggest of the page's
 * content elements, `article`, `main` …, else its body), minus menus, footers, scripts and the like, written as
 * Markdown ([MarkdownText]). A page with at least [MarkdownSections.MIN_HEADINGS] headings gets them as sections.
 * The fallback for every link no [SiteExtractor] claims.
 */
internal object GenericExtractor {
    fun html(page: PageFetcher.Fetched.Page): SourceResult {
        val document = Jsoup.parse(page.bytes.inputStream(), page.charset?.name(), page.url.toString())
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.title().trim().takeIf { it.isNotEmpty() }
        document.select(NOISE).remove()
        val body = document.body()
        val root = body?.let { ContentFinder.find(it) } ?: largestContentElement(document) ?: return SourceResult.Failure(SourceProblem.NoText)
        val markdown = MarkdownText.of(root).let { if (it.length < MIN_ARTICLE_CHARS) MarkdownText.of(body ?: root) else it }
        val text = TextCleanup.normalizeMarkdown(markdown).trim()
        return PageText.markdown(text, title, MarkdownSections.of(text))
    }

    private fun largestContentElement(document: Document): Element? =
        document.select(ContentFinder.CONTENT_ROOTS).ifEmpty { listOfNotNull(document.body()) }.maxByOrNull { it.text().length }

    private const val MIN_ARTICLE_CHARS = 200
    private const val NOISE =
        "script, style, noscript, template, svg, canvas, iframe, object, embed, form, button, input, select, nav, footer, aside, " +
            "[role=navigation], [role=banner], [role=contentinfo], [role=complementary], [aria-hidden=true], [hidden], " +
            ".cookie, .cookies, .cookie-banner, #cookie-banner, .advert, .ads, .share, .social, .sidebar, .related, .comments, #comments, " +
            ".newsletter, .breadcrumb, .breadcrumbs, .pagination, .popup"
}
