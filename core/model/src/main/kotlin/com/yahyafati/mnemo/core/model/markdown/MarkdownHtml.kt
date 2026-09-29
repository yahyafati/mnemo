package com.yahyafati.mnemo.core.model.markdown

import com.yahyafati.mnemo.core.model.markdown.Markdown.Block
import com.yahyafati.mnemo.core.model.markdown.Markdown.Inline

/**
 * Renders card Markdown as HTML: for Anki fields on export, and for the math renderer on cards
 * that contain math. Math is written with MathJax delimiters (`\(…\)`, `\[…\]`), which both
 * Anki's MathJax and KaTeX's auto-render understand.
 */
object MarkdownHtml {
    sealed interface ClozeMode {
        /** `{{cN::…}}` stays as text, with its content rendered: Anki fields. */
        data object Keep : ClozeMode

        /** Deletion [ordinal] is hidden (or highlighted once [revealed]); others show plainly. */
        data class Show(val ordinal: Int, val revealed: Boolean) : ClozeMode
    }

    data class Options(
        val cloze: ClozeMode = ClozeMode.Keep,
        /** The `src` to write for an image, or null to show its alt text instead. */
        val imageSrc: (String) -> String? = { it },
        /** The HTML for a sound tag. Anki's own syntax by default. */
        val sound: (String) -> String = { "[sound:${escape(it)}]" },
        /** Render a lone paragraph as bare inline HTML, as Anki's editor stores simple fields. */
        val bareSingleParagraph: Boolean = false,
    )

    fun render(markdown: String, options: Options = Options()): String {
        val blocks = Markdown.parse(markdown)
        val single = blocks.singleOrNull()
        if (options.bareSingleParagraph && single is Block.Paragraph) {
            return buildString { inlines(single.content, options) }
        }
        return buildString { blocks(blocks, options) }
    }

    private fun StringBuilder.blocks(blocks: List<Block>, options: Options) {
        for (block in blocks) {
            when (block) {
                is Block.Paragraph -> tag("p") { inlines(block.content, options) }
                is Block.Heading -> tag("h${block.level}") { inlines(block.content, options) }
                is Block.CodeBlock -> {
                    append("<pre><code")
                    if (block.language != null) append(" class=\"language-").append(escape(block.language)).append('"')
                    append('>').append(escape(block.code)).append("</code></pre>")
                }
                is Block.Quote -> tag("blockquote") { blocks(block.blocks, options) }
                is Block.ListBlock -> {
                    if (block.ordered) {
                        append(if (block.start == 1) "<ol>" else "<ol start=\"${block.start}\">")
                    } else {
                        append("<ul>")
                    }
                    block.items.forEach { item -> tag("li") { inlines(item, options) } }
                    append(if (block.ordered) "</ol>" else "</ul>")
                }
                Block.Rule -> append("<hr>")
            }
        }
    }

    private fun StringBuilder.inlines(inlines: List<Inline>, options: Options) {
        for (inline in inlines) {
            when (inline) {
                is Inline.Text -> append(escape(inline.text).replace("\n", "<br>"))
                is Inline.Bold -> tag("b") { inlines(inline.children, options) }
                is Inline.Italic -> tag("i") { inlines(inline.children, options) }
                is Inline.Strike -> tag("s") { inlines(inline.children, options) }
                is Inline.Code -> tag("code") { append(escape(inline.code)) }
                is Inline.Link -> {
                    append("<a href=\"").append(escape(inline.url)).append("\">")
                    inlines(inline.children, options)
                    append("</a>")
                }
                is Inline.Image -> {
                    val src = options.imageSrc(inline.src)
                    if (src == null) {
                        append(escape(inline.alt))
                    } else {
                        append("<img src=\"").append(escape(src)).append('"')
                        if (inline.alt.isNotEmpty()) append(" alt=\"").append(escape(inline.alt)).append('"')
                        append('>')
                    }
                }
                is Inline.Math -> {
                    append(if (inline.display) "\\[" else "\\(")
                    append(escape(inline.tex))
                    append(if (inline.display) "\\]" else "\\)")
                }
                is Inline.Sound -> append(options.sound(inline.src))
                is Inline.Cloze -> when (val mode = options.cloze) {
                    ClozeMode.Keep -> {
                        append("{{c").append(inline.ordinal).append("::")
                        inlines(inline.answer, options)
                        if (inline.hint != null) append("::").append(escape(inline.hint))
                        append("}}")
                    }
                    is ClozeMode.Show -> when {
                        inline.ordinal != mode.ordinal -> inlines(inline.answer, options)
                        mode.revealed -> {
                            append("<span class=\"cloze revealed\">")
                            inlines(inline.answer, options)
                            append("</span>")
                        }
                        else -> append("<span class=\"cloze\">[").append(escape(inline.hint ?: "…")).append("]</span>")
                    }
                }
            }
        }
    }

    private inline fun StringBuilder.tag(name: String, content: StringBuilder.() -> Unit) {
        append('<').append(name).append('>')
        content()
        append("</").append(name).append('>')
    }

    /** Escapes text for HTML content and double-quoted attributes. */
    fun escape(text: String): String {
        if (text.none { it == '&' || it == '<' || it == '>' || it == '"' }) return text
        return buildString(text.length + 16) {
            for (c in text) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> append(c)
                }
            }
        }
    }
}
