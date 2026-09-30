package com.yahyafati.mnemo.core.ui.card.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.yahyafati.mnemo.core.ui.card.markdown.ClozeDisplay
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText
import com.yahyafati.mnemo.core.ui.card.markdown.MathPainter
import com.yahyafati.mnemo.core.ui.card.web.MathRenderer

/**
 * The desktop [MathRenderer]: the card's Markdown drawn natively by `MarkdownText`, with each
 * formula drawn by [painter] and laid out in the text. A formula the painter can't draw shows its
 * TeX source, like the fallback.
 */
class JLaTeXMathRenderer(private val painter: MathPainter) : MathRenderer {
    @Composable
    override fun Render(
        markdown: String,
        style: TextStyle,
        serif: Boolean,
        modifier: Modifier,
        clozeOrdinal: Int?,
        revealed: Boolean,
    ) {
        // `serif` picks the font for the WebView's HTML; here the style already carries the card's font.
        MarkdownText(
            markdown = markdown,
            style = style,
            cloze = clozeOrdinal?.let { ClozeDisplay(it, revealed) },
            math = painter,
            modifier = modifier,
        )
    }
}
