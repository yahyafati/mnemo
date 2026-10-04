package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.SourceProblem
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders pages of a PDF file to JPEG (docs/pdf/ROADMAP.md, P3, ADR 0014): Android's `PdfRenderer` on the phone,
 * Apache PDFBox on the desktop. Blocking; call it off the main thread. Nothing is kept between calls: a renderer
 * holds no pages, and the repository caches the files.
 */
interface PdfPageRenderer {
    /**
     * [page] is 1-based. The image is [longEdge] px on its long side (at most: it is made smaller when it would be over
     * [MAX_IMAGE_BYTES]), JPEG, on a white background.
     *
     * @throws PdfRenderException when the file or the page can't be rendered.
     */
    fun render(file: File, page: Int, longEdge: Int): RenderedPage

    companion object {
        /** An encoded page larger than this is encoded again, smaller (a limit that several providers share). */
        const val MAX_IMAGE_BYTES = 1_500_000
    }
}

/** A rendered page: [bytes] are an image of [mimeType]. [blank] says nothing dark was drawn on it. */
class RenderedPage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val blank: Boolean,
)

/** A page that can't be rendered, with the [problem] to tell the user. */
class PdfRenderException(val problem: SourceProblem, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: problem.name, cause)

/** The pixel size of a page of [pageWidth] × [pageHeight] (any unit) when its long side is [longEdge], at least 1 px each way. */
internal fun fitLongEdge(pageWidth: Float, pageHeight: Float, longEdge: Int): Pair<Int, Int> {
    require(pageWidth > 0f && pageHeight > 0f) { "A page has a size" }
    val scale = longEdge / max(pageWidth, pageHeight)
    return max(1, (pageWidth * scale).roundToInt()) to max(1, (pageHeight * scale).roundToInt())
}

/**
 * Encodes a page and, when the result is over [limit] bytes, again at lower quality and then on a smaller edge
 * (ADR 0014). [encode] renders the page with its long side at `edge` px and encodes it at JPEG `quality` (1–100).
 * The last attempt is returned even when it is still too large: a page that big at 400 px can't be helped.
 */
internal fun encodeWithin(
    longEdge: Int,
    limit: Int = PdfPageRenderer.MAX_IMAGE_BYTES,
    encode: (edge: Int, quality: Int) -> RenderedPage,
): RenderedPage {
    var attempt = encode(longEdge, JPEG_QUALITY)
    for ((factor, quality) in SHRINK_STEPS) {
        if (attempt.bytes.size <= limit) break
        val edge = (longEdge * factor).roundToInt()
        if (edge < MIN_EDGE) break
        attempt = encode(edge, quality)
    }
    return attempt
}

private const val JPEG_QUALITY = 80
private const val MIN_EDGE = 400
private val SHRINK_STEPS = listOf(1.0 to 60, 0.75 to 60, 0.5 to 50, 0.35 to 50)

/**
 * Tells a page with something on it from a white one. Feed it the pixels (`0xAARRGGBB`, any order of rows) and ask
 * [hasInk]: a few dark pixels are enough, a stray speck is not.
 */
internal class InkCounter(totalPixels: Int) {
    private var dark = 0
    private val needed = max(MIN_DARK_PIXELS, totalPixels / MIN_DARK_FRACTION)

    fun add(pixels: IntArray, count: Int = pixels.size) {
        for (i in 0 until count) {
            val argb = pixels[i]
            val red = argb shr 16 and 0xFF
            val green = argb shr 8 and 0xFF
            val blue = argb and 0xFF
            // Rec. 601 luma in integers; anything this dark is ink, not paper.
            if ((red * 299 + green * 587 + blue * 114) / 1000 < DARK_BELOW) dark++
        }
    }

    val hasInk: Boolean get() = dark >= needed

    private companion object {
        const val DARK_BELOW = 160
        const val MIN_DARK_PIXELS = 12

        /** One dark pixel in this many (0.005%) at least: a line of text has far more. */
        const val MIN_DARK_FRACTION = 20_000
    }
}
