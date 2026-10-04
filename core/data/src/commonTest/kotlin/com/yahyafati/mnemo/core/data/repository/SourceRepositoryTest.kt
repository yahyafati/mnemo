package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ingest.EpubLimits
import com.yahyafati.mnemo.core.ingest.EpubReader
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The text-file source (a text or Markdown file dropped on the desktop window) and the EPUB source. */
class SourceRepositoryTest {
    private val documents = FakeDocumentAccess()
    /** A PDF library that knows five pages and says which pages it was asked for; "not a PDF" is a file that says so. */
    private val pdf = object : PdfTextExtractor {
        val extracted = mutableListOf<List<Int>?>()

        override fun inspect(input: InputStream, fileName: String?): PdfInfoResult {
            val bytes = input.readBytes().decodeToString()
            return if (bytes.startsWith("%PDF")) PdfInfoResult.Success(PdfInfo(5, fileName)) else PdfInfoResult.Failure(SourceProblem.Unsupported)
        }

        override fun pageTexts(input: InputStream, pages: List<Int>): PdfPageTextsResult =
            PdfPageTextsResult.Success(pages.filter { it in 1..5 }.associateWith { if (it % 2 == 0) "" else "Text of page $it" })

        override fun extract(input: InputStream, pages: List<Int>?, fileName: String?): SourceResult {
            extracted += pages
            return SourceResult.Success(SourceText("Pages ${pages ?: "first"} of ${input.readBytes().decodeToString()}", fileName))
        }
    }

    /** A renderer that writes the page and edge into the "image", counts its calls, and fails or comes out white on request. */
    private val renderer = object : PdfPageRenderer {
        val calls = mutableListOf<Pair<Int, Int>>()
        val crops = mutableListOf<Triple<Int, PageRegion, Int>>()
        var blank = false
        var failure: SourceProblem? = null

        /** The type of image a crop comes out as: the renderer picks the smaller of PNG and JPEG. */
        var cropType = "image/png"

        override fun render(file: File, page: Int, longEdge: Int): RenderedPage {
            calls += page to longEdge
            failure?.let { throw PdfRenderException(it) }
            return RenderedPage("JPEG page $page at $longEdge of ${file.readText()}".toByteArray(), "image/jpeg", 10, 10, blank)
        }

        override fun renderRegion(file: File, page: Int, region: PageRegion, longEdge: Int): RenderedPage {
            crops += Triple(page, region, longEdge)
            failure?.let { throw PdfRenderException(it) }
            return RenderedPage("Crop of page $page at $longEdge of ${file.readText()}".toByteArray(), cropType, 10, 10, blank)
        }
    }
    private val cache: File = createTempDirectory("epub-cache").toFile()
    private val directories = TestAppDirectories()
    private val clock = TestClock()
    private val repository = DefaultSourceRepository(
        documents = documents,
        pdf = pdf,
        pageRenderer = renderer,
        web = WebPageExtractor(okhttp3.OkHttpClient(), pdf),
        epub = EpubReader({ cache }, EpubLimits(maxFileBytes = 64 * 1024)),
        speech = object : SpeechTranscriber {
            override fun isAvailable() = false

            override fun transcribe(languageTag: String?): Flow<DictationEvent> = emptyFlow()
        },
        directories = directories,
        clock = clock,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private suspend fun read(uri: String) = repository.read(SourceInput.TextFile(uri))

    @Test
    fun aTextFileIsReadAsTextWithItsNameAsTitle() = runTest {
        documents.put("/notes/Cell biology.md", "﻿# Cells\n\nMitochondria make ATP.\n")
        val source = assertIs<SourceResult.Success>(read("/notes/Cell biology.md")).source
        assertEquals("# Cells\n\nMitochondria make ATP.", source.text)
        assertEquals("Cell biology", source.title)
        assertEquals(false, source.truncated)
    }

    @Test
    fun aVeryLongFileIsCutAndSaidToBe() = runTest {
        documents.put("/notes/long.txt", "word ".repeat(PdfTextExtractor.MAX_CHARS / 5 + 10))
        val source = assertIs<SourceResult.Success>(read("/notes/long.txt")).source
        assertEquals(PdfTextExtractor.MAX_CHARS, source.text.length)
        assertTrue(source.truncated)
    }

    @Test
    fun whatIsNotTextFailsWithAReason() = runTest {
        documents.put("/notes/empty.txt", "  \n ")
        assertEquals(SourceProblem.NoText, assertIs<SourceResult.Failure>(read("/notes/empty.txt")).problem)

        documents.put("/notes/image.txt", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 1))
        assertEquals(SourceProblem.Unsupported, assertIs<SourceResult.Failure>(read("/notes/image.txt")).problem)

        documents.put("/notes/huge.txt", ByteArray(9 * 1024 * 1024) { 'a'.code.toByte() })
        assertEquals(SourceProblem.TooLarge, assertIs<SourceResult.Failure>(read("/notes/huge.txt")).problem)

        assertEquals(SourceProblem.FileUnavailable, assertIs<SourceResult.Failure>(read("/notes/gone.txt")).problem)
        assertNull(documents.info("/notes/gone.txt").size)
    }

    private fun miniEpub(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            add(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            add(
                "OEBPS/content.opf",
                """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Cell Biology</dc:title><dc:creator>Ada Author</dc:creator></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            add(
                "OEBPS/nav.xhtml",
                """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Mitochondria</a></li></ol></nav></body></html>""",
            )
            add(
                "OEBPS/c1.xhtml",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>Mitochondria</h1><p>${(1..80).joinToString(" ") { "word$it" }}</p></body></html>",
            )
        }
        return out.toByteArray()
    }

    private suspend fun readBook(uri: String) = repository.readBook(SourceInput.Epub(uri))

    @Test
    fun aBookIsReadIntoChaptersAndTheCacheIsLeftEmpty() = runTest {
        documents.put("/books/Cell biology.epub", miniEpub())
        val book = assertIs<BookResult.Success>(readBook("/books/Cell biology.epub")).book
        assertEquals("Cell Biology", book.title)
        assertEquals("Ada Author", book.author)
        assertEquals(listOf("Mitochondria"), book.chapters.map { it.title })
        assertTrue(book.chapters.single().wordCount >= 80)
        assertEquals(emptyList(), cache.listFiles().orEmpty().toList())
    }

    @Test
    fun aBookIsNotOneText() = runTest {
        documents.put("/books/b.epub", miniEpub())
        assertEquals(
            SourceProblem.Unsupported,
            assertIs<SourceResult.Failure>(repository.read(SourceInput.Epub("/books/b.epub"))).problem,
        )
    }

    @Test
    fun aBookThatCannotBeReadFailsWithAReason() = runTest {
        documents.put("/books/notes.epub", "just some text")
        assertEquals(SourceProblem.Unsupported, assertIs<BookResult.Failure>(readBook("/books/notes.epub")).problem)

        // Larger than the limit this repository's reader was built with (64 KB).
        documents.put("/books/huge.epub", ByteArray(100 * 1024))
        assertEquals(SourceProblem.TooLarge, assertIs<BookResult.Failure>(readBook("/books/huge.epub")).problem)

        assertEquals(SourceProblem.FileUnavailable, assertIs<BookResult.Failure>(readBook("/books/gone.epub")).problem)
        assertEquals(emptyList(), cache.listFiles().orEmpty().toList())
    }

    private val pdfFolder get() = File(directories.cache, "pdf")

    @Test
    fun anOpenedPdfIsCopiedIntoTheCacheAndReadAPageSelectionAtATime() = runTest {
        documents.put("/docs/Lecture 3.pdf", "%PDF-1.7 lecture")
        val opened = assertIs<PdfOpenResult.Success>(repository.openPdf("/docs/Lecture 3.pdf")).handle
        assertEquals(PdfInfo(pageCount = 5, title = "Lecture 3"), opened.info)
        assertEquals("%PDF-1.7 lecture", File(pdfFolder, "${opened.id}.pdf").readText())

        val pages = PageRanges.of(2, 3, 5)
        val source = assertIs<SourceResult.Success>(repository.readPdf(opened, pages)).source
        assertEquals("Pages [2, 3, 5] of %PDF-1.7 lecture", source.text)
        assertEquals("Lecture 3", source.title)

        // No selection is the extractor's own default: the first pages.
        assertEquals("Pages first of %PDF-1.7 lecture", assertIs<SourceResult.Success>(repository.readPdf(opened, null)).source.text)

        repository.closePdf(opened)
        assertEquals(emptyList(), pdfFolder.listFiles().orEmpty().toList())
        repository.closePdf(opened) // twice is fine
        assertEquals(SourceProblem.FileUnavailable, assertIs<SourceResult.Failure>(repository.readPdf(opened, pages)).problem)
    }

    @Test
    fun theTextLayerOfSinglePagesIsReadFromTheCopy() = runTest {
        val handle = openFive()
        val texts = assertIs<PdfPageTextsResult.Success>(repository.pageTexts(handle, listOf(1, 2, 9))).texts
        assertEquals(mapOf(1 to "Text of page 1", 2 to ""), texts)

        repository.closePdf(handle)
        assertEquals(SourceProblem.FileUnavailable, assertIs<PdfPageTextsResult.Failure>(repository.pageTexts(handle, listOf(1))).problem)
    }

    @Test
    fun aPdfThatCannotBeOpenedLeavesNothingBehind() = runTest {
        documents.put("/docs/notes.pdf", "just some text")
        assertEquals(SourceProblem.Unsupported, assertIs<PdfOpenResult.Failure>(repository.openPdf("/docs/notes.pdf")).problem)

        documents.put("/docs/huge.pdf", ByteArray(PdfTextExtractor.MAX_FILE_BYTES.toInt() + 1))
        assertEquals(SourceProblem.TooLarge, assertIs<PdfOpenResult.Failure>(repository.openPdf("/docs/huge.pdf")).problem)

        assertEquals(SourceProblem.FileUnavailable, assertIs<PdfOpenResult.Failure>(repository.openPdf("/docs/gone.pdf")).problem)
        assertEquals(emptyList(), pdfFolder.listFiles().orEmpty().toList())
    }

    @Test
    fun copiesADayOldAreDeletedWhenAnotherPdfIsOpened() = runTest {
        documents.put("/docs/a.pdf", "%PDF a")
        val old = assertIs<PdfOpenResult.Success>(repository.openPdf("/docs/a.pdf")).handle
        val oldFile = File(pdfFolder, "${old.id}.pdf")
        val oldPages = File(pdfFolder, old.id).also { it.mkdirs(); File(it, "p1.jpg").writeText("x") }
        oldFile.setLastModified(clock.now().minus(Duration.ofDays(2)).toEpochMilli())
        oldPages.setLastModified(clock.now().minus(Duration.ofDays(2)).toEpochMilli())
        val recent = assertIs<PdfOpenResult.Success>(repository.openPdf("/docs/a.pdf")).handle
        File(pdfFolder, "${recent.id}.pdf").setLastModified(clock.now().minus(Duration.ofHours(23)).toEpochMilli())

        documents.put("/docs/b.pdf", "%PDF b")
        val newest = assertIs<PdfOpenResult.Success>(repository.openPdf("/docs/b.pdf")).handle

        assertEquals(setOf("${recent.id}.pdf", "${newest.id}.pdf"), pdfFolder.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test
    fun aHandleCannotNameAFileOutsideTheFolder() = runTest {
        val outside = File(directories.cache, "secret.pdf").also { it.parentFile.mkdirs(); it.writeText("%PDF secret") }
        val forged = PdfHandle("../secret", PdfInfo(1, "x"))
        assertEquals(SourceProblem.FileUnavailable, assertIs<SourceResult.Failure>(repository.readPdf(forged, null)).problem)
        repository.closePdf(forged)
        assertTrue(outside.exists())
    }

    private suspend fun openFive(): PdfHandle {
        documents.put("/docs/slides.pdf", "%PDF slides")
        return assertIs<PdfOpenResult.Success>(repository.openPdf("/docs/slides.pdf")).handle
    }

    private fun PdfPageResult.file() = assertIs<PdfPageResult.Success>(this).file

    @Test
    fun aPageIsRenderedOnceIntoThePdfsFolderAndReused() = runTest {
        val handle = openFive()
        val standard = repository.renderPdfPage(handle, 2, PdfQuality.Standard).file()
        assertEquals(File(File(pdfFolder, handle.id), "p2-standard.jpg"), standard)
        assertEquals("JPEG page 2 at 1568 of %PDF slides", standard.readText())

        assertEquals(standard, repository.renderPdfPage(handle, 2, PdfQuality.Standard).file())
        assertEquals(listOf(2 to 1568), renderer.calls)

        // Another quality, and the thumbnail, are other files.
        assertEquals("p2-high.jpg", repository.renderPdfPage(handle, 2, PdfQuality.High).file().name)
        val thumbnail = repository.pdfThumbnail(handle, 2).file()
        assertEquals("p2-thumb.jpg", thumbnail.name)
        assertEquals(listOf(2 to 1568, 2 to 2048, 2 to PdfQuality.THUMBNAIL_EDGE), renderer.calls)
        assertEquals(emptyList(), File(pdfFolder, handle.id).listFiles().orEmpty().filter { it.name.endsWith(".part") })
    }

    @Test
    fun closingThePdfDeletesItsPages() = runTest {
        val handle = openFive()
        repository.renderPdfPage(handle, 1, PdfQuality.Standard)
        repository.pdfThumbnail(handle, 3)
        repository.closePdf(handle)
        assertEquals(emptyList(), pdfFolder.listFiles().orEmpty().toList())
        assertEquals(SourceProblem.FileUnavailable, assertIs<PdfPageResult.Failure>(repository.renderPdfPage(handle, 1, PdfQuality.Standard)).problem)
    }

    @Test
    fun aBlankPageIsAnErrorAndLeavesNoFile() = runTest {
        val handle = openFive()
        renderer.blank = true
        assertEquals(SourceProblem.BlankPage, assertIs<PdfPageResult.Failure>(repository.renderPdfPage(handle, 1, PdfQuality.Standard)).problem)
        assertEquals(emptyList(), File(pdfFolder, handle.id).listFiles().orEmpty().toList())

        // The next try renders again: it is not remembered as blank.
        renderer.blank = false
        repository.renderPdfPage(handle, 1, PdfQuality.Standard).file()
        assertEquals(2, renderer.calls.size)
    }

    @Test
    fun aPageTheRendererCannotDrawFailsWithItsReason() = runTest {
        val handle = openFive()
        renderer.failure = SourceProblem.Encrypted
        assertEquals(SourceProblem.Encrypted, assertIs<PdfPageResult.Failure>(repository.pdfThumbnail(handle, 1)).problem)
    }

    @Test
    fun aPageThePdfDoesNotHaveIsNeverRendered() = runTest {
        val handle = openFive()
        for (page in listOf(0, 6, -1)) {
            assertEquals(SourceProblem.Unsupported, assertIs<PdfPageResult.Failure>(repository.renderPdfPage(handle, page, PdfQuality.High)).problem)
        }
        assertEquals(emptyList(), renderer.calls)
    }

    @Test
    fun aForgedHandleCannotDeleteOrRenderOutsideItsFolder() = runTest {
        val outside = File(directories.cache, "secret").also { it.mkdirs(); File(it, "keep.txt").writeText("x") }
        val forged = PdfHandle("../secret", PdfInfo(1, "x"))
        repository.closePdf(forged)
        assertTrue(File(outside, "keep.txt").exists())
        assertIs<PdfPageResult.Failure>(repository.renderPdfPage(forged, 1, PdfQuality.Standard))
    }

    private val region = PageRegion(0.1f, 0.2f, 0.6f, 0.7f)

    @Test
    fun aCropIsDrawnOnceAtTheFigureEdgeNamedByPageAndRegionAndReused() = runTest {
        val handle = openFive()
        val figure = repository.cropPdfPage(handle, 2, region).file()
        assertEquals(File(File(pdfFolder, handle.id), "fig-p2-100-200-600-700.png"), figure)
        assertEquals("Crop of page 2 at 1600 of %PDF slides", figure.readText())

        assertEquals(figure, repository.cropPdfPage(handle, 2, region).file())
        assertEquals(listOf(Triple(2, region, PdfQuality.FIGURE_EDGE)), renderer.crops)

        // Another region, or page, is another file.
        assertEquals("fig-p2-100-200-600-800.png", repository.cropPdfPage(handle, 2, PageRegion(0.1f, 0.2f, 0.6f, 0.8f)).file().name)
        assertEquals("fig-p3-100-200-600-700.png", repository.cropPdfPage(handle, 3, region).file().name)
        assertEquals(emptyList(), File(pdfFolder, handle.id).listFiles().orEmpty().filter { it.name.endsWith(".part") })
    }

    @Test
    fun aCropTheRendererEncodedAsAJpegIsAJpgFileAndStillReused() = runTest {
        val handle = openFive()
        renderer.cropType = "image/jpeg"
        val figure = repository.cropPdfPage(handle, 1, region).file()
        assertEquals("fig-p1-100-200-600-700.jpg", figure.name)
        // Reuse looks for either name, so a cached JPEG isn't drawn again even if the renderer would now choose a PNG.
        renderer.cropType = "image/png"
        assertEquals(figure, repository.cropPdfPage(handle, 1, region).file())
        assertEquals(1, renderer.crops.size)
    }

    @Test
    fun aBlankCropIsAnErrorWithNoFileAndAFailureKeepsItsReason() = runTest {
        val handle = openFive()
        renderer.blank = true
        assertEquals(SourceProblem.BlankPage, assertIs<PdfPageResult.Failure>(repository.cropPdfPage(handle, 1, region)).problem)
        assertEquals(emptyList(), File(pdfFolder, handle.id).listFiles().orEmpty().toList())

        renderer.blank = false
        renderer.failure = SourceProblem.Encrypted
        assertEquals(SourceProblem.Encrypted, assertIs<PdfPageResult.Failure>(repository.cropPdfPage(handle, 1, region)).problem)
    }

    @Test
    fun aCropOfAPageThePdfDoesNotHaveOrOfAClosedPdfIsNeverDrawn() = runTest {
        val handle = openFive()
        assertEquals(SourceProblem.Unsupported, assertIs<PdfPageResult.Failure>(repository.cropPdfPage(handle, 6, region)).problem)
        repository.cropPdfPage(handle, 1, region).file()
        repository.closePdf(handle)
        assertEquals(emptyList(), pdfFolder.listFiles().orEmpty().toList())
        assertEquals(SourceProblem.FileUnavailable, assertIs<PdfPageResult.Failure>(repository.cropPdfPage(handle, 1, region)).problem)
        assertEquals(1, renderer.crops.size)
    }
}
