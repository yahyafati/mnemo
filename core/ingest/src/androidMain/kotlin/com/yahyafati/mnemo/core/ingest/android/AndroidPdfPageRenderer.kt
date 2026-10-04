package com.yahyafati.mnemo.core.ingest.android

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.yahyafati.mnemo.core.ingest.InkCounter
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.encodeWithin
import com.yahyafati.mnemo.core.ingest.figureGeometry
import com.yahyafati.mnemo.core.ingest.fitLongEdge
import com.yahyafati.mnemo.core.ingest.smallerOf
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.SourceProblem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * [PdfPageRenderer] on the platform's `PdfRenderer` (PDFium), which reads every codec scans use (CCITT, JBIG2,
 * JPEG 2000). It needs a seekable file, which is why a picked PDF is copied into the cache when it is opened.
 */
class AndroidPdfPageRenderer : PdfPageRenderer {
    override fun render(file: File, page: Int, longEdge: Int): RenderedPage =
        withPage(file, page) { pdfPage -> encodeWithin(longEdge) { edge, quality -> renderPage(pdfPage, edge, quality) } }

    override fun renderRegion(file: File, page: Int, region: PageRegion, longEdge: Int): RenderedPage =
        withPage(file, page) { pdfPage -> encodeWithin(longEdge) { edge, quality -> renderCrop(pdfPage, region, edge, quality) } }

    private fun withPage(file: File, page: Int, draw: (PdfRenderer.Page) -> RenderedPage): RenderedPage = try {
        // `PdfRenderer` is not thread-safe, not even across documents, and keeps one page open at a time.
        synchronized(lock) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (page !in 1..renderer.pageCount) throw PdfRenderException(SourceProblem.Unsupported, "No page $page")
                    renderer.openPage(page - 1).use(draw)
                }
            }
        }
    } catch (e: PdfRenderException) {
        throw e
    } catch (e: SecurityException) {
        // `PdfRenderer` says a PDF that needs a password this way.
        throw PdfRenderException(SourceProblem.Encrypted, cause = e)
    } catch (e: IOException) {
        throw PdfRenderException(SourceProblem.Unsupported, e.message, e)
    } catch (e: IllegalStateException) {
        throw PdfRenderException(SourceProblem.Unsupported, e.message, e)
    } catch (e: IllegalArgumentException) {
        throw PdfRenderException(SourceProblem.Unsupported, e.message, e)
    } catch (e: OutOfMemoryError) {
        throw PdfRenderException(SourceProblem.TooLarge, cause = e)
    }

    private fun renderPage(page: PdfRenderer.Page, edge: Int, quality: Int): RenderedPage {
        val (width, height) = fitLongEdge(page.width.toFloat(), page.height.toFloat(), edge)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            // `PdfRenderer` draws on top of what is there, and a new bitmap is transparent: JPEG would make it black.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val ink = InkCounter(width * height)
            val row = IntArray(width)
            for (y in 0 until height) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1)
                ink.add(row)
            }
            val out = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Couldn't encode the page" }
            return RenderedPage(out.toByteArray(), "image/jpeg", width, height, blank = !ink.hasInk)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * [region] of the page, drawn at the scale that makes it [edge] px on its long side. `render` with a matrix draws the
     * page at one pixel to the point and then applies it, so scaling and shifting the region's corner to the origin is all
     * it takes; the bitmap is only as large as the crop.
     */
    private fun renderCrop(page: PdfRenderer.Page, region: PageRegion, edge: Int, quality: Int): RenderedPage {
        val geometry = figureGeometry(page.width.toFloat(), page.height.toFloat(), region, edge)
        val bitmap = Bitmap.createBitmap(geometry.width, geometry.height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            val matrix = Matrix().apply {
                postScale(geometry.scale, geometry.scale)
                postTranslate(-region.left * page.width * geometry.scale, -region.top * page.height * geometry.scale)
            }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val ink = InkCounter(geometry.width * geometry.height)
            val row = IntArray(geometry.width)
            for (y in 0 until geometry.height) {
                bitmap.getPixels(row, 0, geometry.width, 0, y, geometry.width, 1)
                ink.add(row)
            }
            val blank = !ink.hasInk
            return smallerOf(
                RenderedPage(encode(bitmap, Bitmap.CompressFormat.JPEG, quality), "image/jpeg", geometry.width, geometry.height, blank),
                RenderedPage(encode(bitmap, Bitmap.CompressFormat.PNG, 100), "image/png", geometry.width, geometry.height, blank),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        check(bitmap.compress(format, quality, out)) { "Couldn't encode the figure" }
        return out.toByteArray()
    }

    private companion object {
        val lock = Any()
    }
}
