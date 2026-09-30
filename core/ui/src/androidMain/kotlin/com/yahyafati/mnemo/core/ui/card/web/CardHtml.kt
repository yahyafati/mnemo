package com.yahyafati.mnemo.core.ui.card.web

import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.markdown.MarkdownHtml
import com.yahyafati.mnemo.core.model.markdown.MarkdownHtml.ClozeMode

/** Colors and type for the math renderer, taken from the Compose theme. CSS color strings. */
data class CardHtmlStyle(
    val text: String,
    val muted: String,
    val link: String,
    val code: String,
    val clozeHidden: String,
    val clozeHiddenBackground: String,
    val clozeRevealed: String,
    val clozeRevealedBackground: String,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val serif: Boolean,
)

/**
 * The HTML document for one side of a card that contains math (ADR 0004). Everything is served
 * from the app ([ORIGIN]): KaTeX and the fonts from assets, images from media storage;
 * nothing is fetched from the network.
 *
 * For cloze cards the question is rendered twice, hidden and revealed, and the renderer toggles
 * between them with a class on `<body>`, so revealing doesn't reload the page.
 */
object CardHtml {
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val BRIDGE = "MnemoCard"

    /**
     * Where the design system's fonts are, among the app's assets: Compose Multiplatform packages
     * `composeResources/<package of Res>/font/…` (the page reads the same files the text does).
     */
    const val FONTS = "$ORIGIN/assets/composeResources/com.yahyafati.mnemo.core.designsystem.resources/font"

    fun document(markdown: String, clozeOrdinal: Int?, revealed: Boolean, style: CardHtmlStyle): String {
        fun render(mode: ClozeMode) = MarkdownHtml.render(
            markdown,
            MarkdownHtml.Options(
                cloze = mode,
                imageSrc = { src -> MediaRef.hashOf(src)?.let { "$ORIGIN/media/$it" } },
                sound = { "<span class=\"sound\">&#128266;</span>" },
            ),
        )
        val body = if (clozeOrdinal == null) {
            render(ClozeMode.Show(ordinal = 0, revealed = true))
        } else {
            "<div class=\"when-hidden\">${render(ClozeMode.Show(clozeOrdinal, revealed = false))}</div>" +
                "<div class=\"when-revealed\">${render(ClozeMode.Show(clozeOrdinal, revealed = true))}</div>"
        }
        val font = if (style.serif) "Newsreader, serif" else "HankenGrotesk, sans-serif"
        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <link rel="stylesheet" href="$ORIGIN/assets/katex/katex.min.css">
            <style>
            @font-face { font-family: Newsreader; src: url('$FONTS/newsreader.ttf'); }
            @font-face { font-family: HankenGrotesk; src: url('$FONTS/hanken_grotesk.ttf'); }
            @font-face { font-family: JetBrainsMono; src: url('$FONTS/jetbrains_mono.ttf'); }
            html, body { margin: 0; padding: 0; background: transparent; }
            body { color: ${style.text}; font-family: $font; font-size: ${style.fontSizePx}px;
                   line-height: ${style.lineHeightPx}px; overflow-wrap: anywhere; }
            body > *:first-child, .when-hidden > *:first-child, .when-revealed > *:first-child { margin-top: 0; }
            p, ul, ol, pre, blockquote, h1, h2, h3, h4, h5, h6 { margin: 0 0 8px 0; }
            a { color: ${style.link}; }
            code, pre { font-family: JetBrainsMono, monospace; font-size: 0.85em; background: ${style.code}; border-radius: 2px; }
            pre { padding: 12px; overflow-x: auto; }
            blockquote { color: ${style.muted}; border-left: 3px solid ${style.muted}; padding-left: 12px; }
            img { max-width: 100%; max-height: 320px; display: block; }
            .cloze { color: ${style.clozeHidden}; background: ${style.clozeHiddenBackground}; font-weight: 600; }
            .cloze.revealed { color: ${style.clozeRevealed}; background: ${style.clozeRevealedBackground}; }
            .katex-display { margin: 8px 0; overflow-x: auto; overflow-y: hidden; }
            body.revealed .when-hidden, body:not(.revealed) .when-revealed { display: none; }
            </style>
            </head>
            <body class="${if (revealed) "revealed" else ""}">
            <div id="content">$body</div>
            <script src="$ORIGIN/assets/katex/katex.min.js"></script>
            <script src="$ORIGIN/assets/katex/auto-render.min.js"></script>
            <script>
            function report() { $BRIDGE.onHeight(Math.ceil(document.getElementById('content').getBoundingClientRect().height)); }
            function setRevealed(r) { document.body.classList.toggle('revealed', r); report(); }
            try {
              renderMathInElement(document.getElementById('content'), {
                delimiters: [{left: '\\[', right: '\\]', display: true}, {left: '\\(', right: '\\)', display: false}],
                throwOnError: false
              });
            } catch (e) {}
            new ResizeObserver(report).observe(document.getElementById('content'));
            document.fonts.ready.then(report);
            report();
            </script>
            </body></html>
        """.trimIndent()
    }
}
