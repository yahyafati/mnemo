package com.yahyafati.mnemo.core.ingest

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/** HTML to plain text, shared by web pages ([WebPageExtractor]) and books ([EpubReader]). */
internal object ReadableText {
    private val BLOCKS = setOf(
        "p", "div", "section", "article", "main", "blockquote", "pre", "ul", "ol", "dl", "dt", "dd", "table",
        "h1", "h2", "h3", "h4", "h5", "h6", "figure", "figcaption", "header", "hr",
    )

    /**
     * The element's text with paragraph breaks where blocks are, and list items marked.
     * [onElement] is told, for each element in document order, how much text came before it: a
     * book uses it to find where an `id` (a table-of-contents target) is in the text.
     */
    fun of(root: Element, onElement: (Element, Int) -> Unit = { _, _ -> }): String {
        val out = StringBuilder()
        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is TextNode -> {
                            val parent = node.parent() as? Element
                            if (parent != null && parent.closest("pre") != null) {
                                out.append(node.wholeText)
                            } else {
                                val text = node.text()
                                if (text.isNotBlank()) {
                                    if (out.isNotEmpty() && !out.last().isWhitespace() && text.first().isWhitespace()) out.append(' ')
                                    out.append(text.trim())
                                    if (text.last().isWhitespace()) out.append(' ')
                                }
                            }
                        }
                        is Element -> {
                            onElement(node, out.length)
                            when (node.normalName()) {
                                "br" -> out.append('\n')
                                "li" -> out.append("\n- ")
                                "tr" -> out.append('\n')
                                "td", "th" -> out.append(" | ")
                                in BLOCKS -> out.append("\n\n")
                            }
                        }
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element && node.normalName() in BLOCKS) out.append("\n\n")
                }
            },
            root,
        )
        return out.toString()
    }
}
