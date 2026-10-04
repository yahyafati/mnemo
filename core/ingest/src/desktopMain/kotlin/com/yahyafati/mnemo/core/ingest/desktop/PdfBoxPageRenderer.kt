package com.yahyafati.mnemo.core.ingest.desktop

import com.yahyafati.mnemo.core.ingest.InkCounter
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.encodeWithin
import com.yahyafati.mnemo.core.ingest.figureGeometry
import com.yahyafati.mnemo.core.ingest.smallerOf
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.SourceProblem
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max

/**
 * [PdfPageRenderer] on Apache PDFBox. JBIG2 scans need the `jbig2-imageio` plugin (it registers itself with ImageIO);
 * JPEG 2000 has none (ADR 0014), so such a page comes out white and the caller sees [RenderedPage.blank].
 */
class PdfBoxPageRenderer : PdfPageRenderer {
    override fun render(file: File, page: Int, longEdge: Int): RenderedPage = withDocument(file, page) { document ->
        val box = document.getPage(page - 1).cropBox
        // A page is drawn at `scale` × 72 dpi; with the rotation already in the renderer's output, only the long side counts.
        val longSide = max(box.width, box.height)
        if (longSide <= 0f) throw PdfRenderException(SourceProblem.Unsupported, "Page $page has no size")
        val renderer = PDFRenderer(document)
        encodeWithin(longEdge) { edge, quality ->
            // RGB is filled white by PDFBox, so the JPEG has no black background.
            val image = renderer.renderImage(page - 1, edge / longSide, ImageType.RGB)
            try {
                RenderedPage(jpeg(image, quality), "image/jpeg", image.width, image.height, blank = !hasInk(image))
            } finally {
                image.flush()
            }
        }
    }

    override fun renderRegion(file: File, page: Int, region: PageRegion, longEdge: Int): RenderedPage = withDocument(file, page) { document ->
        val pdfPage = document.getPage(page - 1)
        val box = pdfPage.cropBox
        // The region is a fraction of the page as it is shown, so a rotated page swaps its sides.
        val turned = pdfPage.rotation % 180 != 0
        val pageWidth = if (turned) box.height else box.width
        val pageHeight = if (turned) box.width else box.height
        if (pageWidth <= 0f || pageHeight <= 0f) throw PdfRenderException(SourceProblem.Unsupported, "Page $page has no size")
        val renderer = PDFRenderer(document)
        encodeWithin(longEdge) { edge, quality ->
            val geometry = figureGeometry(pageWidth, pageHeight, region, edge)
            val image = BufferedImage(geometry.width, geometry.height, BufferedImage.TYPE_INT_RGB)
            try {
                val graphics = image.createGraphics()
                try {
                    graphics.background = Color.WHITE
                    graphics.color = Color.WHITE
                    graphics.fillRect(0, 0, geometry.width, geometry.height)
                    // Shifted in pixels before the renderer scales and rotates the page, so the region's corner lands on the origin.
                    graphics.translate((-region.left * pageWidth * geometry.scale).toDouble(), (-region.top * pageHeight * geometry.scale).toDouble())
                    renderer.renderPageToGraphics(page - 1, graphics, geometry.scale)
                } finally {
                    graphics.dispose()
                }
                val blank = !hasInk(image)
                smallerOf(
                    RenderedPage(jpeg(image, quality), "image/jpeg", image.width, image.height, blank),
                    RenderedPage(png(image), "image/png", image.width, image.height, blank),
                )
            } finally {
                image.flush()
            }
        }
    }

    private fun withDocument(file: File, page: Int, draw: (org.apache.pdfbox.pdmodel.PDDocument) -> RenderedPage): RenderedPage = try {
        Loader.loadPDF(file).use { document ->
            if (page !in 1..document.numberOfPages) throw PdfRenderException(SourceProblem.Unsupported, "No page $page")
            draw(document)
        }
    } catch (e: PdfRenderException) {
        throw e
    } catch (e: InvalidPasswordException) {
        throw PdfRenderException(SourceProblem.Encrypted, cause = e)
    } catch (e: NoClassDefFoundError) {
        // Certificate-encrypted PDFs need BouncyCastle, which Mnemo doesn't ship.
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

    private fun png(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        check(ImageIO.write(image, "png", out)) { "Couldn't encode the figure" }
        return out.toByteArray()
    }

    private fun jpeg(image: BufferedImage, quality: Int): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / 100f
            }
            val out = ByteArrayOutputStream()
            // Not ImageIO.createImageOutputStream: that may use a disk cache file for what is a memory buffer.
            MemoryCacheImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(image, null, null), params)
            }
            return out.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private fun hasInk(image: BufferedImage): Boolean {
        val ink = InkCounter(image.width * image.height)
        val row = IntArray(image.width)
        for (y in 0 until image.height) {
            image.getRGB(0, y, image.width, 1, row, 0, image.width)
            ink.add(row)
        }
        return ink.hasInk
    }
}
