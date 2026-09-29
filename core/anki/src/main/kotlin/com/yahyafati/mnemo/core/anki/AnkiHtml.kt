package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.markdown.MarkdownHtml
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts Anki field HTML to card Markdown and back (ADR 0003).
 *
 * Import keeps structure and emphasis (line breaks, paragraphs, lists, bold, italic, strike,
 * code, links, headings, quotes), images and sounds as media references, and MathJax/LaTeX as
 * math. Colors, fonts, sizes and other styling are dropped. Text that would read as Markdown
 * syntax is escaped, so what shows is what Anki showed.
 */
object AnkiHtml {
    /**
     * [html] as Markdown. [mediaRef] turns a file name from the package into a media reference
     * (`media:<hash>`), or returns null if the package doesn't have that file; the name is then kept.
     */
    fun toMarkdown(html: String, mediaRef: (String) -> String? = { null }): String {
        if (html.isBlank()) return ""
        val body = Jsoup.parseBodyFragment(normalizeMath(html)).body()
        val out = MarkdownBuilder(mediaRef)
        out.children(body)
        return out.result()
    }

    /**
     * [markdown] as Anki field HTML. [mediaName] gives the package file name for a media hash
     * (null if the media is gone, in which case the image shows its alt text).
     */
    fun fromMarkdown(markdown: String, mediaName: (String) -> String?): String = MarkdownHtml.render(
        markdown,
        MarkdownHtml.Options(
            cloze = MarkdownHtml.ClozeMode.Keep,
            imageSrc = { src -> MediaRef.hashOf(src)?.let(mediaName) ?: src.takeUnless { it.startsWith(MediaRef.SCHEME) } },
            sound = { src -> "[sound:${MarkdownHtml.escape(MediaRef.hashOf(src)?.let(mediaName) ?: src)}]" },
            bareSingleParagraph = true,
        ),
    )

    /** Plain text with image file names kept, like Anki's sort field. */
    fun stripHtml(html: String): String {
        if ('<' !in html && '&' !in html) return html.trim()
        val withImages = IMG_SRC.replace(html) { " ${it.groupValues[1]} " }
        return Jsoup.parseBodyFragment(withImages).text().trim()
    }

    private val IMG_SRC = Regex("""<img[^>]*?src=["']?([^"'>\s]+)[^>]*>""", RegexOption.IGNORE_CASE)

    // --- Math -------------------------------------------------------------------------------

    private val LATEX_DISPLAY = Regex("""\[\$\$](.*?)\[/\$\$]""", RegexOption.DOT_MATCHES_ALL)
    private val LATEX_INLINE = Regex("""\[\$](.*?)\[/\$]""", RegexOption.DOT_MATCHES_ALL)
    private val LATEX_BLOCK = Regex("""\[latex](.*?)\[/latex]""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val MATHJAX = Regex("""\\\((.*?)\\\)|\\\[(.*?)\\]""", RegexOption.DOT_MATCHES_ALL)
    private val BR = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]*>""")

    /**
     * Rewrites Anki's LaTeX tags as MathJax delimiters and strips markup inside math, so each
     * math span reaches the converter as one piece of text.
     */
    private fun normalizeMath(html: String): String {
        if ('[' !in html && '\\' !in html) return html
        fun clean(tex: String) = TAG.replace(BR.replace(tex, " "), "")
        // Regex.replace with a lambda inserts the result literally: no replacement escaping needed.
        var result = LATEX_DISPLAY.replace(html) { "\\[" + clean(it.groupValues[1]) + "\\]" }
        result = LATEX_INLINE.replace(result) { "\\(" + clean(it.groupValues[1]) + "\\)" }
        result = LATEX_BLOCK.replace(result) { latexToMath(clean(it.groupValues[1])) }
        return MATHJAX.replace(result) { match ->
            val inline = match.groups[1]
            if (inline != null) "\\(" + clean(inline.value) + "\\)" else "\\[" + clean(match.groupValues[2]) + "\\]"
        }
    }

    private fun latexToMath(content: String): String {
        val tex = content.trim()
        return when {
            tex.length > 4 && tex.startsWith("$$") && tex.endsWith("$$") -> "\\[" + tex.substring(2, tex.length - 2) + "\\]"
            tex.length > 2 && tex.startsWith("$") && tex.endsWith("$") -> "\\(" + tex.substring(1, tex.length - 1) + "\\)"
            else -> "\\[$tex\\]"
        }
    }

    // --- HTML → Markdown ----------------------------------------------------------------------

    private val BLOCKS = setOf("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "blockquote", "pre", "hr", "table")
    private val LINES = setOf(
        "div", "section", "article", "header", "footer", "center", "address", "figure", "figcaption",
        "dl", "dt", "dd", "nav", "main", "aside", "tr", "li",
    )
    private val SKIPPED = setOf("script", "style", "head", "title", "rp", "template", "noscript")
    private val HTML_WHITESPACE = Regex("""[ \t\r\n\u000c]+""")
    private val INLINE_SPECIAL = Regex("""\\\(.*?\\\)|\\\[.*?\\]|\[sound:([^\]]+)]""", RegexOption.DOT_MATCHES_ALL)
    private val LINE_START_MARKER = Regex("""^(#{1,6}\s|>|[-+*]\s|([-*_])(\s*\2){2,}\s*$)""")
    private val ORDERED_MARKER = Regex("""^(\d{1,9})([.)])(\s)""")
    private val STYLE_BOLD = Regex("font-weight:(bold|[6-9]00)")
    private val WOULD_LINK = Regex("""^\[[^\]]*]\(""")

    private class MarkdownBuilder(private val mediaRef: (String) -> String?) {
        private val out = StringBuilder()
        private var pendingNewlines = 0

        private val atLineStart: Boolean get() = out.isEmpty() || out.last() == '\n'

        fun result(): String = out.toString()
            .lines().joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim('\n')

        /** Requests a line break before the next text (explicit `<br>`s add up). */
        fun lineBreak() {
            pendingNewlines = (pendingNewlines + 1).coerceAtMost(2)
        }

        fun softLine() {
            pendingNewlines = maxOf(pendingNewlines, 1)
        }

        fun block() {
            pendingNewlines = 2
        }

        /** Writes Markdown as is. At the start of a line, leading spaces are dropped. */
        fun raw(markdown: String) {
            if (markdown.isEmpty()) return
            flushBreaks(hasContent = markdown.isNotBlank())
            if (atLineStart) {
                val trimmed = markdown.trimStart()
                if (trimmed.isEmpty()) return
                out.append(trimmed)
            } else {
                out.append(markdown)
            }
        }

        /** Writes plain text, escaping anything that would read as Markdown. */
        fun text(text: String) {
            if (text.isEmpty()) return
            flushBreaks(hasContent = text.isNotBlank())
            var value = if (atLineStart) text.trimStart() else text
            if (value.isEmpty()) return
            value = escapeInline(value)
            if (atLineStart) value = escapeLineStart(value)
            out.append(value)
        }

        private fun flushBreaks(hasContent: Boolean) {
            if (pendingNewlines == 0 || !hasContent) return
            if (out.isNotEmpty()) {
                while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
                val existing = out.takeLastWhile { it == '\n' }.length
                repeat((pendingNewlines - existing).coerceAtLeast(0)) { out.append('\n') }
            }
            pendingNewlines = 0
        }

        fun children(element: Element) = element.childNodes().forEach { node(it) }

        private fun node(node: Node) {
            when (node) {
                is TextNode -> textNode(node.wholeText)
                is Element -> element(node)
            }
        }

        private fun textNode(whole: String) {
            val collapsed = HTML_WHITESPACE.replace(whole, " ").replace('\u00a0', ' ')
            var last = 0
            for (match in INLINE_SPECIAL.findAll(collapsed)) {
                text(collapsed.substring(last, match.range.first))
                val sound = match.groups[1]
                if (sound != null) {
                    val name = sound.value.trim()
                    raw("[sound:${mediaRef(name) ?: name}]")
                } else {
                    raw(match.value)
                }
                last = match.range.last + 1
            }
            text(collapsed.substring(last))
        }

        private fun element(el: Element) {
            val tag = el.normalName()
            when {
                tag in SKIPPED -> Unit
                tag == "br" -> lineBreak()
                tag == "img" -> image(el)
                tag == "hr" -> {
                    block()
                    raw("---")
                    block()
                }
                tag == "pre" -> {
                    block()
                    val code = el.wholeText().trimEnd('\n')
                    raw("```\n$code\n```")
                    block()
                }
                tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' -> {
                    block()
                    raw("#".repeat(tag[1] - '0') + " ")
                    inlineChildren(el)
                    block()
                }
                tag == "blockquote" -> {
                    block()
                    val inner = MarkdownBuilder(mediaRef).apply { children(el) }.result()
                    if (inner.isNotBlank()) raw(inner.lines().joinToString("\n") { "> $it" })
                    block()
                }
                tag == "ul" || tag == "ol" -> list(el, ordered = tag == "ol")
                tag == "td" || tag == "th" -> {
                    if (el.elementSiblingIndex() > 0) raw(" | ")
                    children(el)
                }
                tag == "rt" -> {
                    raw("(")
                    children(el)
                    raw(")")
                }
                tag == "code" && !el.hasBlockDescendant() -> code(el)
                tag == "a" -> link(el)
                else -> {
                    val marker = emphasisMarker(el)
                    when {
                        marker != null -> emphasis(el, marker)
                        tag in BLOCKS -> {
                            block()
                            children(el)
                            block()
                        }
                        tag in LINES -> {
                            softLine()
                            children(el)
                            softLine()
                        }
                        else -> children(el)
                    }
                }
            }
        }

        private fun list(el: Element, ordered: Boolean) {
            block()
            var number = el.attr("start").toIntOrNull() ?: 1
            for (child in el.children()) {
                if (child.normalName() != "li") {
                    element(child)
                    continue
                }
                softLine()
                raw(if (ordered) "${number++}. " else "- ")
                inlineChildren(child)
                softLine()
            }
            block()
        }

        /** Children of a block that Markdown puts on one line: nested blocks become spaces. */
        private fun inlineChildren(el: Element) {
            val inner = MarkdownBuilder(mediaRef).apply { children(el) }.result()
            raw(inner.replace(Regex("\\s*\n\\s*"), " "))
        }

        private fun emphasis(el: Element, marker: String) {
            if (el.hasBlockDescendant()) {
                children(el)
                return
            }
            val inner = MarkdownBuilder(mediaRef).apply { children(el) }.result()
            if (inner.isBlank()) {
                if (el.wholeText().isNotEmpty()) text(" ")
                return
            }
            // Markers must touch the text: "** bold**" is not bold.
            val whole = el.wholeText()
            if (whole.firstOrNull()?.isWhitespace() == true) text(" ")
            raw(marker + inner.trim() + marker)
            if (whole.lastOrNull()?.isWhitespace() == true) text(" ")
        }

        private fun code(el: Element) {
            val code = HTML_WHITESPACE.replace(el.wholeText(), " ").replace('\u00a0', ' ')
            if (code.isBlank()) return
            if ('`' in code) text(code) else raw("`$code`")
        }

        private fun link(el: Element) {
            val href = el.attr("href").trim()
            val inner = MarkdownBuilder(mediaRef).apply { children(el) }.result()
            val plain = href.isEmpty() || href.any { it.isWhitespace() || it == ')' } || inner.isBlank() ||
                '\n' in inner || ']' in inner
            raw(if (plain) inner else "[$inner]($href)")
        }

        private fun image(el: Element) {
            val src = el.attr("src").trim()
            if (src.isEmpty()) return
            val alt = el.attr("alt").replace(Regex("[\\[\\]\\n]"), " ").trim()
            val ref = mediaRef(src) ?: src.takeIf { it.none { c -> c.isWhitespace() || c == ')' } } ?: return
            raw("![$alt]($ref)")
        }

        private fun emphasisMarker(el: Element): String? {
            val style = el.attr("style").lowercase().replace(" ", "")
            return when (el.normalName()) {
                "b", "strong" -> "**"
                "i", "em", "cite", "var" -> "*"
                "s", "strike", "del" -> "~~"
                else -> when {
                    STYLE_BOLD.containsMatchIn(style) -> "**"
                    "font-style:italic" in style -> "*"
                    "text-decoration:line-through" in style -> "~~"
                    else -> null
                }
            }
        }

        private fun Element.hasBlockDescendant(): Boolean =
            getAllElements().drop(1).any { val n = it.normalName(); n in BLOCKS || n in LINES || n == "br" }
    }

    /** A line that would start a Markdown block (heading, list, quote, rule) is made plain text. */
    private fun escapeLineStart(line: String): String {
        ORDERED_MARKER.find(line)?.let { m -> return m.groupValues[1] + "\\" + line.substring(m.groupValues[1].length) }
        return if (LINE_START_MARKER.containsMatchIn(line)) "\\" + line else line
    }

    /**
     * Escapes characters that would change meaning in Markdown, and only those, so imported text
     * stays readable in the editor ("2 * 3" and snake_case need no backslashes).
     */
    internal fun escapeInline(text: String): String {
        val out = StringBuilder(text.length + 8)
        for (i in text.indices) {
            val c = text[i]
            val prev = text.getOrNull(i - 1)
            val next = text.getOrNull(i + 1)
            val escape = when (c) {
                '\\' -> next != null && (next in com.yahyafati.mnemo.core.model.markdown.Markdown.ESCAPABLE)
                '`' -> true
                '*' -> (prev != null && !prev.isWhitespace()) || (next != null && !next.isWhitespace())
                '_' -> {
                    val opens = (prev == null || !prev.isLetterOrDigit()) && next != null && !next.isWhitespace()
                    val closes = (next == null || !next.isLetterOrDigit()) && prev != null && !prev.isWhitespace()
                    opens || closes
                }
                '~' -> next == '~' || prev == '~'
                '$' -> next == '$' || prev == '$'
                '[' -> WOULD_LINK.containsMatchIn(text.substring(i)) || text.startsWith("[sound:", i)
                '!' -> next == '[' && WOULD_LINK.containsMatchIn(text.substring(i + 1))
                else -> false
            }
            if (escape) out.append('\\')
            out.append(c)
        }
        return out.toString()
    }
}
