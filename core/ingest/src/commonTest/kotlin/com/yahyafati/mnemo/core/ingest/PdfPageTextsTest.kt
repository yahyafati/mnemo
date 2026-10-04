package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.PdfPageText
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.SourceProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The text layer page by page (docs/pdf/ROADMAP.md, P5): what Auto mode uses to tell a page of text from a picture, on both PDF libraries. */
class PdfPageTextsTest : PlatformTest() {
    private val pdf = newPdfExtractor()

    private fun fixture(name: String): ByteArray =
        PdfPageTextsTest::class.java.getResourceAsStream("/pdf/$name")?.use { it.readBytes() } ?: error("missing fixture $name")

    private fun texts(name: String, pages: List<Int>) =
        assertIs<PdfPageTextsResult.Success>(pdf.pageTexts(fixture(name).inputStream(), pages)).texts

    @Test
    fun everyAskedForPageIsReadOnItsOwn() {
        val texts = texts("outline.pdf", listOf(9, 3, 4))

        assertEquals(listOf(3, 4, 9), texts.keys.toList()) // document order
        assertTrue("page 3 of the outline fixture" in texts.getValue(3))
        assertFalse("page 4 of the outline fixture" in texts.getValue(3))
        assertTrue("page 9 of the outline fixture" in texts.getValue(9))
        assertTrue(texts.values.all(PdfPageText::hasText))
    }

    @Test
    fun pagesThePdfDoesNotHaveAreLeftOutAndTheRestIsStillRead() {
        assertEquals(listOf(1, 12), texts("outline.pdf", listOf(0, 1, 12, 13, 99, 12)).keys.toList())
    }

    @Test
    fun aScannedPdfHasNoTextOnAnyPage() {
        val texts = texts("scanned.pdf", listOf(1, 2, 3))

        assertEquals(listOf(1, 2, 3), texts.keys.toList())
        assertTrue(texts.values.none(PdfPageText::hasText))
    }

    @Test
    fun aMixedPdfHasTextOnItsTextPagesOnly() {
        val texts = texts("mixed.pdf", (1..6).toList())

        assertEquals(listOf(true, false, true, false, true, false), (1..6).map { PdfPageText.hasText(texts.getValue(it)) })
        assertTrue("page 1 of the mixed fixture" in texts.getValue(1))
    }

    @Test
    fun anOcrLayerUnderAScanCountsAsText() {
        assertTrue(PdfPageText.hasText(texts("ocr-layer.pdf", listOf(1)).getValue(1)))
    }

    @Test
    fun aFileThatIsNotAPdfFailsLikeTheOtherReads() {
        val failure = assertIs<PdfPageTextsResult.Failure>(pdf.pageTexts("not a pdf".byteInputStream(), listOf(1)))
        assertEquals(SourceProblem.Unsupported, failure.problem)
    }

    @Test
    fun aPageNeedsEnoughLettersToCountAsText() {
        assertFalse(PdfPageText.hasText(""))
        assertFalse(PdfPageText.hasText("  12 \n\n  "))
        assertFalse(PdfPageText.hasText("Figure 3.1 ".padEnd(40, ' '))) // spaces are not text
        assertTrue(PdfPageText.hasText("x".repeat(PdfPageText.MIN_CHARS)))
        assertFalse(PdfPageText.hasText("x".repeat(PdfPageText.MIN_CHARS - 1)))
    }
}
