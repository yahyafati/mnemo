package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.ingest.desktop.PdfBoxPageRenderer
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
}
