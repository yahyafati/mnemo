package com.yahyafati.mnemo.core.ingest

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the PDF fixtures (`src/commonTest/resources/pdf`, made by `core/ingest/fixtures/make_pdf_fixtures.py`,
 * docs/pdf/ROADMAP.md P0) so a regeneration can't silently change what later steps' tests rely on.
 * Reads them with PDFBox directly, not through the app's classes.
 */
class PdfFixturesTest {
    private fun open(name: String): PDDocument {
        val bytes = PdfFixturesTest::class.java.getResourceAsStream("/pdf/$name")?.use { it.readBytes() }
            ?: error("missing fixture $name")
        return Loader.loadPDF(bytes)
    }

    private fun PDDocument.pageText(index: Int): String =
        PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }.getText(this)

    private fun PDDocument.visibleChars(index: Int) = pageText(index).count { !it.isWhitespace() }

    /** Filter names of the images on a page. */
    private fun PDPage.imageFilters(): List<String> = resources.xObjectNames.mapNotNull { name ->
        (resources.getXObject(name) as? PDImageXObject)?.stream?.filters?.map { it.name }
    }.flatten()

    private fun items(node: PDOutlineNode, level: Int = 1): List<Triple<Int, String, PDOutlineItem>> =
        node.children().flatMap { listOf(Triple(level, it.title, it)) + items(it, level + 1) }

    @Test
    fun outlineHasFourLevelsLabelsAndABookmarkWithoutADestination() {
        open("outline.pdf").use { doc ->
            assertEquals(12, doc.numberOfPages)
            val outline = items(doc.documentCatalog.documentOutline)
            assertEquals(4, outline.maxOf { it.first })
            assertEquals(
                listOf("Contents", "Preface", "Part I: Foundations", "Part II: Systems", "Appendix", "Errata (no destination)"),
                outline.filter { it.first == 1 }.map { it.second },
            )
            val pages = outline.associate { (_, title, item) ->
                title to item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) + 1 }
            }
            assertEquals(3, pages["Chapter 1: Cells"])
            assertEquals(10, pages["Chapter 4: Respiration"])
            assertEquals(null, pages["Errata (no destination)"])

            val labels = doc.documentCatalog.pageLabels.labelsByPageIndices.toList()
            assertEquals(listOf("i", "ii") + (1..10).map { it.toString() }, labels)
            assertTrue(doc.pageText(6).contains("page 7 of the outline fixture, printed page 5"))
            assertTrue(doc.pageText(0).contains("page 1 of the outline fixture"))
        }
    }

    @Test
    fun scannedPagesAreImagesOnlyInEachEncoding() {
        val expectedFilters = mapOf(
            "scanned.pdf" to "FlateDecode",
            "scanned-ccitt.pdf" to "CCITTFaxDecode",
            "scanned-jbig2.pdf" to "JBIG2Decode",
        )
        for ((file, filter) in expectedFilters) {
            open(file).use { doc ->
                assertEquals(3, doc.numberOfPages, file)
                for (i in 0 until 3) {
                    assertEquals(0, doc.visibleChars(i), "$file page ${i + 1} has no text layer")
                    assertEquals(listOf(filter), doc.getPage(i).imageFilters(), "$file page ${i + 1}")
                }
            }
        }
    }

    @Test
    fun mixedAlternatesTextAndImageOnlyPages() {
        open("mixed.pdf").use { doc ->
            assertEquals(6, doc.numberOfPages)
            for (i in 0 until 6) {
                val hasText = doc.visibleChars(i) >= 30 // the Auto mode's MIN_PAGE_CHARS (docs/pdf/ROADMAP.md P5)
                assertEquals(i % 2 == 0, hasText, "page ${i + 1}")
            }
        }
    }

    @Test
    fun ocrLayerScanReadsAsText() {
        open("ocr-layer.pdf").use { doc ->
            assertEquals(1, doc.numberOfPages)
            assertTrue(doc.visibleChars(0) >= 30)
            assertTrue(doc.pageText(0).contains("The Water Cycle"))
            assertEquals(listOf("DCTDecode"), doc.getPage(0).imageFilters())
        }
    }

    @Test
    fun slidesAreLandscapeWithLittleText() {
        open("slides.pdf").use { doc ->
            assertEquals(4, doc.numberOfPages)
            for (i in 0 until 4) {
                val box = doc.getPage(i).mediaBox
                assertTrue(box.width > box.height, "page ${i + 1} is landscape")
                assertTrue(doc.visibleChars(i) < 100, "page ${i + 1} has little text")
            }
            assertEquals(1, doc.getPage(3).imageFilters().size, "the last slide holds a raster picture")
        }
    }
}
