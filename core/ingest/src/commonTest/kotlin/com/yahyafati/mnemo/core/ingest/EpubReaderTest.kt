package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.SourceProblem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubReaderTest {
    private val root = Files.createTempDirectory("epub-test").toFile()
    private val cache = File(root, "cache")

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Reads [epub] and checks the reader left nothing in the cache folder, whatever the outcome. */
    private fun read(epub: EpubBuilder, limits: EpubLimits = EpubLimits(), fileName: String? = null) = read(epub.build(), limits, fileName)

    private fun read(bytes: ByteArray, limits: EpubLimits = EpubLimits(), fileName: String? = null): BookResult {
        val result = EpubReader({ cache }, limits).read(bytes.inputStream(), fileName)
        assertEquals(emptyList(), cache.list()?.toList().orEmpty(), "the temporary copy is deleted")
        return result
    }

    private fun book(result: BookResult): BookSource = assertIs<BookResult.Success>(result).book

    private fun problem(result: BookResult): SourceProblem = assertIs<BookResult.Failure>(result).problem

    private fun chapter(prefix: String, count: Int = 150) = para(words(count, prefix))

    // ---- structure ----

    @Test
    fun epub3ChaptersFollowTheNavInOrder() {
        val result = book(
            read(
                epub3(
                    listOf(
                        Ch("one.xhtml", "The Beginning", "<h1>The Beginning</h1>" + chapter("a")),
                        Ch("two.xhtml", "The Middle", "<h1>The Middle</h1>" + chapter("b")),
                        Ch("three.xhtml", "The End", "<h1>The End</h1>" + chapter("c")),
                    ),
                ),
            ),
        )
        assertEquals("Test Book", result.title)
        assertEquals("Ada Author", result.author)
        assertEquals("en", result.language)
        assertEquals(listOf("The Beginning", "The Middle", "The End"), result.chapters.map { it.title })
        assertEquals(listOf(0, 1, 2), result.chapters.map { it.id })
        assertTrue(result.chapters.all { it.kind == ChapterKind.Content && !it.truncated })
        assertTrue(result.chapters[1].text.contains("b1 b2") && !result.chapters[1].text.contains("a1"))
        assertEquals(152, result.chapters[1].wordCount)
        assertFalse(result.truncated)
    }

    @Test
    fun epub2ChaptersComeFromTheNcx() {
        val epub = EpubBuilder()
            .file("META-INF/container.xml", container("OEBPS/content.opf"))
            .file("OEBPS/c1.xhtml", xhtml("<h1>First</h1>" + chapter("a")))
            .file("OEBPS/c2.xhtml", xhtml("<h1>Second</h1>" + chapter("b")))
            .file(
                "OEBPS/toc.ncx",
                """<?xml version="1.0"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap>
                <navPoint id="n1"><navLabel><text>Chapter I</text></navLabel><content src="c1.xhtml"/></navPoint>
                <navPoint id="n2"><navLabel><text>Chapter II</text></navLabel><content src="c2.xhtml"/></navPoint>
                </navMap></ncx>""",
            )
            .file(
                "OEBPS/content.opf",
                opf(
                    DEFAULT_METADATA,
                    """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>""",
                    """<itemref idref="c1"/><itemref idref="c2"/>""",
                    version = "2.0",
                    spineAttrs = """toc="ncx"""",
                ),
            )
        val result = book(read(epub))
        assertEquals(listOf("Chapter I", "Chapter II"), result.chapters.map { it.title })
        assertTrue(result.chapters[1].text.contains("b150"))
    }

    @Test
    fun withoutATocEveryLinearSpineFileIsAChapterTitledByItsHeading() {
        // No nav, no ncx; the third file is outside the reading order.
        val bytes = EpubBuilder()
            .file("META-INF/container.xml", container())
            .file("OEBPS/a.xhtml", xhtml("<h2>Alpha</h2>" + chapter("a")))
            .file("OEBPS/b.xhtml", xhtml("<h2>Beta</h2>" + chapter("b")))
            .file("OEBPS/notes.xhtml", xhtml("<h2>Notes</h2>" + chapter("n")))
            .file(
                "OEBPS/content.opf",
                opf(
                    DEFAULT_METADATA,
                    """<item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                    <item id="n" href="notes.xhtml" media-type="application/xhtml+xml"/>""",
                    """<itemref idref="a"/><itemref idref="b"/><itemref idref="n" linear="no"/>""",
                ),
            )
        val result = book(read(bytes))
        assertEquals(listOf("Alpha", "Beta"), result.chapters.map { it.title })
    }

    @Test
    fun entriesIntoTheMiddleOfOneFileSplitTheText() {
        val epub = epub3(
            listOf(Ch("book.xhtml", "Book", """<h2 id="a">Alpha</h2>${chapter("alpha")}<h2 id="b">Beta</h2>${chapter("beta")}""")),
            nav = navItem("book.xhtml#a", "Alpha") + navItem("book.xhtml#b", "Beta"),
        )
        val result = book(read(epub))
        assertEquals(listOf("Alpha", "Beta"), result.chapters.map { it.title })
        assertTrue("alpha150" in result.chapters[0].text && "beta1 " !in result.chapters[0].text)
        assertTrue("beta1 " in result.chapters[1].text && "alpha1 " !in result.chapters[1].text)
    }

    @Test
    fun aChapterRunsOnThroughTheFilesTheTocDoesNotList() {
        val epub = epub3(
            listOf(
                Ch("c1a.xhtml", "One", chapter("a")),
                Ch("c1b.xhtml", "One, continued", chapter("b")),
                Ch("c2.xhtml", "Two", chapter("c")),
            ),
            nav = navItem("c1a.xhtml", "One") + navItem("c2.xhtml", "Two"),
        )
        val result = book(read(epub))
        assertEquals(listOf("One", "Two"), result.chapters.map { it.title })
        assertTrue("a1 " in result.chapters[0].text && "b150" in result.chapters[0].text && "c1 " !in result.chapters[0].text)
    }

    @Test
    fun partPagesAreNotChaptersAndSectionsBelongToTheirChapter() {
        val epub = epub3(
            listOf(
                Ch("part1.xhtml", "Part I", "<h1>Part I</h1>"),
                Ch("ch1.xhtml", "Chapter 1", """<h1>Chapter 1</h1>${chapter("a")}<h2 id="s1">1.1 Section</h2>${chapter("sec")}"""),
                Ch("ch2.xhtml", "Chapter 2", chapter("b")),
                Ch("part2.xhtml", "Part II", "<h1>Part II</h1>"),
                Ch("ch3.xhtml", "Chapter 3", chapter("c")),
            ),
            nav = navItem(
                "part1.xhtml",
                "Part I",
                navItem("ch1.xhtml", "Chapter 1", navItem("ch1.xhtml#s1", "1.1 Section")) + navItem("ch2.xhtml", "Chapter 2"),
            ) + navItem("part2.xhtml", "Part II", navItem("ch3.xhtml", "Chapter 3")),
        )
        val result = book(read(epub))
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), result.chapters.map { it.title })
        assertTrue("sec150" in result.chapters[0].text, "the section's text stays in its chapter")
        assertFalse("Part II" in result.chapters[1].text, "a part page doesn't trail the chapter before it")
    }

    @Test
    fun aTitlePageWithNextToNothingJoinsTheChapterAfterIt() {
        val epub = epub3(
            listOf(
                Ch("p.xhtml", "Prologue Part", "<h1>Prologue Part</h1>"),
                Ch("c1.xhtml", "Chapter 1", chapter("a")),
                Ch("poem1.xhtml", "Poem 1", chapter("p", 60)),
                Ch("poem2.xhtml", "Poem 2", chapter("q", 60)),
            ),
        )
        val result = book(read(epub))
        assertEquals(listOf("Chapter 1", "Poem 1", "Poem 2"), result.chapters.map { it.title })
        assertTrue(result.chapters[0].text.startsWith("Prologue Part"))
    }

    @Test
    fun sameTitlesAndDoubleColonsAreKeptAsTheyAre() {
        val epub = epub3(
            listOf(
                Ch("a.xhtml", "Introduction", chapter("a")),
                Ch("b.xhtml", "Introduction", chapter("b")),
                Ch("c.xhtml", "Cells::Organelles", chapter("c")),
            ),
        )
        assertEquals(listOf("Introduction", "Introduction", "Cells::Organelles"), book(read(epub)).chapters.map { it.title })
    }

    @Test
    fun coversCopyrightAndAcknowledgmentsAreMarkedNotRemoved() {
        val epub = epub3(
            listOf(
                Ch("cover.xhtml", "Cover", para("A cover with words"), bodyAttrs = """epub:type="cover""""),
                Ch("copy.xhtml", "Copyright", para("All rights reserved by nobody in particular")),
                Ch("c1.xhtml", "Chapter 1", chapter("a")),
                Ch("c2.xhtml", "Indexing Algorithms", chapter("b")),
                Ch("ack.xhtml", "Acknowledgments", para("Thanks to everybody who helped with the writing of this book")),
                Ch("idx.xhtml", "Back", para("Entries"), bodyAttrs = """epub:type="index""""),
            ),
        )
        assertEquals(
            listOf(
                ChapterKind.FrontMatter, ChapterKind.FrontMatter, ChapterKind.Content,
                ChapterKind.Content, ChapterKind.BackMatter, ChapterKind.BackMatter,
            ),
            book(read(epub)).chapters.map { it.kind },
        )
    }

    // ---- text ----

    @Test
    fun noPageNumbersFootnotesOrRubyReadings() {
        val epub = epub3(
            listOf(
                Ch(
                    "a.xhtml",
                    "A",
                    """<p>Text<a epub:type="noteref" href="#n1">9</a> goes<span epub:type="pagebreak" id="p3" title="3"/> on. ${words(150)}</p>
                    <aside epub:type="footnote" id="n1"><p>Footnote words</p></aside>
                    <p><ruby>漢<rp>(</rp><rt>かん</rt><rp>)</rp></ruby>字${"の".repeat(300)}</p>""",
                ),
                Ch("b.xhtml", "B", chapter("b")),
            ),
        )
        val text = book(read(epub)).chapters[0].text
        assertTrue("Text goes on." in text, text)
        assertFalse("Footnote" in text || "かん" in text || "(" in text)
        assertTrue("漢字の" in text)
    }

    @Test
    fun chaptersWithoutSpacesAreNotMistakenForEmptyPages() {
        val epub = epub3(listOf(Ch("a.xhtml", "一", para("あ".repeat(400))), Ch("b.xhtml", "二", para("い".repeat(400)))))
        assertEquals(listOf("一", "二"), book(read(epub)).chapters.map { it.title })
    }

    @Test
    fun aChapterLongerThanTheLimitIsCutAndSaysSo() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a", 1_000)), Ch("b.xhtml", "B", chapter("b"))))
        val result = book(read(epub, EpubLimits(maxChapterChars = 1_000)))
        assertTrue(result.chapters[0].truncated && result.chapters[0].text.length <= 1_000)
        assertFalse(result.chapters[1].truncated)
        assertTrue(result.truncated)
    }

    @Test
    fun moreChaptersThanTheLimitAreLeftOutAndSaid() {
        val epub = epub3((1..5).map { Ch("c$it.xhtml", "C$it", chapter("w$it")) })
        val result = book(read(epub, EpubLimits(maxChapters = 3)))
        assertEquals(listOf("C1", "C2", "C3"), result.chapters.map { it.title })
        assertTrue(result.truncated)
    }

    // ---- paths ----

    @Test
    fun hrefsArePercentDecodedRelativeToTheirFileAndMatchedIgnoringCase() {
        val bytes = EpubBuilder()
            .file("META-INF/container.xml", container())
            .file("OEBPS/text/Chapter 1.xhtml", xhtml("<h1>One</h1>" + chapter("a")))
            .file("OEBPS/text/CHAPTER2.xhtml", xhtml("<h1>Two</h1>" + chapter("b")))
            .file(
                "OEBPS/nav/nav.xhtml",
                navDocument(navItem("../text/Chapter%201.xhtml", "First") + navItem("../text/Chapter2.xhtml", "Second")),
            )
            .file(
                "OEBPS/content.opf",
                opf(
                    DEFAULT_METADATA,
                    """<item id="nav" href="nav/nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/Chapter%201.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/Chapter2.xhtml" media-type="application/xhtml+xml"/>""",
                    """<itemref idref="c1"/><itemref idref="c2"/>""",
                ),
            )
        val result = book(read(bytes.build()))
        assertEquals(listOf("First", "Second"), result.chapters.map { it.title })
        assertTrue("b150" in result.chapters[1].text)
    }

    @Test
    fun resolvingPaths() {
        assertEquals("OEBPS/b.xhtml", resolvePath("OEBPS/", "a/../b.xhtml#x"))
        assertEquals("a b.xhtml", resolvePath("", "a%20b.xhtml"))
        assertEquals("a+b.xhtml", resolvePath("", "a+b.xhtml"))
        assertEquals("abs.xhtml", resolvePath("d/", "/abs.xhtml"))
        assertEquals("d/x.xhtml", resolvePath("d/", "./x.xhtml?v=1"))
        assertNull(resolvePath("", "../x.xhtml"))
        assertNull(resolvePath("d/", "../../x.xhtml"))
        assertNull(resolvePath("", "https://example.com/x.xhtml"))
        assertNull(resolvePath("", "#fragment"))
    }

    @Test
    fun aPathOutOfTheZipIsNeverRead() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a")), Ch("b.xhtml", "B", chapter("b")))) {
            file("../evil.xhtml", xhtml(para("EVIL " + words(200, "evil"))))
            file(
                "OEBPS/content.opf",
                opf(
                    DEFAULT_METADATA,
                    """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c0" href="a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="evil" href="../../evil.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c1" href="b.xhtml" media-type="application/xhtml+xml"/>""",
                    """<itemref idref="c0"/><itemref idref="evil"/><itemref idref="c1"/>""",
                ),
            )
        }
        val result = book(read(epub))
        assertEquals(listOf("A", "B"), result.chapters.map { it.title })
        assertTrue(result.chapters.none { "EVIL" in it.text })
        assertFalse(File(root.parentFile, "evil.xhtml").exists())
    }

    @Test
    fun aBookWithoutAContainerFileUsesItsOnlyPackageFile() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a")))) { file("META-INF/container.xml", "<container/>") }
        assertEquals(listOf("A"), book(read(epub)).chapters.map { it.title })
    }

    // ---- DRM ----

    private val encryption = { algorithm: String, uri: String ->
        """<encryption xmlns="urn:oasis:names:tc:opendocument:xmlns:container" xmlns:enc="http://www.w3.org/2001/04/xmlenc#">
        <enc:EncryptedData><enc:EncryptionMethod Algorithm="$algorithm"/><enc:CipherData><enc:CipherReference URI="$uri"/></enc:CipherData></enc:EncryptedData>
        </encryption>"""
    }

    @Test
    fun obfuscatedFontsAreNotDrm() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a")))) {
            file("META-INF/encryption.xml", encryption("http://www.idpf.org/2008/embedding", "OEBPS/font.otf"))
        }
        assertEquals(listOf("A"), book(read(epub)).chapters.map { it.title })
    }

    @Test
    fun anEncryptedChapterIsDrm() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a")))) {
            file("META-INF/encryption.xml", encryption("http://www.w3.org/2001/04/xmlenc#aes128-cbc", "OEBPS/a.xhtml"))
        }
        assertEquals(SourceProblem.Drm, problem(read(epub)))
    }

    @Test
    fun aRightsFileIsDrm() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a")))) { file("META-INF/rights.xml", "<rights/>") }
        assertEquals(SourceProblem.Drm, problem(read(epub)))
    }

    // ---- bad input ----

    @Test
    fun aBookOfPicturesHasNoText() {
        val epub = epub3(listOf(Ch("p1.xhtml", "Page 1", """<img src="1.jpg" alt=""/>"""), Ch("p2.xhtml", "Page 2", """<img src="2.jpg" alt=""/>""")))
        assertEquals(SourceProblem.NoText, problem(read(epub)))
    }

    @Test
    fun notAnEpubIsUnsupported() {
        assertEquals(SourceProblem.Unsupported, problem(read(EpubBuilder().file("readme.txt", "hello"))))
        assertEquals(SourceProblem.Unsupported, problem(read("this is not a zip".toByteArray())))
        assertEquals(SourceProblem.Unsupported, problem(read(ByteArray(0))))
    }

    @Test
    fun aFileOverTheLimitIsTooLargeAndLeavesNothingBehind() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a", 2_000))))
        assertEquals(SourceProblem.TooLarge, problem(read(epub.build(), EpubLimits(maxFileBytes = 500))))
    }

    @Test
    fun tooManyEntriesIsTooLarge() {
        val epub = epub3((1..20).map { Ch("c$it.xhtml", "C$it", chapter("w$it")) })
        assertEquals(SourceProblem.TooLarge, problem(read(epub, EpubLimits(maxEntries = 10))))
    }

    @Test
    fun aHugeEntryIsReadOnlyUpToTheLimit() {
        // About 2 MB of text that a few KB of zip expands to.
        val bomb = para("boom ".repeat(400_000))
        val epub = epub3(listOf(Ch("a.xhtml", "A", bomb), Ch("b.xhtml", "B", chapter("b"))))
        val result = book(read(epub, EpubLimits(maxEntryBytes = 50_000)))
        assertTrue(result.truncated)
        assertTrue(result.chapters[0].text.length <= 50_000)
        assertEquals("B", result.chapters[1].title)
    }

    @Test
    fun aBookLargerThanTheTextBudgetKeepsTheStartAndSaysSo() {
        val epub = epub3((1..4).map { Ch("c$it.xhtml", "C$it", chapter("w$it", 400)) })
        val result = book(read(epub, EpubLimits(maxTotalChars = 4_000)))
        assertTrue(result.truncated)
        assertTrue(result.chapters.size < 4)
    }

    @Test
    fun theFileNameTitlesABookWithoutOne() {
        val epub = epub3(listOf(Ch("a.xhtml", "A", chapter("a"))), metadata = "<dc:language>en</dc:language>")
        assertEquals("My Book", book(read(epub, fileName = "My Book.epub")).title)
    }
}
