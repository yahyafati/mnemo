package com.yahyafati.mnemo.core.ui.card.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Turns TeX into pictures, for a platform that draws math itself (the desktop app, with JLaTeXMath).
 * [MarkdownText] lays the pictures out with the text: inline formulas sit in the line, display
 * formulas get a line of their own. Android does not use it: its cards with math go to a WebView.
 */
interface MathPainter {
    /**
     * Draws [tex] at [sizePx] pixels per em in [color], or returns null if the formula can't be drawn
     * (the caller then shows the TeX source). [display] is true for `\[…\]` and `$$…$$`.
     */
    fun paint(tex: String, display: Boolean, sizePx: Float, color: Color): MathImage?
}

/** A drawn formula. All sizes are in pixels; [depthPx] is how far it reaches below the text baseline. */
@Immutable
class MathImage(val bitmap: ImageBitmap, val depthPx: Int) {
    val widthPx: Int get() = bitmap.width
    val heightPx: Int get() = bitmap.height
}
