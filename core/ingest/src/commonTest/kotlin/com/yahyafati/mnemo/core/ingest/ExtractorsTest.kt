package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtractorsTest : PlatformTest() {
    private val pdf = newPdfExtractor()
    private val web = WebPageExtractor(OkHttpClient(), pdf)
    private val server = MockWebServer()

    private fun fixture(name: String): String {
        val stream = ExtractorsTest::class.java.getResourceAsStream("/web/$name") ?: error("missing fixture $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @BeforeTest
    fun setUp() = server.start()

    @AfterTest
    fun tearDown() = server.close()

    private fun html(body: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun success(result: SourceResult) = assertIs<SourceResult.Success>(result).source

    @Test
    fun articleTextWithoutTheChrome() {
        server.enqueue(
            html(
                """
                <html><head><title>Fallback title</title><meta property="og:title" content="The Amygdala">
                <script>var tracking = "not text";</script><style>p { color: red }</style></head>
                <body>
                  <nav><a href="/">Home</a> <a href="/about">About</a></nav>
                  <div class="cookie-banner">We use cookies</div>
                  <article>
                    <h1>The Amygdala</h1>
                    <p>The amygdala processes <b>emotional</b> memories, especially fear.</p>
                    <p>Its basolateral complex receives sensory input.</p>
                    <ul><li>Central nucleus</li><li>Basolateral complex</li></ul>
                  </article>
                  <footer>© 2026 Example</footer>
                </body></html>
                """.trimIndent(),
            ),
        )
        val source = success(web.extract(server.url("/page").toString()))
        assertEquals("The Amygdala", source.title)
        assertTrue("The amygdala processes **emotional** memories, especially fear." in source.text)
        assertTrue("# The Amygdala\n\nThe amygdala" in source.text)
        assertTrue("\n- Central nucleus\n- Basolateral complex" in source.text)
        for (noise in listOf("Home", "cookies", "tracking", "color", "2026")) assertFalse(noise in source.text, noise)
    }

    @Test
    fun plainTextAndErrors() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/plain").body("Just notes.\n\n\nMore notes.").build())
        assertEquals("Just notes.\n\nMore notes.", success(web.extract(server.url("/notes.txt").toString())).text)

        server.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(SourceProblem.HttpError, assertIs<SourceResult.Failure>(web.extract(server.url("/missing").toString())).problem)

        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "image/png").body(Buffer().write(ByteArray(10))).build())
        assertEquals(SourceProblem.Unsupported, assertIs<SourceResult.Failure>(web.extract(server.url("/a.png").toString())).problem)

        server.enqueue(html("<html><body><div id=app></div><script>render()</script></body></html>"))
        assertEquals(SourceProblem.NoText, assertIs<SourceResult.Failure>(web.extract(server.url("/app").toString())).problem)

        assertEquals(SourceProblem.InvalidUrl, assertIs<SourceResult.Failure>(web.extract("not a url")).problem)
        assertEquals(SourceProblem.InvalidUrl, assertIs<SourceResult.Failure>(web.extract("ftp://example.com/x")).problem)
    }

    @Test
    fun urlsWithoutASchemeGetHttps() {
        assertEquals("https://en.wikipedia.org/wiki/Amygdala", WebPageExtractor.parseUrl(" en.wikipedia.org/wiki/Amygdala ").toString())
        assertEquals(null, WebPageExtractor.parseUrl("amygdala"))
    }

    @Test
    fun pdfTextLayerFromAFileOrALink() {
        val bytes = makePdf("Mitochondria make ATP.", "Ribosomes make proteins.")
        val fromFile = success(pdf.extract(bytes.inputStream(), fileName = "cells.pdf"))
        assertEquals("cells.pdf", fromFile.title)
        assertTrue("Mitochondria make ATP." in fromFile.text && "Ribosomes make proteins." in fromFile.text)

        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/pdf").body(Buffer().write(bytes)).build())
        assertTrue("Ribosomes" in success(web.extract(server.url("/files/cells.pdf").toString())).text)
    }

    @Test
    fun notAPdf() {
        val result = pdf.extract("hello".byteInputStream())
        assertEquals(SourceProblem.Unsupported, assertIs<SourceResult.Failure>(result).problem)
        assertEquals(SourceProblem.NoText, assertIs<SourceResult.Failure>(pdf.extract(makePdf().inputStream())).problem)
    }

    @Test
    fun newsArticleKeepsItsHeadingsListAndCaption() {
        server.enqueue(html(fixture("news-article.html")))
        val source = success(web.extract(server.url("/news").toString()))
        assertEquals("City council approves new tram line", source.title)
        assertTrue("# City council approves new tram line" in source.text)
        assertTrue("voted **9 to 4** on Thursday" in source.text)
        assertTrue("## What happens next" in source.text)
        assertTrue("start in *spring*, after the tender closes" in source.text, source.text)
        assertTrue("\n- Tracks and stations: 120 million\n- Depot: 18 million" in source.text)
        assertTrue("An artist's impression of the new line." in source.text)
        for (noise in listOf("Gazette", "We use cookies", "Sport", "dataLayer")) assertFalse(noise in source.text, noise)
    }

    @Test
    fun documentationKeepsCodeNestedListsAndTables() {
        server.enqueue(html(fixture("docs-page.html")))
        val text = success(web.extract(server.url("/docs/coroutines").toString())).text
        assertTrue("```kotlin\nfun main() = runBlocking {\n    launch {\n        delay(1000L)\n\n        println(\"World!\")" in text, text)
        assertTrue("- Structured concurrency\n  - A parent waits for its children" in text, text)
        assertTrue("  1. `Dispatchers.Default`\n  2. `Dispatchers.IO`" in text, text)
        assertTrue("| Builder | Returns |" in text, text)
        assertTrue("a \\| b" in text, text)
        assertFalse("Getting started" in text)
        assertFalse("Last updated" in text)
    }

    @Test
    fun blogEntryContentKeepsTheArticleAndLosesTheChrome() {
        server.enqueue(html(fixture("blog-entry-content.html")))
        val blog = success(web.extract(server.url("/blog").toString()))
        assertTrue("2,000" in blog.text && "## What I do now" in blog.text, blog.text)
        assertTrue("1. Words alone have no *context*." in blog.text, blog.text)
        for (noise in listOf("Archives", "Share on Social", "Great post", "Powered by")) assertFalse(noise in blog.text, noise)
    }

    @Test
    fun divSoupIsReadByItsProseNotItsChrome() {
        server.enqueue(html(fixture("div-soup.html")))
        val soup = success(web.extract(server.url("/soup").toString()))
        assertEquals("Photosynthesis explained", soup.title)
        assertTrue("Calvin cycle" in soup.text && "chloroplasts" in soup.text)
        for (noise in listOf("ScienceStuff", "Ad: buy", "Follow us", "Copyright", "Home")) assertFalse(noise in soup.text, noise)
    }

    @Test
    fun aStoryInDivsBeatsLinkListsAndReaderNotes() {
        server.enqueue(html(fixture("div-article.html")))
        val text = success(web.extract(server.url("/tides").toString())).text
        assertTrue("two high tides a day" in text && "spring tides" in text, text)
        for (noise in listOf("Weather", "lighthouses", "Reader note", "since 1998")) assertFalse(noise in text, noise)
    }

    @Test
    fun aPageWithSeveralHeadingsGetsSections() {
        server.enqueue(html(fixture("guide-with-sections.html")))
        val source = success(web.extract(server.url("/guide").toString()))
        assertEquals(listOf("Setting up a build", "Install the tools", "Write the script", "Options", "Run it on every change"), source.sections.map { it.title })
        assertEquals(listOf(1, 2, 2, 3, 2), source.sections.map { it.level })
        assertEquals(source.sections.indices.toList(), source.sections.map { it.id })
        assertTrue(source.textOf(source.sections[2]).let { it.startsWith("## Write the script") && "# compile the sources" in it && "./app --test" in it })
        assertTrue(source.textOf(source.sections.last()).endsWith("a source file is saved."))
    }

    @Test
    fun aPageWithFewHeadingsHasNoSections() {
        server.enqueue(html(fixture("news-article.html")))
        assertTrue(success(web.extract(server.url("/news").toString())).sections.isEmpty())
    }

    @Test
    fun requestsNameTheAppAndItsRepository() {
        server.enqueue(html("<html><body><p>${"Some words in a page. ".repeat(20)}</p></body></html>"))
        success(web.extract(server.url("/").toString()))
        val agent = server.takeRequest().headers["User-Agent"]
        assertEquals("Mnemo (+https://github.com/yahyafati/mnemo)", agent)
    }

    private class FakeSite(
        private val path: String,
        private val result: (PageFetcher) -> SourceResult?,
    ) : SiteExtractor {
        var calls = 0

        override fun handles(url: HttpUrl) = url.encodedPath.startsWith(path)

        override fun extract(url: HttpUrl, fetcher: PageFetcher): SourceResult? {
            calls++
            return result(fetcher)
        }
    }

    private val longPage = "<html><body><article><p>${"The generic page text. ".repeat(20)}</p></article></body></html>"

    @Test
    fun aSiteReadsItsLinksAndTheRestStaysGeneric() {
        val site = FakeSite("/wiki/") { SourceResult.Success(com.yahyafati.mnemo.core.model.SourceText("From the site", "Site")) }
        val extractor = WebPageExtractor(OkHttpClient(), pdf, sites = listOf(site))
        assertEquals("From the site", success(extractor.extract(server.url("/wiki/Amygdala").toString())).text)
        assertEquals(0, server.requestCount)

        server.enqueue(html(longPage))
        assertTrue("generic page text" in success(extractor.extract(server.url("/other").toString())).text)
        assertEquals(1, site.calls)
    }

    @Test
    fun aSiteThatReturnsNullFallsThroughToTheGenericPath() {
        val site = FakeSite("/wiki/") { null }
        val extractor = WebPageExtractor(OkHttpClient(), pdf, sites = listOf(site))
        server.enqueue(html(longPage))
        assertTrue("generic page text" in success(extractor.extract(server.url("/wiki/Talk:Amygdala").toString())).text)
        assertEquals(1, site.calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aSitesFailureIsReturnedNotRetriedGenerically() {
        val site = FakeSite("/wiki/") { SourceResult.Failure(SourceProblem.NoText, "broken") }
        val extractor = WebPageExtractor(OkHttpClient(), pdf, sites = listOf(site))
        val failure = assertIs<SourceResult.Failure>(extractor.extract(server.url("/wiki/Amygdala").toString()))
        assertEquals(SourceProblem.NoText, failure.problem)
        assertEquals("broken", failure.detail)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aSiteFetchesThroughTheSharedFetcher() {
        server.enqueue(html("<p>x</p>"))
        val site = FakeSite("/api/") { fetcher ->
            when (val fetched = fetcher.fetch(server.url("/api/page"))) {
                is PageFetcher.Fetched.Failed -> fetched.failure
                is PageFetcher.Fetched.Page -> PageText.markdown(fetched.text(), "Fetched")
            }
        }
        val extractor = WebPageExtractor(OkHttpClient(), pdf, sites = listOf(site))
        assertEquals("<p>x</p>", success(extractor.extract(server.url("/api/page").toString())).text)
        assertEquals("Mnemo (+https://github.com/yahyafati/mnemo)", server.takeRequest().headers["User-Agent"])

        server.enqueue(MockResponse.Builder().code(503).build())
        val failure = assertIs<SourceResult.Failure>(extractor.extract(server.url("/api/page").toString()))
        assertEquals(SourceProblem.HttpError, failure.problem)
    }
}
