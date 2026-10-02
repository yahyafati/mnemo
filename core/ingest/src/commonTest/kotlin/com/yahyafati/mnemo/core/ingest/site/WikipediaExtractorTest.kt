package com.yahyafati.mnemo.core.ingest.site

import com.yahyafati.mnemo.core.ingest.PageFetcher
import com.yahyafati.mnemo.core.ingest.PlatformTest
import com.yahyafati.mnemo.core.ingest.newPdfExtractor
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikipediaExtractorTest : PlatformTest() {
    private val server = MockWebServer()
    private val languages = ArrayList<String>()
    private val wikipedia = WikipediaExtractor { language ->
        languages += language
        server.url("/")
    }
    private val web = WebPageExtractor(OkHttpClient(), newPdfExtractor(), sites = listOf(wikipedia))

    private fun fixture(name: String): String {
        val stream = WikipediaExtractorTest::class.java.getResourceAsStream("/web/$name") ?: error("missing fixture $name")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @BeforeTest
    fun setUp() = server.start()

    @AfterTest
    fun tearDown() = server.close()

    private fun page(name: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(fixture(name)).build()

    private fun read(link: String): SourceText = assertIs<SourceResult.Success>(web.extract(link)).source

    // ---- the articles ----

    @Test
    fun anArticleWithoutReferencesBoxesOrEndMatter() {
        server.enqueue(page("amygdala.html"))
        val source = read("https://en.wikipedia.org/wiki/Amygdala")
        assertEquals("Amygdala", source.title)
        assertFalse(source.truncated)
        val text = source.text

        assertTrue(text.startsWith("The **amygdala** (/əˈmɪɡdələ/"))
        assertTrue("\n\n## Structure\n\nThirteen nuclei" in text)
        assertTrue("\n\n### Hemispheric specializations\n\nThe right and left portions" in text)
        assertTrue(text.endsWith("reward system."))
        assertFalse("[" in text, "citation markers")

        val gone = listOf(
            "For other uses", // hatnote
            "Paired structure within", // hidden short description
            "Subdivisions of the mouse", // image captions
            "Coronal",
            "Location of amygdalae", // infobox
            "NeuroNames",
            "See also", // end matter
            "Amygdala hijack",
            "Further reading",
            "LeDoux",
            "External links",
            "Wikimedia Commons", // sister-project box
            "[edit",
        )
        for (word in gone) assertFalse(word in text, word)
        assertEquals(listOf("en"), languages)
    }

    @Test
    fun theRequestGoesToTheRestApiWithTheAppsUserAgent() {
        server.enqueue(page("amygdala.html"))
        read("https://en.wikipedia.org/wiki/Amygdala")
        val request = server.takeRequest()
        assertEquals("/api/rest_v1/page/html/Amygdala", request.url.encodedPath)
        assertEquals("Mnemo (+https://github.com/yahyafati/mnemo)", request.headers["User-Agent"])
        assertEquals(1, server.requestCount)
    }

    @Test
    fun mathIsReadOnceAndWithoutTheDisplayStyleWrapper() {
        server.enqueue(page("fourier-transform.html"))
        val text = read("https://en.wikipedia.org/wiki/Fourier_transform").text

        assertTrue("on the real line, is the complex valued function \\({\\widehat {f}}(\\xi )\\), defined by" in text)
        assertTrue("\\[\\|f\\|_{1}=\\int _{\\mathbb {R} }|f(x)|\\,dx<\\infty .\\]" in text)
        assertFalse("displaystyle" in text)
        assertFalse("mathbb {R} }|f(x)|\\,dx<\\infty .\\)\\[" in text)
        // One copy: the hidden MathML and the fallback image would each have written it.
        assertEquals(1, Regex(Regex.escape("\\|f\\|_{1}=")).findAll(text).count())
    }

    @Test
    fun aDataTableStaysATableAndANumberedEquationDoesNot() {
        server.enqueue(page("fourier-transform.html"))
        val text = read("https://en.wikipedia.org/wiki/Fourier_transform").text
        assertTrue("| ordinary frequency ξ (Hz) | unitary |" in text)
        assertTrue("| --- | --- | --- |" in text)
        // The equation box is a presentation table: the formula is a paragraph, its number another.
        assertTrue("\n\n\\({\\widehat {f}}(\\xi )=\\int _{-\\infty }^{\\infty }f(x)" in text)
        assertTrue("\n\nEq.1\n\n" in text)
    }

    @Test
    fun aJapaneseArticleDropsItsEndMatterByStructure() {
        server.enqueue(page("ja-amygdala.html"))
        val source = read("https://ja.wikipedia.org/wiki/扁桃体")
        assertEquals("扁桃体", source.title)
        assertTrue(source.text.startsWith("**扁桃体**（へんとうたい"))
        assertTrue("\n\n## 解剖学的下位領域\n\n" in source.text)
        assertTrue("\n\n### 基底外側複合体\n\n" in source.text)
        for (title in listOf("関連項目", "出典", "外部リンク", "スカラーペディア")) assertFalse(title in source.text, title)
        assertEquals(listOf("ja"), languages)
        assertEquals("/api/rest_v1/page/html/%E6%89%81%E6%A1%83%E4%BD%93", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aDisambiguationPageReturnsItsLists() {
        server.enqueue(page("mercury-disambiguation.html"))
        val text = read("https://en.wikipedia.org/wiki/Mercury_(disambiguation)").text
        assertTrue(text.startsWith("**Mercury** most commonly refers to:\n\n- Mercury (planet), the closest planet to the Sun"))
        assertTrue("\n\n## Companies\n\n- Mercury (toy manufacturer)" in text)
        assertTrue("\n\n## See also\n\n" in text)
        // The message box at the foot of the page is page furniture, not an entry.
        assertFalse("articles associated with the title" in text)
        assertEquals("/api/rest_v1/page/html/Mercury_(disambiguation)", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aDisambiguationPageSaysSoAndAnArticleDoesNot() {
        server.enqueue(page("mercury-disambiguation.html"))
        assertTrue(read("https://en.wikipedia.org/wiki/Mercury_(disambiguation)").disambiguation)
        server.enqueue(page("amygdala.html"))
        assertFalse(read("https://en.wikipedia.org/wiki/Amygdala").disambiguation)
    }

    @Test
    fun aSpecialPageIsNotAnArticleAndNothingIsRequested() {
        for (link in listOf(
            "https://en.wikipedia.org/wiki/Special:Search?search=brain",
            "https://en.wikipedia.org/wiki/Special:Random",
            "https://en.wikipedia.org/w/index.php?title=Special:Search",
            "https://en.wikipedia.org/wiki/Media:Brain.png",
        )) {
            assertTrue(wikipedia.handles(link.toHttpUrl()), link)
            assertEquals(SourceProblem.NotAnArticle, assertIs<SourceResult.Failure>(web.extract(link)).problem, link)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aRedirectIsFollowedAndNamesTheTarget() {
        server.enqueue(
            MockResponse.Builder().code(307).addHeader("Location", "/w/rest.php/v1/page/Fourier_transform/html?redirect=no").build(),
        )
        server.enqueue(page("fourier-transform.html"))
        val source = read("https://en.wikipedia.org/wiki/Fourier_Transform")
        assertEquals("Fourier transform", source.title)
        assertEquals(2, server.requestCount)
        assertEquals("/api/rest_v1/page/html/Fourier_Transform", server.takeRequest().url.encodedPath)
        assertEquals("/w/rest.php/v1/page/Fourier_transform/html", server.takeRequest().url.encodedPath)
    }

    // ---- links ----

    @Test
    fun theMobileHostAndIndexPhpLinksAreRead() {
        server.enqueue(page("amygdala.html"))
        server.enqueue(page("amygdala.html"))
        read("https://en.m.wikipedia.org/wiki/Amygdala#Structure")
        read("https://en.wikipedia.org/w/index.php?title=Amygdala")
        assertEquals(listOf("en", "en"), languages)
        assertEquals("/api/rest_v1/page/html/Amygdala", server.takeRequest().url.encodedPath)
        assertEquals("/api/rest_v1/page/html/Amygdala", server.takeRequest().url.encodedPath)
    }

    @Test
    fun aSlashInATitleIsSentEncoded() {
        server.enqueue(page("amygdala.html"))
        read("https://en.wikipedia.org/wiki/AC/DC")
        assertEquals("/api/rest_v1/page/html/AC%2FDC", server.takeRequest().url.encodedPath)
    }

    @Test
    fun spacesBecomeUnderscores() {
        server.enqueue(page("amygdala.html"))
        server.enqueue(page("amygdala.html"))
        read("https://en.wikipedia.org/wiki/Star%20Trek:_The_Next_Generation")
        read("https://en.wikipedia.org/w/index.php?title=Star+Trek")
        assertEquals("/api/rest_v1/page/html/Star_Trek:_The_Next_Generation", server.takeRequest().url.encodedPath)
        assertEquals("/api/rest_v1/page/html/Star_Trek", server.takeRequest().url.encodedPath)
    }

    @Test
    fun onlyArticleLinksOnWikipediaAreClaimed() {
        fun handles(link: String) = wikipedia.handles(link.toHttpUrl())

        for (yes in listOf(
            "https://en.wikipedia.org/wiki/Amygdala",
            "https://de.wikipedia.org/wiki/Amygdala#Aufbau",
            "https://en.m.wikipedia.org/wiki/Amygdala",
            "https://simple.wikipedia.org/wiki/Brain",
            "https://zh-min-nan.wikipedia.org/wiki/Brain",
            "https://en.wikipedia.org/wiki/Star_Trek:_The_Next_Generation",
            "https://en.wikipedia.org/wiki/AC/DC",
            "https://en.wikipedia.org/w/index.php?title=Amygdala",
            "https://en.wikipedia.org/w/index.php?title=Amygdala&action=view",
            "https://en.wikipedia.org/index.php?title=Amygdala",
            "http://en.wikipedia.org/wiki/Amygdala",
        )) assertTrue(handles(yes), yes)

        for (no in listOf(
            "https://en.wikipedia.org/",
            "https://en.wikipedia.org/wiki/",
            "https://www.wikipedia.org/wiki/Amygdala",
            "https://wikipedia.org/wiki/Amygdala",
            "https://en.wiktionary.org/wiki/brain",
            "https://commons.wikimedia.org/wiki/Brain",
            "https://wikipedia.org.example.com/wiki/Amygdala",
            "https://example.com/wiki/Amygdala",
            "https://en.wikipedia.org/wiki/Talk:Amygdala",
            "https://en.wikipedia.org/wiki/File:Brain.png",
            "https://en.wikipedia.org/wiki/Category:Brain",
            "https://en.wikipedia.org/wiki/Wikipedia:About",
            "https://en.wikipedia.org/wiki/Template_talk:Infobox",
            "https://en.wikipedia.org/w/index.php?title=Amygdala&oldid=123",
            "https://en.wikipedia.org/w/index.php?title=Amygdala&diff=123",
            "https://en.wikipedia.org/w/index.php?title=Amygdala&action=edit",
            "https://en.wikipedia.org/w/index.php?curid=1234",
            "https://en.wikipedia.org/w/api.php?action=query",
        )) assertFalse(handles(no), no)
    }

    @Test
    fun aPageThatIsNotItsKindIsLeftToTheGenericExtractor() {
        val fetcher = PageFetcher(OkHttpClient())
        // A namespace the extractor refuses: null, and nothing is requested.
        assertNull(wikipedia.extract("https://en.wikipedia.org/wiki/Talk:Amygdala".toHttpUrl(), fetcher))
        assertEquals(0, server.requestCount)

        // The API answering with something that is not HTML.
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body("{}").build())
        assertNull(wikipedia.extract("https://en.wikipedia.org/wiki/Amygdala".toHttpUrl(), fetcher))
    }

    @Test
    fun aFailureIsReturnedAsItIs() {
        server.enqueue(MockResponse.Builder().code(404).build())
        val missing = assertIs<SourceResult.Failure>(web.extract("https://en.wikipedia.org/wiki/No_such_article_anywhere"))
        assertEquals(SourceProblem.HttpError, missing.problem)
        assertEquals("HTTP 404", missing.detail)
        assertEquals(1, server.requestCount)

        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html><body><section data-mw-section-id=\"0\"></section></body></html>").build())
        assertEquals(SourceProblem.NoText, assertIs<SourceResult.Failure>(web.extract("https://en.wikipedia.org/wiki/Empty")).problem)
    }

    // ---- the sections ----

    @Test
    fun sectionsAreRangesOfTheText() {
        server.enqueue(page("amygdala.html"))
        val source = read("https://en.wikipedia.org/wiki/Amygdala")
        val article = WikipediaArticle.of(Jsoup.parse(fixture("amygdala.html")))

        // The text is already tidy, so the range offsets stay right after the extractor's own cleanup.
        assertEquals(article.text, source.text)
        assertEquals(listOf(null, "Structure", "Hemispheric specializations"), article.sections.map { it.title })
        assertEquals(listOf(0, 2, 3), article.sections.map { it.level })
        assertEquals(0, article.sections.first().start)
        assertEquals(article.text.length, article.sections.last().end)
        for ((section, next) in article.sections.zipWithNext()) assertEquals(section.end + 2, next.start)
        val structure = article.sections[1]
        assertTrue(article.text.substring(structure.start, structure.end).startsWith("## Structure\n\nThirteen nuclei"))
        val subsection = article.sections[2]
        assertTrue(article.text.substring(subsection.start, subsection.end).startsWith("### Hemispheric specializations\n\n"))
        // A parent's range stops where its subsection starts.
        assertFalse("Hemispheric" in article.text.substring(structure.start, structure.end))
    }

    @Test
    fun theSourceCarriesItsSectionsByPosition() {
        server.enqueue(page("amygdala.html"))
        val source = read("https://en.wikipedia.org/wiki/Amygdala")

        assertEquals(listOf(0, 1, 2), source.sections.map { it.id })
        assertEquals(listOf(null, "Structure", "Hemispheric specializations"), source.sections.map { it.title })
        assertEquals(listOf(0, 2, 3), source.sections.map { it.level })
        assertTrue(source.textOf(source.sections[1]).startsWith("## Structure\n\nThirteen nuclei"))
        assertEquals(source.text.length, source.sections.last().end)
    }

    @Test
    fun sectionsPastTheCharacterLimitAreLeftOutAndTheLastIsCut() {
        val prose = "word ".repeat(WebPageExtractor.MAX_CHARS / 8)
        val html = "<html><head><title>Long</title></head><body>" +
            "<section data-mw-section-id=\"0\"><p>Lead.</p></section>" +
            "<section data-mw-section-id=\"1\"><h2>A</h2><p>$prose</p></section>" +
            "<section data-mw-section-id=\"2\"><h2>B</h2><p>$prose</p></section>" +
            "<section data-mw-section-id=\"3\"><h2>C</h2><p>$prose</p></section>" +
            "<section data-mw-section-id=\"4\"><h2>D</h2><p>$prose</p></section></body></html>"
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/html").body(html).build())
        val source = read("https://en.wikipedia.org/wiki/Long")

        assertTrue(source.truncated)
        assertEquals(WebPageExtractor.MAX_CHARS, source.text.length)
        assertTrue(source.sections.size in 2..4)
        assertEquals(source.text.length, source.sections.last().end)
        assertTrue(source.sections.all { it.start < it.end && it.end <= source.text.length })
    }

    @Test
    fun aSectionThatIsOnlyLinksIsDroppedWhateverItsTitle() {
        val article = WikipediaArticle.of(
            Jsoup.parse(
                """
                <html><head><title>Gehirn</title></head><body>
                <section data-mw-section-id="0"><p>Das Gehirn ist ein Organ.</p></section>
                <section data-mw-section-id="1"><h2>Aufbau</h2><p>Es besteht aus Zellen.</p></section>
                <section data-mw-section-id="2"><h2>Siehe auch</h2><ul>
                  <li><a href="./Nerv">Nerv</a></li><li><a href="./Hirn">Hirn</a> – <a href="./Kopf">Kopf</a></li></ul></section>
                <section data-mw-section-id="3"><h2>Typen</h2><ul>
                  <li><a href="./A">Alpha</a>, die erste Zelle, die man kennt</li></ul></section>
                <section data-mw-section-id="4"><h2>Einzelnachweise</h2><ol class="references"><li>Quelle</li></ol></section>
                <section data-mw-section-id="5"><h2>Weblinks</h2><div class="navbox">Navigation</div></section>
                </body></html>
                """.trimIndent(),
            ),
        )
        assertEquals("Gehirn", article.title)
        // Kept: the lead, a section of prose, and a list whose items have text of their own.
        assertEquals(listOf(null, "Aufbau", "Typen"), article.sections.map { it.title })
        assertTrue("Alpha, die erste Zelle" in article.text)
        for (gone in listOf("Siehe auch", "Nerv", "Einzelnachweise", "Quelle", "Weblinks", "Navigation")) assertFalse(gone in article.text, gone)
    }

    @Test
    fun theEnglishEndMatterGoesByTitleEvenWithProse() {
        val article = WikipediaArticle.of(
            Jsoup.parse(
                """
                <html><head><title>T</title></head><body>
                <section data-mw-section-id="0"><p>Lead.</p></section>
                <section data-mw-section-id="1"><h2>Notes</h2><p>A note written as prose.</p>
                  <section data-mw-section-id="2"><h3>Footnotes</h3><p>Nested prose.</p></section></section>
                <section data-mw-section-id="3"><h2>Sources</h2><p>Prose too.</p></section>
                <section data-mw-section-id="4"><h2>History</h2><p>Kept.</p></section>
                </body></html>
                """.trimIndent(),
            ),
        )
        assertEquals(listOf(null, "History"), article.sections.map { it.title })
        assertEquals("Lead.\n\n## History\n\nKept.", article.text)
    }

    @Test
    fun aParentWhoseSubsectionsAreAllDroppedGoesToo() {
        val article = WikipediaArticle.of(
            Jsoup.parse(
                """
                <html><head><title>T</title></head><body>
                <section data-mw-section-id="0"><p>Lead.</p></section>
                <section data-mw-section-id="1"><h2>Background</h2>
                  <section data-mw-section-id="2"><h3>See also</h3><ul><li><a href="./X">X</a></li></ul></section></section>
                </body></html>
                """.trimIndent(),
            ),
        )
        assertEquals("Lead.", article.text)
        assertEquals(1, article.sections.size)
    }

    @Test
    fun aPageWithoutSectionsIsReadWhole() {
        val article = WikipediaArticle.of(Jsoup.parse("<html><head><title>T</title></head><body><p>Just a paragraph.</p><div class=\"hatnote\">Skip</div></body></html>"))
        assertEquals("Just a paragraph.", article.text)
        assertTrue(article.sections.isEmpty())
    }
}
