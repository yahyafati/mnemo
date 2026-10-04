package com.yahyafati.mnemo.pdf

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.android.AndroidPdfPageRenderer
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/**
 * The real `PdfRenderer` on a device (docs/pdf/ROADMAP.md, P3): Robolectric can't run it, so this is the only automatic
 * check that PDFium draws the fixtures. `./gradlew :app:connectedDebugAndroidTest --tests "*AndroidPdfPageRendererTest"`.
 * The fixtures are the ones in `core/ingest/src/commonTest/resources/pdf`, added to this test's assets.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPdfPageRendererTest {
    private val renderer = AndroidPdfPageRenderer()
    private val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "render-test").also { it.mkdirs() }

    @After
    fun clean() {
        folder.deleteRecursively()
    }

    private fun fixture(name: String): File {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        return File(folder, name).also { file -> assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } } }
    }

    private fun render(name: String, page: Int, edge: Int) = renderer.render(fixture(name), page, edge)

    @Test
    fun everyScanEncodingRendersALegibleJpegWithAWhiteBackground() {
        for (name in listOf("scanned.pdf", "scanned-ccitt.pdf", "scanned-jbig2.pdf")) {
            for (quality in PdfQuality.entries) {
                val page = render(name, 2, quality.longEdge)
                assertEquals(name, "image/jpeg", page.mimeType)
                assertFalse("$name should not be blank", page.blank)
                assertTrue(page.bytes.size < PdfPageRenderer.MAX_IMAGE_BYTES)
                val bitmap = BitmapFactory.decodeByteArray(page.bytes, 0, page.bytes.size)
                assertEquals(quality.longEdge, max(bitmap.width, bitmap.height))
                // The corner is paper, not the black a transparent bitmap becomes in a JPEG.
                val corner = bitmap.getPixel(2, 2)
                assertTrue("$name corner is white", (corner shr 16 and 0xFF) > 0xF0)
                bitmap.recycle()
            }
        }
    }

    @Test
    fun slidesKeepTheirShapeAndTextPagesRender() {
        val slide = render("slides.pdf", 3, 1568)
        assertEquals(1568, slide.width)
        assertEquals(882, slide.height)
        assertFalse(slide.blank)
        assertFalse(render("outline.pdf", 3, 800).blank)
        assertFalse(render("ocr-layer.pdf", 1, 800).blank)
    }

    @Test
    fun aPageTheDocumentDoesNotHaveFails() {
        try {
            render("scanned.pdf", 4, 800)
            fail("page 4 of a three-page PDF")
        } catch (e: PdfRenderException) {
            assertEquals(SourceProblem.Unsupported, e.problem)
        }
    }

    @Test
    fun aFileThatIsNotAPdfFails() {
        val file = File(folder, "notes.pdf").also { it.writeText("just some text") }
        try {
            renderer.render(file, 1, 800)
            fail("not a PDF")
        } catch (e: PdfRenderException) {
            assertEquals(SourceProblem.Unsupported, e.problem)
        }
    }

    @Test
    fun aFigureIsTheRegionDrawnAtTheFigureEdgeAndWhiteWhereThePageIsEmpty() {
        val file = fixture("scanned.pdf")
        val crop = renderer.renderRegion(file, 1, PageRegion(0f, 0f, 1f, 0.5f), PdfQuality.FIGURE_EDGE)
        assertTrue(crop.mimeType == "image/png" || crop.mimeType == "image/jpeg")
        assertEquals(PdfQuality.FIGURE_EDGE, crop.width)
        assertTrue(crop.height < crop.width)
        assertFalse("the top half of the page has text", crop.blank)
        assertTrue(crop.bytes.size < PdfPageRenderer.MAX_IMAGE_BYTES)
        val bitmap = BitmapFactory.decodeByteArray(crop.bytes, 0, crop.bytes.size)
        assertEquals(crop.width, bitmap.width)
        assertEquals(crop.height, bitmap.height)
        val corner = bitmap.getPixel(2, 2)
        assertTrue("the corner is paper", (corner shr 16 and 0xFF) > 0xF0)
        bitmap.recycle()

        // The page's bottom edge is margin.
        assertTrue(renderer.renderRegion(file, 1, PageRegion(0.1f, 0.95f, 0.9f, 1f), 800).blank)
    }

    @Test
    fun aFigureFromAPageTheDocumentDoesNotHaveFails() {
        try {
            renderer.renderRegion(fixture("scanned.pdf"), 4, PageRegion.Full, 800)
            fail("page 4 of a three-page PDF")
        } catch (e: PdfRenderException) {
            assertEquals(SourceProblem.Unsupported, e.problem)
        }
    }
}
