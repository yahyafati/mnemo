package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reading chosen pages of a PDF (docs/pdf/ROADMAP.md, P1), on both PDF libraries. */
class PdfPagesTest : PlatformTest() {
    private val pdf = newPdfExtractor()

    private fun fixture(name: String): ByteArray =
        PdfPagesTest::class.java.getResourceAsStream("/pdf/$name")?.use { it.readBytes() } ?: error("missing fixture $name")

    private fun pageNumbers(text: String): List<Int> =
        Regex("""page (\d+) of the outline fixture""").findAll(text).map { it.groupValues[1].toInt() }.toList()

    private fun read(bytes: ByteArray, pages: List<Int>?) =
        assertIs<SourceResult.Success>(pdf.extract(bytes.inputStream(), pages, "outline")).source

    @Test
    fun inspectCountsPagesAndNamesTheDocument() {
        val info = assertIs<PdfInfoResult.Success>(pdf.inspect(fixture("outline.pdf").inputStream(), "outline file")).info
        assertEquals(12, info.pageCount)
        assertEquals("Outline fixture", info.title)

        // Without a title of its own, the file name stands in.
        val untitled = assertIs<PdfInfoResult.Success>(pdf.inspect(makePdf("a", "b", "c").inputStream(), "notes")).info
        assertEquals(3, untitled.pageCount)
        assertEquals("notes", untitled.title)
    }

    @Test
    fun inspectFailsLikeExtractDoes() {
        val failure = assertIs<PdfInfoResult.Failure>(pdf.inspect("not a pdf".byteInputStream()))
        assertEquals(SourceProblem.Unsupported, failure.problem)
    }

    @Test
    fun onlyTheChosenPagesAreRead() {
        val source = read(fixture("outline.pdf"), listOf(3, 4, 9))
        assertEquals(listOf(3, 4, 9), pageNumbers(source.text))
        assertFalse(source.truncated)
    }

    @Test
    fun separateRunsAreJoinedWithABlankLine() {
        val text = read(fixture("outline.pdf"), listOf(1, 2, 7)).text
        assertEquals(listOf(1, 2, 7), pageNumbers(text))
        assertTrue("\n\n" in text.substringAfter("page 2 of the outline fixture"))
    }

    @Test
    fun pagesComeInDocumentOrderWhateverOrderTheyWereGiven() {
        assertEquals(listOf(2, 5, 8), pageNumbers(read(fixture("outline.pdf"), listOf(8, 2, 5, 2)).text))
    }

    @Test
    fun pagesThePdfDoesNotHaveAreIgnored() {
        assertEquals(listOf(11, 12), pageNumbers(read(fixture("outline.pdf"), listOf(11, 12, 13, 400)).text))
        val failure = assertIs<SourceResult.Failure>(pdf.extract(fixture("outline.pdf").inputStream(), listOf(13), "outline"))
        assertEquals(SourceProblem.NoText, failure.problem)
    }

    @Test
    fun noSelectionIsTheFirstPagesAsBefore() {
        val source = read(fixture("outline.pdf"), null)
        assertEquals((1..12).toList(), pageNumbers(source.text))
        assertFalse(source.truncated)
        assertEquals("Outline fixture", source.title)
        assertEquals((1..12).toList(), pageNumbers(assertIs<SourceResult.Success>(pdf.extract(fixture("outline.pdf").inputStream(), "outline")).source.text))
    }

    @Test
    fun moreThanThePageLimitIsCutAndSaidToBe() {
        val lines = (1..PdfTextExtractor.MAX_PAGES + 20).map { "Line $it" }.toTypedArray()
        val bytes = makePdf(*lines)

        // The first pages of a long PDF, as before.
        val first = read(bytes, null)
        assertTrue(first.truncated)
        assertTrue("Line ${PdfTextExtractor.MAX_PAGES}" in first.text)
        assertFalse("Line ${PdfTextExtractor.MAX_PAGES + 1}" in first.text)

        // Any pages of it, as long as there are 300 or fewer.
        val late = read(bytes, (PdfTextExtractor.MAX_PAGES - 9..PdfTextExtractor.MAX_PAGES + 20).toList())
        assertFalse(late.truncated)
        assertTrue("Line ${PdfTextExtractor.MAX_PAGES + 20}" in late.text)
        assertFalse("Line 1" in late.text)

        // More than the limit chosen: the extractor stops at the limit and says so.
        val cut = read(bytes, (1..PdfTextExtractor.MAX_PAGES + 20).toList())
        assertTrue(cut.truncated)
        assertFalse("Line ${PdfTextExtractor.MAX_PAGES + 1}" in cut.text)
    }

    @Test
    fun aScannedPdfHasNoTextToRead() {
        val failure = assertIs<SourceResult.Failure>(pdf.extract(fixture("scanned.pdf").inputStream(), listOf(1, 2), "scan"))
        assertEquals(SourceProblem.NoText, failure.problem)
    }

    @Test
    fun aMixedPdfGivesTheTextOfItsTextPagesOnly() {
        // Pages 1, 3 and 5 of mixed.pdf have a text layer; 2, 4 and 6 are scans.
        val text = assertIs<SourceResult.Success>(pdf.extract(fixture("mixed.pdf").inputStream(), listOf(2, 3, 4), "mixed")).source.text
        assertTrue("page 3 of the mixed fixture" in text)
        assertFalse("page 1 of the mixed fixture" in text)
    }
}
