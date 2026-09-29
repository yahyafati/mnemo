package com.yahyafati.mnemo.core.model.markdown

import com.yahyafati.mnemo.core.model.markdown.MarkdownHtml.ClozeMode
import com.yahyafati.mnemo.core.model.markdown.MarkdownHtml.Options
import org.junit.Test
import kotlin.test.assertEquals

class MarkdownHtmlTest {
    private val anki = Options(bareSingleParagraph = true)

    @Test
    fun singleParagraphIsBareInlineHtml() {
        assertEquals(
            "<b>ATP</b> &amp; <i>NADH</i><br>2 &lt; 3",
            MarkdownHtml.render("**ATP** & *NADH*\n2 < 3", anki),
        )
    }

    @Test
    fun blocks() {
        assertEquals(
            "<h2>Steps</h2><ol start=\"3\"><li>Prophase</li></ol><ul><li><code>x</code></li></ul>" +
                "<pre><code class=\"language-kotlin\">a &lt; b</code></pre><blockquote><p>q</p></blockquote><hr><p>end</p>",
            MarkdownHtml.render("## Steps\n3. Prophase\n\n- `x`\n\n```kotlin\na < b\n```\n> q\n\n---\nend", anki),
        )
    }

    @Test
    fun clozeKeptForAnki() {
        assertEquals(
            "{{c1::<b>Mitochondria</b>}} make {{c2::ATP::energy &amp; stuff}}.",
            MarkdownHtml.render("{{c1::**Mitochondria**}} make {{c2::ATP::energy & stuff}}.", anki),
        )
    }

    @Test
    fun clozeShownOnCards() {
        val markdown = "{{c1::A}} and {{c2::B::hint}}"
        assertEquals(
            "<p>A and <span class=\"cloze\">[hint]</span></p>",
            MarkdownHtml.render(markdown, Options(cloze = ClozeMode.Show(2, revealed = false))),
        )
        assertEquals(
            "<p><span class=\"cloze revealed\">A</span> and B</p>",
            MarkdownHtml.render(markdown, Options(cloze = ClozeMode.Show(1, revealed = true))),
        )
    }

    @Test
    fun mediaMathAndSound() {
        val options = anki.copy(imageSrc = { src -> if (src == "media:1") "cell.png" else null })
        assertEquals(
            "<img src=\"cell.png\" alt=\"a cell\"> gone \\(x &lt; 1\\) \\[y\\] [sound:hi.mp3]",
            MarkdownHtml.render("![a cell](media:1) ![gone](media:2) \\(x < 1\\) $\$y$$ [sound:hi.mp3]", options),
        )
    }
}
