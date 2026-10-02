package com.yahyafati.mnemo.core.ingest.site

import com.yahyafati.mnemo.core.ingest.PageFetcher
import com.yahyafati.mnemo.core.ingest.PageText
import com.yahyafati.mnemo.core.ingest.SiteExtractor
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceSection
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

/**
 * Wikipedia articles, read from the Wikimedia REST API (`/api/rest_v1/page/html/<title>`, Parsoid HTML) instead
 * of the rendered page, whose menus and banners are large and change often (ADR 0012). The result is the
 * article as Markdown without references, infobox, navboxes, hatnotes, images or the end matter
 * ([WikipediaArticle] has the rules).
 *
 * Hosts: it reads links on `<lang>.wikipedia.org` and `<lang>.m.wikipedia.org`, and contacts only that
 * language's `<lang>.wikipedia.org`, the host the user typed (the mobile one is mapped to it). One request per
 * extraction; the API's redirect for a redirect title is followed by the [PageFetcher].
 *
 * [baseUrl] is the site of a language, replaced in tests to point at MockWebServer.
 *
 * It claims `/wiki/<Title>` and `/w/index.php?title=<Title>`; a title in another namespace (`Talk:`, `File:` …),
 * an old revision or a non-view action is left to the generic extractor. `Special:` and `Media:` links have no
 * article at all: they fail with [SourceProblem.NotAnArticle] without a request. A `#fragment` is not
 * used here: Smart Extract reads it from the link to preselect a section (W4), and gets the article's sections
 * with the text. A disambiguation page returns its lists.
 */
class WikipediaExtractor(
    private val baseUrl: (language: String) -> HttpUrl = { language -> "https://$language.wikipedia.org".toHttpUrl() },
) : SiteExtractor {
    override fun handles(url: HttpUrl): Boolean = target(url) != null

    override fun extract(url: HttpUrl, fetcher: PageFetcher): SourceResult? {
        val target = target(url) ?: return null
        if (target.special) return SourceResult.Failure(SourceProblem.NotAnArticle)
        val api = baseUrl(target.language).newBuilder()
            .addPathSegments("api/rest_v1/page/html")
            // One segment: a "/" in the title ("AC/DC") must be sent as %2F.
            .addPathSegment(target.title)
            .build()
        val page = when (val fetched = fetcher.fetch(api, accept = ACCEPT)) {
            is PageFetcher.Fetched.Failed -> return fetched.failure
            is PageFetcher.Fetched.Page -> fetched
        }
        // Not HTML after all (an error document, say): let the generic extractor have the link.
        if (page.contentType?.subtype != "html") return null
        val document = Jsoup.parse(page.bytes.inputStream(), page.charset?.name(), page.url.toString())
        val article = WikipediaArticle.of(document)
        val sections = article.sections.mapIndexed { id, it -> SourceSection(id, it.title, it.level, it.start, it.end) }
        val result = PageText.markdown(article.text, article.title ?: target.title.replace('_', ' '), sections)
        return if (result is SourceResult.Success && article.disambiguation) {
            SourceResult.Success(result.source.copy(disambiguation = true))
        } else {
            result
        }
    }

    /** [special]: `Special:` and `Media:` pages, which are tools and files, not articles. */
    private class Target(val language: String, val title: String, val special: Boolean = false)

    private fun target(url: HttpUrl): Target? {
        val language = HOST.matchEntire(url.host)?.groupValues?.get(1)?.takeIf { it !in NOT_LANGUAGES } ?: return null
        if (url.queryParameter("oldid") != null || url.queryParameter("diff") != null) return null
        if (url.queryParameter("action")?.let { it != "view" } == true) return null
        val segments = url.pathSegments
        val raw = when {
            // pathSegments are decoded, so "AC/DC" and "AC%2FDC" are the same title.
            segments.size >= 2 && segments[0] == "wiki" -> segments.drop(1).joinToString("/")
            segments.lastOrNull() == "index.php" && (segments.size == 1 || segments.size == 2 && segments[0] == "w") ->
                url.queryParameter("title")
            else -> null
        } ?: return null
        val title = raw.trim().replace(' ', '_')
        if (title.isEmpty()) return null
        return when (namespace(title)) {
            null -> Target(language, title)
            in SPECIAL -> Target(language, title, special = true)
            else -> null
        }
    }

    /** The lower-cased English namespace a title starts with, or null for an article (or another language's namespace). */
    private fun namespace(title: String): String? {
        val colon = title.indexOf(':')
        if (colon <= 0) return null
        return title.substring(0, colon).replace('_', ' ').trim().lowercase().takeIf { it in NAMESPACES }
    }

    private companion object {
        const val ACCEPT = "text/html"
        val HOST = Regex("""([a-z][a-z0-9-]*)(?:\.m)?\.wikipedia\.org""")
        val NOT_LANGUAGES = setOf("www", "m")

        val SPECIAL = setOf("special", "media")

        /** English namespace names and aliases (the main namespace has none). Other languages' are not listed. */
        val NAMESPACES = setOf(
            "media", "special", "talk", "user", "user talk", "wikipedia", "wikipedia talk", "project", "project talk", "wp", "wt",
            "file", "file talk", "image", "image talk", "mediawiki", "mediawiki talk", "template", "template talk",
            "help", "help talk", "category", "category talk", "portal", "portal talk", "draft", "draft talk",
            "timedtext", "timedtext talk", "module", "module talk",
        )
    }
}
