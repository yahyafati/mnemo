package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ingest.EpubLimits
import com.yahyafati.mnemo.core.ingest.EpubReader
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOpenResult
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

        override fun extract(input: InputStream, pages: List<Int>?, fileName: String?): SourceResult {
            extracted += pages
            return SourceResult.Success(SourceText("Pages ${pages ?: "first"} of ${input.readBytes().decodeToString()}", fileName))
        }
    }
    private val cache: File = createTempDirectory("epub-cache").toFile()
    private val directories = TestAppDirectories()
    private val clock = TestClock()
    private val repository = DefaultSourceRepository(
        documents = documents,
        pdf = pdf,
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
}
