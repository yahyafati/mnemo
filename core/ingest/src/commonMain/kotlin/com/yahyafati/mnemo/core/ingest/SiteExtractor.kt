package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceResult
import okhttp3.HttpUrl

/**
 * A site with its own way of being read (ADR 0012), usually through its API. A plain class, built in
 * `:core:data`'s `dataModule` and handed to [WebPageExtractor] in a list; the first whose [handles] is true
 * is asked, and [GenericExtractor] reads whatever no site claims.
 *
 * Rules for an implementation (the checklist is in docs/web/ROADMAP.md, "Adding a site extractor"):
 * - Contact only the host in the link or that site's own API or raw-content host, and say which in the doc
 *   comment. No third-party readers or proxies, no login, no way round a paywall.
 * - Fetch only through the [PageFetcher] you are given (it applies the User-Agent and [PageFetcher.MAX_BYTES]),
 *   and say in the doc comment how many requests one extraction makes (at most a handful).
 * - Take the site's base URL as a constructor parameter, so tests can point it at MockWebServer.
 * - Finish with [PageText.markdown] / [PageText.plain], which apply [WebPageExtractor.MAX_CHARS].
 */
interface SiteExtractor {
    /** Whether this extractor reads [url]. Decided from the URL alone, before anything is fetched. */
    fun handles(url: HttpUrl): Boolean

    /**
     * The page's text, fetched with [fetcher]. Null means "not after all" (this page is not one the extractor
     * reads), and the generic extractor runs. A failure is returned to the user as it is, not retried
     * generically, so a broken extractor is noticed.
     */
    fun extract(url: HttpUrl, fetcher: PageFetcher): SourceResult?
}
