package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.desktop.PdfBoxPageRenderer
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import org.junit.After
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rendering pages with PDFBox (docs/pdf/ROADMAP.md, P3) on the fixtures; `scanned-jbig2.pdf` needs the `jbig2-imageio` plugin. */
class PdfBoxPageRendererTest {
    private val renderer = PdfBoxPageRenderer()
    private val folder = createTempDirectory("render").toFile()

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    private fun fixture(name: String): File {
        val bytes = PdfBoxPageRendererTest::class.java.getResourceAsStream("/pdf/$name")?.use { it.readBytes() } ?: error("missing fixture $name")
        return File(folder, name).also { it.writeBytes(bytes) }
    }

    private fun decode(page: RenderedPage): BufferedImage = ImageIO.read(ByteArrayInputStream(page.bytes))

    private fun dark(image: BufferedImage): Int {
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            if (((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3 < 128) count++
        }
        return count
    }

    @Test
    fun aScannedPageIsAJpegWhoseLongSideIsTheEdge() {
        for (quality in PdfQuality.entries) {
            val page = renderer.render(fixture("scanned.pdf"), 1, quality.longEdge)
            assertEquals("image/jpeg", page.mimeType)
            assertFalse(page.blank)
            val image = decode(page)
            assertEquals(page.width, image.width)
            assertEquals(page.height, image.height)
            assertEquals(quality.longEdge, max(image.width, image.height))
            assertTrue(page.bytes.size < PdfPageRenderer.MAX_IMAGE_BYTES)
            // Black text on white paper: well under half the pixels are dark, and some are.
            assertTrue(dark(image) in 1000..image.width * image.height / 4, "dark pixels: ${dark(image)}")
        }
    }

    @Test
    fun theBackgroundIsWhiteNotBlack() {
        val image = decode(renderer.render(fixture("scanned.pdf"), 2, 800))
        val corner = image.getRGB(2, 2)
        assertEquals(0xFF, corner shr 16 and 0xFF)
        assertEquals(0xFF, corner and 0xFF)
    }

    @Test
    fun landscapeSlidesKeepTheirShape() {
        val page = renderer.render(fixture("slides.pdf"), 2, 1568)
        assertEquals(1568, page.width)
        assertEquals(882, page.height)
        assertFalse(page.blank)
    }

    @Test
    fun eachScanEncodingRendersAndTheJbig2PageIsNotWhite() {
        val ccitt = decode(renderer.render(fixture("scanned-ccitt.pdf"), 1, 1000))
        val jbig2Page = renderer.render(fixture("scanned-jbig2.pdf"), 1, 1000)
        assertFalse(jbig2Page.blank, "jbig2-imageio is on the classpath, so the page shows its text")
        val jbig2 = decode(jbig2Page)
        // The same scan in two encodings darkens about the same amount of the page.
        val a = dark(ccitt).toDouble()
        val b = dark(jbig2).toDouble()
        assertTrue(a > 1000 && Math.abs(a - b) / a < 0.1, "ccitt $a, jbig2 $b")
    }

    @Test
    fun aTextPageAndAScanWithATextLayerRender() {
        assertFalse(renderer.render(fixture("outline.pdf"), 3, 800).blank)
        assertFalse(renderer.render(fixture("ocr-layer.pdf"), 1, 800).blank)
        assertFalse(renderer.render(fixture("mixed.pdf"), 2, 800).blank, "an image-only page of a mixed PDF")
    }

    @Test
    fun anEmptyPageIsReportedBlank() {
        val file = File(folder, "empty.pdf").also { it.writeBytes(makePdf()) }
        assertTrue(renderer.render(file, 1, 800).blank)
    }

    @Test
    fun aPageTheDocumentDoesNotHaveOrAFileThatIsNotAPdfFails() {
        val missing = assertFailsWith<PdfRenderException> { renderer.render(fixture("scanned.pdf"), 4, 800) }
        assertEquals(SourceProblem.Unsupported, missing.problem)

        val notPdf = File(folder, "notes.pdf").also { it.writeText("just some text") }
        assertEquals(SourceProblem.Unsupported, assertFailsWith<PdfRenderException> { renderer.render(notPdf, 1, 800) }.problem)
    }

    @Test
    fun aPasswordProtectedPdfIsEncrypted() {
        val file = File(folder, "locked.pdf")
        org.apache.pdfbox.pdmodel.PDDocument().use { document ->
            document.addPage(org.apache.pdfbox.pdmodel.PDPage())
            val policy = org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                "owner", "user", org.apache.pdfbox.pdmodel.encryption.AccessPermission(),
            ).apply { encryptionKeyLength = 128 }
            document.protect(policy)
            document.save(file)
        }
        assertEquals(SourceProblem.Encrypted, assertFailsWith<PdfRenderException> { renderer.render(file, 1, 800) }.problem)
    }

    private fun figure(name: String, page: Int, region: PageRegion, edge: Int = PdfQuality.FIGURE_EDGE) = renderer.renderRegion(fixture(name), page, region, edge)

    @Test
    fun aFigureHasTheEdgeOnItsLongSideAndTheShapeOfTheRegion() {
        val whole = figure("scanned.pdf", 1, PageRegion.Full)
        val image = decode(whole)
        assertEquals(PdfQuality.FIGURE_EDGE, max(image.width, image.height))
        assertEquals(whole.width, image.width)
        assertEquals(whole.height, image.height)
        assertFalse(whole.blank)

        // The top half of a portrait page is wider than tall, and still gets the edge.
        val half = figure("scanned.pdf", 1, PageRegion(0f, 0f, 1f, 0.5f))
        assertEquals(PdfQuality.FIGURE_EDGE, half.width)
        assertTrue(half.height < half.width)
    }

    @Test
    fun aFigureIsAPngOrAJpegWhicheverIsSmallerOnAWhiteBackground() {
        val page = figure("scanned.pdf", 1, PageRegion.Full)
        assertTrue(page.mimeType == "image/png" || page.mimeType == "image/jpeg", page.mimeType)
        assertTrue(page.bytes.size < PdfPageRenderer.MAX_IMAGE_BYTES)
        val corner = decode(page).getRGB(2, 2)
        assertEquals(0xFF, corner shr 16 and 0xFF)
        assertEquals(0xFF, corner and 0xFF)
        // Black text on white is exactly what PNG is good at: the slide with a diagram needn't win it, the scan does.
        val diagram = figure("slides.pdf", 2, PageRegion.Full)
        assertTrue(diagram.mimeType == "image/png" || diagram.mimeType == "image/jpeg")
    }

    @Test
    fun aFigureShowsTheRegionAndNotTheRestOfThePage() {
        val file = fixture("scanned.pdf")
        val full = decode(renderer.renderRegion(file, 1, PageRegion.Full, 1600))
        val regionOfFull = PageRegion(0.1f, 0.1f, 0.6f, 0.4f)
        val crop = decode(renderer.renderRegion(file, 1, regionOfFull, 1600))

        // The same region cut out of the full render, scaled to the crop's size, shows the same amount of ink.
        val box = regionOfFull.toPixels(full.width, full.height)
        val expected = full.getSubimage(box.left, box.top, box.width, box.height)
        val scale = crop.width.toDouble() / expected.width
        val inkExpected = dark(expected) * scale * scale
        val inkCrop = dark(crop).toDouble()
        assertTrue(inkCrop > 0, "the region has text on it")
        assertTrue(Math.abs(inkCrop - inkExpected) / inkExpected < 0.25, "crop $inkCrop, expected about $inkExpected")
    }

    @Test
    fun aRegionWithNothingOnItIsBlankAndOneWithTextIsNot() {
        val file = fixture("scanned.pdf")
        // The page's bottom edge is margin; its text is higher up.
        assertTrue(renderer.renderRegion(file, 1, PageRegion(0.1f, 0.95f, 0.9f, 1f), 800).blank)
        assertFalse(renderer.renderRegion(file, 1, PageRegion(0f, 0f, 1f, 0.5f), 800).blank)
    }

    @Test
    fun aRegionOfARotatedPageIsInTheOrientationItIsShown() {
        val file = File(folder, "rotated.pdf")
        org.apache.pdfbox.pdmodel.PDDocument().use { document ->
            val page = org.apache.pdfbox.pdmodel.PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle(400f, 200f))
            page.rotation = 90
            document.addPage(page)
            org.apache.pdfbox.pdmodel.PDPageContentStream(document, page).use { content ->
                // A black block in the page's own bottom-left corner.
                content.addRect(0f, 0f, 100f, 100f)
                content.fill()
            }
            document.save(file)
        }
        // Rotated by 90°, the page is shown 200 wide and 400 tall, and its bottom-left corner is the shown top-left.
        val whole = renderer.renderRegion(file, 1, PageRegion.Full, 400)
        assertEquals(200, whole.width)
        assertEquals(400, whole.height)
        val topLeft = renderer.renderRegion(file, 1, PageRegion(0f, 0f, 0.5f, 0.25f), 400)
        assertFalse(topLeft.blank, "the block is in the shown top-left quarter")
        val bottomRight = renderer.renderRegion(file, 1, PageRegion(0.5f, 0.5f, 1f, 1f), 400)
        assertTrue(bottomRight.blank)
    }

    @Test
    fun aFigureFromAPageTheDocumentDoesNotHaveOrFromALockedPdfFails() {
        val missing = assertFailsWith<PdfRenderException> { figure("scanned.pdf", 9, PageRegion.Full) }
        assertEquals(SourceProblem.Unsupported, missing.problem)
        val notPdf = File(folder, "notes.pdf").also { it.writeText("just some text") }
        assertEquals(SourceProblem.Unsupported, assertFailsWith<PdfRenderException> { renderer.renderRegion(notPdf, 1, PageRegion.Full, 800) }.problem)
    }
}
