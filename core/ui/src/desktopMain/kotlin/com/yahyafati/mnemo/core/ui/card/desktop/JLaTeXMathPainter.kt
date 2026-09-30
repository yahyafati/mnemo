package com.yahyafati.mnemo.core.ui.card.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.yahyafati.mnemo.core.ui.card.markdown.MathImage
import com.yahyafati.mnemo.core.ui.card.markdown.MathPainter
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JLabel

/**
 * Card math with JLaTeXMath (ADR 0010): a pure-Java TeX renderer, drawn to a bitmap. It covers what
 * cards use (fractions, roots, sums, integrals, matrices, `aligned`, `\mathbb`, `\binom` …) and
 * throws on a few things KaTeX has (`\cancel`, `\htmlClass`); those formulas return null and the
 * card shows their source. Finished pictures are kept, so a card redrawn while studying is free.
 */
class JLaTeXMathPainter : MathPainter {
    private val cache = object : LinkedHashMap<Key, MathImage?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, MathImage?>?) = size > MAX_ENTRIES
    }

    @Synchronized
    override fun paint(tex: String, display: Boolean, sizePx: Float, color: Color): MathImage? {
        val key = Key(tex, display, sizePx, color.toArgb())
        if (cache.containsKey(key)) return cache[key]
        return draw(tex, display, sizePx, color).also { cache[key] = it }
    }

    private fun draw(tex: String, display: Boolean, sizePx: Float, color: Color): MathImage? = try {
        val formula = TeXFormula(rewrite(tex.trim()))
        // Display formulas are set a little larger than the text, as a typesetter would.
        val style = if (display) TeXConstants.STYLE_DISPLAY else TeXConstants.STYLE_TEXT
        val icon = formula.createTeXIcon(style, if (display) sizePx * DISPLAY_SCALE else sizePx)
        icon.setForeground(java.awt.Color(color.toArgb(), true))
        val width = icon.iconWidth.coerceAtLeast(1)
        val height = icon.iconHeight.coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            icon.paintIcon(JLabel(), graphics, 0, 0)
        } finally {
            graphics.dispose()
        }
        MathImage(image.toComposeImageBitmap(), depthPx = icon.iconDepth.coerceIn(0, height - 1))
    } catch (_: Exception) {
        // Unsupported TeX (a ParseException and friends): the caller shows the source.
        null
    } catch (_: StackOverflowError) {
        null
    }

    /** KaTeX's `\color{red}{x}` is `\textcolor{red}{x}` to JLaTeXMath, which has no braced `\color`. */
    private fun rewrite(tex: String) = BRACED_COLOR.replace(tex) { "\\textcolor{${it.groupValues[1]}}{${it.groupValues[2]}}" }

    private data class Key(val tex: String, val display: Boolean, val sizePx: Float, val argb: Int)

    private companion object {
        val BRACED_COLOR = Regex("""\\color\{([^{}]+)\}\{([^{}]*)\}""")
        const val DISPLAY_SCALE = 1.15f
        const val MAX_ENTRIES = 128
    }
}
