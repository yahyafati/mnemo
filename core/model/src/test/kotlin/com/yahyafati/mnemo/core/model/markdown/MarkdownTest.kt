package com.yahyafati.mnemo.core.model.markdown

import com.yahyafati.mnemo.core.model.markdown.Markdown.Block
import com.yahyafati.mnemo.core.model.markdown.Markdown.Inline
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownTest {
    private fun text(s: String) = Inline.Text(s)

    @Test
    fun blocks() {
        val blocks = Markdown.parse(
            """
            # Title
            First line
            second line

            - one
            - **two**

            1. a
            2. b

            > quoted

            ```kotlin
            val x = 1 // *not italic*
            ```
            ---
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Block.Heading(1, listOf(text("Title"))),
                Block.Paragraph(listOf(text("First line\nsecond line"))),
                Block.ListBlock(false, 1, listOf(listOf(text("one")), listOf(Inline.Bold(listOf(text("two")))))),
                Block.ListBlock(true, 1, listOf(listOf(text("a")), listOf(text("b")))),
                Block.Quote(listOf(Block.Paragraph(listOf(text("quoted"))))),
                Block.CodeBlock("kotlin", "val x = 1 // *not italic*"),
                Block.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun emphasis() {
        assertEquals(
            listOf(
                Inline.Bold(listOf(text("bold"))),
                text(" and "),
                Inline.Italic(listOf(text("italic"))),
                text(" and "),
                Inline.Strike(listOf(text("gone"))),
                text(" "),
                Inline.Code("a*b"),
            ),
            Markdown.parseInline("**bold** and *italic* and ~~gone~~ `a*b`"),
        )
    }

    @Test
    fun literalAsterisksAndUnderscores() {
        assertEquals(listOf(text("2 * 3 * 4")), Markdown.parseInline("2 * 3 * 4"))
        assertEquals(listOf(text("snake_case_name")), Markdown.parseInline("snake_case_name"))
        assertEquals(listOf(text("*escaped*")), Markdown.parseInline("\\*escaped\\*"))
        assertEquals(listOf(text("**unclosed")), Markdown.parseInline("**unclosed"))
    }

    @Test
    fun nestedEmphasisInsideBold() {
        assertEquals(
            listOf(Inline.Bold(listOf(text("very "), Inline.Italic(listOf(text("much")))))),
            Markdown.parseInline("**very *much***"),
        )
    }

    @Test
    fun clozeWithMarkdownAnswerAndHint() {
        assertEquals(
            listOf(
                text("The "),
                Inline.Cloze(1, listOf(Inline.Bold(listOf(text("amygdala")))), "structure"),
                text(" and "),
                Inline.Cloze(2, listOf(text("fear")), null),
            ),
            Markdown.parseInline("The {{c1::**amygdala**::structure}} and {{c2::fear}}"),
        )
    }

    @Test
    fun links() {
        assertEquals(
            listOf(Inline.Link(listOf(text("docs")), "https://example.com")),
            Markdown.parseInline("[docs](https://example.com)"),
        )
    }

    @Test
    fun imagesAndSounds() {
        assertEquals(
            listOf(Inline.Image("cell", "media:abc"), text(" "), Inline.Sound("media:def")),
            Markdown.parseInline("![cell](media:abc) [sound:media:def]"),
        )
        // Not a sound tag or a link: plain text.
        assertEquals(listOf(text("[sound:] and [x]")), Markdown.parseInline("[sound:] and [x]"))
    }

    @Test
    fun math() {
        assertEquals(
            listOf(
                text("Energy "),
                Inline.Math("E = mc^2", display = false),
                text(" and "),
                Inline.Math("\\int_0^1 x\\,dx", display = true),
                text(" "),
                Inline.Math("a*b*c", display = true),
            ),
            Markdown.parseInline("Energy \\(E = mc^2\\) and \\[\\int_0^1 x\\,dx\\] \$\$a*b*c\$\$"),
        )
        // A lone dollar is text, an escaped parenthesis is not math, an unclosed delimiter is text.
        assertEquals(listOf(text("costs $5 (")), Markdown.parseInline("costs $5 \\("))
        assertEquals(listOf(text("\\(x)")), Markdown.parseInline("\\\\(x)"))
    }

    @Test
    fun mathInsideCloze() {
        assertEquals(
            listOf(Inline.Cloze(1, listOf(Inline.Math("x^2", display = false)), null)),
            Markdown.parseInline("{{c1::\\(x^2\\)}}"),
        )
        assertTrue(Markdown.containsMath("{{c1::\\(x^2\\)}}"))
        assertFalse(Markdown.containsMath("`\\(x\\)` in code"))
        assertFalse(Markdown.containsMath("plain"))
    }

    @Test
    fun plainText() {
        assertEquals(
            "Title The amygdala and fear one two",
            Markdown.plainText("# Title\nThe {{c1::**amygdala**::structure}} and\n*fear*\n\n- one\n- two ![](media:x)"),
        )
    }
}
