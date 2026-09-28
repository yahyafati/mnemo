package com.yahyafati.mnemo.core.ui.card.markdown

import com.yahyafati.mnemo.core.ui.card.markdown.Markdown.Block
import com.yahyafati.mnemo.core.ui.card.markdown.Markdown.Inline
import org.junit.Test
import kotlin.test.assertEquals

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
}
