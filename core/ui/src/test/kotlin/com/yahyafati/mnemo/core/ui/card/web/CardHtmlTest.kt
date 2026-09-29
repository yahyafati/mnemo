package com.yahyafati.mnemo.core.ui.card.web

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardHtmlTest {
    private val style = CardHtmlStyle("#111", "#666", "#00f", "#eee", "#00f", "#eef", "#070", "#efe", 22f, 30f, serif = true)

    @Test
    fun servesEverythingFromTheApp() {
        val html = CardHtml.document("\\(x^2\\) ![](media:${"a".repeat(64)})", clozeOrdinal = null, revealed = true, style = style)
        assertTrue("${CardHtml.ORIGIN}/assets/katex/katex.min.js" in html)
        assertTrue("<img src=\"${CardHtml.ORIGIN}/media/${"a".repeat(64)}\">" in html)
        assertTrue("\\(x^2\\)" in html)
        // Nothing points at the network.
        assertFalse(Regex("""(src|href)="https?://(?!appassets\.androidplatform\.net)""").containsMatchIn(html))
    }

    @Test
    fun clozeRendersBothStatesForToggling() {
        val html = CardHtml.document("{{c1::\\(E=mc^2\\)::formula}}", clozeOrdinal = 1, revealed = false, style = style)
        assertTrue("<div class=\"when-hidden\"><p><span class=\"cloze\">[formula]</span></p></div>" in html)
        assertTrue("<span class=\"cloze revealed\">\\(E=mc^2\\)</span>" in html)
        assertTrue("<body class=\"\">" in html)
        assertTrue("<body class=\"revealed\">" in CardHtml.document("{{c1::x}}", 1, revealed = true, style = style))
    }
}
