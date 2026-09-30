package com.yahyafati.mnemo.core.ui.card.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.yahyafati.mnemo.core.ui.card.markdown.ClozeDisplay
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText

/**
 * Draws card Markdown that contains math (`\(…\)`, `\[…\]`, `$$…$$`). Everything else is drawn
 * natively by `MarkdownText`; only cards with math go through a renderer (ADR 0004). Android
 * renders it with KaTeX in a WebView; the desktop app will use JLaTeXMath (desktop D5).
 */
interface MathRenderer {
    @Composable
    fun Render(
        markdown: String,
        style: TextStyle,
        serif: Boolean,
        modifier: Modifier,
        clozeOrdinal: Int?,
        revealed: Boolean,
    )

    companion object {
        /**
         * Shows the TeX as it was typed, in the card's own text. The fallback for a platform with
         * no math renderer, and for a formula a renderer can't draw.
         */
        val RawTex: MathRenderer = object : MathRenderer {
            @Composable
            override fun Render(
                markdown: String,
                style: TextStyle,
                serif: Boolean,
                modifier: Modifier,
                clozeOrdinal: Int?,
                revealed: Boolean,
            ) {
                MarkdownText(
                    markdown = markdown,
                    style = style,
                    cloze = clozeOrdinal?.let { ClozeDisplay(it, revealed) },
                    modifier = modifier,
                )
            }
        }
    }
}

/** The app's [MathRenderer]; [MathRenderer.RawTex] unless the app shell provides one. */
val LocalMathRenderer = staticCompositionLocalOf { MathRenderer.RawTex }

/** Card Markdown that contains math, drawn by the platform's [MathRenderer]. */
@Composable
fun MathText(
    markdown: String,
    style: TextStyle,
    serif: Boolean,
    modifier: Modifier = Modifier,
    clozeOrdinal: Int? = null,
    revealed: Boolean = true,
) {
    LocalMathRenderer.current.Render(markdown, style, serif, modifier, clozeOrdinal, revealed)
}
