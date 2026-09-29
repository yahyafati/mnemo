package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * A link's readable text: the article of a web page (without menus, footers and scripts), a plain
 * text file, or a PDF. Nothing runs from the page; it is only parsed. Pages that need JavaScript
 * to show anything (video sites, apps) have no text and fail with [SourceProblem.NoText].
 * Blocking; call it off the main thread.
 *
 * Unlike AI requests, this follows redirects (short links, http → https): nothing secret is sent.
 */
class WebPageExtractor(client: OkHttpClient, private val pdf: PdfTextExtractor) {
    private val client = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(true)
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    fun extract(link: String): SourceResult {
        val url = parseUrl(link) ?: return SourceResult.Failure(SourceProblem.InvalidUrl)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,application/pdf;q=0.8,*/*;q=0.5")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return SourceResult.Failure(SourceProblem.HttpError, "HTTP ${response.code}")
                val body = response.body
                val source = body.source()
                // Read at most one byte past the limit, so a huge page fails without being downloaded.
                if (source.request(MAX_BYTES + 1)) return SourceResult.Failure(SourceProblem.TooLarge)
                val bytes = source.readByteArray()
                val type = body.contentType()
                val finalUrl = response.request.url
                when {
                    type?.subtype == "pdf" || (type == null && bytes.startsWithPdfMagic()) ->
                        pdf.extract(bytes.inputStream(), finalUrl.pathSegments.lastOrNull { it.isNotEmpty() })
                    type == null || type.subtype in HTML_TYPES -> html(bytes, type?.charset(), finalUrl)
                    type.type == "text" -> text(String(bytes, type.charset() ?: Charsets.UTF_8), title = null)
                    else -> SourceResult.Failure(SourceProblem.Unsupported, type.toString())
                }
            }
        } catch (e: IOException) {
            SourceResult.Failure(SourceProblem.Unreachable, e.message)
        }
    }

    private fun html(bytes: ByteArray, charset: Charset?, url: HttpUrl): SourceResult {
        val document = Jsoup.parse(bytes.inputStream(), charset?.name(), url.toString())
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
            ?: document.title().trim().takeIf { it.isNotEmpty() }
        document.select(NOISE).remove()
        val candidates = document.select(CONTENT_ROOTS).ifEmpty { listOfNotNull(document.body()) }
        val root = candidates.maxByOrNull { it.text().length } ?: return SourceResult.Failure(SourceProblem.NoText)
        val text = readableText(root).let { if (it.length < MIN_ARTICLE_CHARS) readableText(document.body() ?: root) else it }
        return text(text, title)
    }

    private fun text(raw: String, title: String?): SourceResult {
        val text = TextCleanup.normalize(raw)
        if (text.isBlank()) return SourceResult.Failure(SourceProblem.NoText)
        return SourceResult.Success(SourceText(text.take(MAX_CHARS), title, truncated = text.length > MAX_CHARS))
    }

    /** The element's text with paragraph breaks where blocks are, and list items marked. */
    private fun readableText(root: Element): String {
        val out = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is TextNode -> {
                            val parent = node.parent() as? Element
                            if (parent != null && parent.closest("pre") != null) {
                                out.append(node.wholeText)
                            } else {
                                val text = node.text()
                                if (text.isNotBlank()) {
                                    if (out.isNotEmpty() && !out.last().isWhitespace() && text.first().isWhitespace()) out.append(' ')
                                    out.append(text.trim())
                                    if (text.last().isWhitespace()) out.append(' ')
                                }
                            }
                        }
                        is Element -> when (node.normalName()) {
                            "br" -> out.append('\n')
                            "li" -> out.append("\n- ")
                            "tr" -> out.append('\n')
                            "td", "th" -> out.append(" | ")
                            in BLOCKS -> out.append("\n\n")
                        }
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element && node.normalName() in BLOCKS) out.append("\n\n")
                }
            },
            root,
        )
        return out.toString()
    }

    private fun ByteArray.startsWithPdfMagic() = size >= 5 && String(this, 0, 5, Charsets.ISO_8859_1) == "%PDF-"

    companion object {
        const val MAX_BYTES = 8L * 1024 * 1024
        const val MAX_CHARS = 400_000
        private const val CALL_TIMEOUT_SECONDS = 30L
        private const val MIN_ARTICLE_CHARS = 200
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) Mnemo/1.0"

        private val HTML_TYPES = setOf("html", "xhtml+xml")
        private val BLOCKS = setOf(
            "p", "div", "section", "article", "main", "blockquote", "pre", "ul", "ol", "dl", "dt", "dd", "table",
            "h1", "h2", "h3", "h4", "h5", "h6", "figure", "figcaption", "header", "hr",
        )
        private const val NOISE =
            "script, style, noscript, template, svg, canvas, iframe, object, embed, form, button, input, select, nav, footer, aside, " +
                "[role=navigation], [role=banner], [role=contentinfo], [role=complementary], [aria-hidden=true], [hidden], " +
                ".cookie, .cookies, .cookie-banner, #cookie-banner, .advert, .ads, .share, .social, .sidebar, .related, .comments, #comments"
        private const val CONTENT_ROOTS = "article, main, [role=main], #content, #main-content, .post-content, .entry-content, .article-body, #mw-content-text"

        /** [link] as an http(s) URL; a bare host gets `https://`. */
        fun parseUrl(link: String): HttpUrl? {
            val trimmed = link.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
            return withScheme.toHttpUrlOrNull()?.takeIf { '.' in it.host || it.host == "localhost" }
        }
    }
}
