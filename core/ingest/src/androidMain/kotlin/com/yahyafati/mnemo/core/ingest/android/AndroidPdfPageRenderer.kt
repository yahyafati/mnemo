package com.yahyafati.mnemo.core.ingest.android

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.yahyafati.mnemo.core.ingest.InkCounter
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.encodeWithin
import com.yahyafati.mnemo.core.ingest.fitLongEdge
import com.yahyafati.mnemo.core.model.SourceProblem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * [PdfPageRenderer] on the platform's `PdfRenderer` (PDFium), which reads every codec scans use (CCITT, JBIG2,
 * JPEG 2000). It needs a seekable file, which is why a picked PDF is copied into the cache when it is opened.
 */
class AndroidPdfPageRenderer : PdfPageRenderer {
    override fun render(file: File, page: Int, longEdge: Int): RenderedPage = try {
        // `PdfRenderer` is not thread-safe, not even across documents, and keeps one page open at a time.
        synchronized(lock) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (page !in 1..renderer.pageCount) throw PdfRenderException(SourceProblem.Unsupported, "No page $page")
                    renderer.openPage(page - 1).use { pdfPage ->
                        encodeWithin(longEdge) { edge, quality -> renderPage(pdfPage, edge, quality) }
                    }
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

    private companion object {
        val lock = Any()
    }
}
