package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
        assertTrue("The amygdala processes emotional memories, especially fear." in source.text)
        assertTrue("\n- Central nucleus" in source.text)
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
}
