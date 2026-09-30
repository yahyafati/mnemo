package com.yahyafati.mnemo.core.ui.card

import androidx.compose.ui.graphics.Color
import com.yahyafati.mnemo.core.ui.card.desktop.JLaTeXMathPainter
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JLaTeXMathPainterTest {
    private val painter = JLaTeXMathPainter()

    @Test
    fun aFormulaBecomesAPictureThatSitsOnTheBaseline() {
        val image = assertNotNull(painter.paint("\\frac{a}{b} + x^2", display = false, sizePx = 24f, color = Color.Black))

        assertTrue(image.widthPx > 24 && image.heightPx > 24)
        // A fraction reaches below the baseline, but not past its own bottom.
        assertTrue(image.depthPx in 1 until image.heightPx)
    }

    @Test
    fun aDisplayFormulaIsLargerThanTheSameInline() {
        val inline = assertNotNull(painter.paint("\\sum_{i=1}^{n} i", display = false, sizePx = 20f, color = Color.Black))
        val display = assertNotNull(painter.paint("\\sum_{i=1}^{n} i", display = true, sizePx = 20f, color = Color.Black))

        assertTrue(display.heightPx > inline.heightPx)
    }

    @Test
    fun whatTheRendererCannotParseIsNull() {
        assertNull(painter.paint("\\cancel{x}", display = false, sizePx = 20f, color = Color.Black))
        assertNull(painter.paint("\\unknownCommand", display = false, sizePx = 20f, color = Color.Black))
    }

    @Test
    fun aBracedColorIsRewrittenToTheFormWhichJLaTeXMathKnows() {
        assertNotNull(painter.paint("\\color{red}{x} + y", display = false, sizePx = 20f, color = Color.Black))
    }

    @Test
    fun theSameFormulaInTheSameSizeIsDrawnOnce() {
        val first = painter.paint("E = mc^2", display = false, sizePx = 20f, color = Color.Black)

        assertSame(first, painter.paint("E = mc^2", display = false, sizePx = 20f, color = Color.Black))
    }
}
