package com.yahyafati.mnemo.core.model

import kotlin.math.roundToInt

/** A corner of a [PageRegion], named as the page is shown. */
enum class RegionCorner { TopLeft, TopRight, BottomLeft, BottomRight }

/** A rectangle in whole pixels: [left] and [top] are the first pixel, [width] and [height] at least 1. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/**
 * The part of a PDF page a figure is cut from (docs/pdf/ROADMAP.md, P7): a rectangle as fractions (0 to 1) of the
 * page's width and height, counted from the top left of the page **as it is shown** (rotation applied). Fractions,
 * not points or pixels, so one region means the same place at any rendering size, and a crop drawn on a small
 * preview is cut from a large render.
 *
 * It is never empty or inside out ([MIN_SIZE] each way) and never leaves the page: every edit below keeps that.
 */
data class PageRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) { "A region lies on the page: $this" }
        require(right - left >= MIN_SIZE - EPSILON && bottom - top >= MIN_SIZE - EPSILON) { "A region has a size: $this" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Moved by ([dx], [dy]) as fractions of the page, stopping at the page's edge instead of shrinking. */
    fun moved(dx: Float, dy: Float): PageRegion {
        val newLeft = (left + dx).coerceIn(0f, 1f - width)
        val newTop = (top + dy).coerceIn(0f, 1f - height)
        return PageRegion(newLeft, newTop, newLeft + width, newTop + height)
    }

    /**
     * [corner] put at ([x], [y]) (fractions of the page), the opposite corner staying where it is. A corner dragged
     * past the opposite one stops at [MIN_SIZE] from it rather than flipping the rectangle over.
     */
    fun withCorner(corner: RegionCorner, x: Float, y: Float): PageRegion {
        val px = x.coerceIn(0f, 1f)
        val py = y.coerceIn(0f, 1f)
        val fromLeft = corner == RegionCorner.TopLeft || corner == RegionCorner.BottomLeft
        val fromTop = corner == RegionCorner.TopLeft || corner == RegionCorner.TopRight
        return PageRegion(
            left = if (fromLeft) px.coerceAtMost(right - MIN_SIZE) else left,
            top = if (fromTop) py.coerceAtMost(bottom - MIN_SIZE) else top,
            right = if (fromLeft) right else px.coerceAtLeast(left + MIN_SIZE),
            bottom = if (fromTop) bottom else py.coerceAtLeast(top + MIN_SIZE),
        )
    }

    /** The bottom-right corner moved by ([dw], [dh]): how the keyboard makes the rectangle larger or smaller. */
    fun resized(dw: Float, dh: Float): PageRegion = withCorner(RegionCorner.BottomRight, right + dw, bottom + dh)

    /** The pixels this region covers on an image of [imageWidth] × [imageHeight] (the page drawn at any scale), at least one each way. */
    fun toPixels(imageWidth: Int, imageHeight: Int): PixelRect {
        require(imageWidth > 0 && imageHeight > 0) { "An image has a size" }
        val x0 = (left * imageWidth).roundToInt().coerceIn(0, imageWidth - 1)
        val y0 = (top * imageHeight).roundToInt().coerceIn(0, imageHeight - 1)
        val x1 = (right * imageWidth).roundToInt().coerceIn(x0 + 1, imageWidth)
        val y1 = (bottom * imageHeight).roundToInt().coerceIn(y0 + 1, imageHeight)
        return PixelRect(x0, y0, x1 - x0, y1 - y0)
    }

    /** A name for this region that is safe in a file name: the edges in thousandths of the page. */
    val key: String get() = listOf(left, top, right, bottom).joinToString("-") { (it * 1000).roundToInt().toString() }

    companion object {
        /** The smallest a region is, in either direction, as a fraction of the page: 5%. */
        const val MIN_SIZE = 0.05f

        private const val EPSILON = 0.0001f

        /** The whole page. */
        val Full = PageRegion(0f, 0f, 1f, 1f)

        /** A region [inset] in from each edge of the page: where a new crop starts. */
        fun inset(inset: Float): PageRegion {
            val edge = inset.coerceIn(0f, (1f - MIN_SIZE) / 2f)
            return PageRegion(edge, edge, 1f - edge, 1f - edge)
        }
    }
}

/** The side of a card a figure is put on. */
enum class FigureSide {
    Front,
    Back,
    ;

    companion object {
        /**
         * The sides of a [kind] of card a picture can go on. A multiple-choice card's back is the text of the right option,
         * and a type-in card's is what the answer is compared with: neither is a place for an image.
         */
        fun allowedFor(kind: NoteKind): List<FigureSide> = when (kind) {
            NoteKind.Basic, NoteKind.Reversed, NoteKind.Cloze -> listOf(Front, Back)
            NoteKind.MultipleChoice, NoteKind.TypeIn -> listOf(Front)
        }
    }
}

/**
 * A picture cut from a PDF page for a card in the review queue: [file] is the crop (JPEG or PNG, in the cache) and [side]
 * the field it joins when the card is accepted. Queue only: nothing is stored until then.
 */
data class CardFigure(val file: java.io.File, val side: FigureSide)
