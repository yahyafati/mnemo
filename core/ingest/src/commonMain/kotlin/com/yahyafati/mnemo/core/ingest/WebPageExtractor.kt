package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/**
 * A link's readable text: the article of a web page as Markdown (without menus, footers and scripts), a
 * plain text file, or a PDF. A [SiteExtractor] that claims the link reads it by its own rules; otherwise the
 * page is read by [GenericExtractor] (ADR 0012). Nothing runs from the page; it is only parsed. Pages that
 * need JavaScript to show anything (video sites, apps) have no text and fail with [SourceProblem.NoText].
 * Blocking; call it off the main thread.
 *
 * Unlike AI requests, this follows redirects (short links, http → https): nothing secret is sent.
 */
class WebPageExtractor(
    client: OkHttpClient,
    private val pdf: PdfTextExtractor,
    private val sites: List<SiteExtractor> = emptyList(),
    userAgent: String = PageFetcher.USER_AGENT,
) {
    private val fetcher = PageFetcher(client, userAgent)

    fun extract(link: String): SourceResult {
        val url = parseUrl(link) ?: return SourceResult.Failure(SourceProblem.InvalidUrl)
        // The first site that claims the link; its failure is final, only null ("not after all") falls through.
        sites.firstOrNull { it.handles(url) }?.extract(url, fetcher)?.let { return it }
        return when (val fetched = fetcher.fetch(url)) {
            is PageFetcher.Fetched.Failed -> fetched.failure
            is PageFetcher.Fetched.Page -> read(fetched)
        }
    }

    private fun read(page: PageFetcher.Fetched.Page): SourceResult {
        val type = page.contentType
        return when {
            type?.subtype == "pdf" || (type == null && page.bytes.startsWithPdfMagic()) ->
                pdf.extract(page.bytes.inputStream(), page.url.pathSegments.lastOrNull { it.isNotEmpty() })
            type == null || type.subtype in HTML_TYPES -> GenericExtractor.html(page)
            type.type == "text" -> PageText.plain(page.text(), title = null)
            else -> SourceResult.Failure(SourceProblem.Unsupported, type.toString())
        }
    }

    private fun ByteArray.startsWithPdfMagic() = size >= 5 && String(this, 0, 5, Charsets.ISO_8859_1) == "%PDF-"

    companion object {
        const val MAX_BYTES = PageFetcher.MAX_BYTES
        const val MAX_CHARS = 400_000

        private val HTML_TYPES = setOf("html", "xhtml+xml")

        /** [link] as an http(s) URL; a bare host gets `https://`. */
        fun parseUrl(link: String): HttpUrl? {
            val trimmed = link.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return withScheme.toHttpUrlOrNull()?.takeIf { '.' in it.host || it.host == "localhost" }
        }
    }
}
