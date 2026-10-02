package com.yahyafati.mnemo.core.ingest

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeFilter
import org.jsoup.select.NodeTraversor

/**
 * HTML to Markdown, for the model and for the person editing the box (ADR 0012), not for the card renderer:
 * so GitHub-style pipe tables are fine. Shared by every web extractor, and by books later.
 *
 * The mapping is in the ADR. In short: headings, paragraphs, `**bold**`, `*italic*`, `~~strike~~`, inline
 * code and fenced blocks, `-` / `1.` lists nested by their marker's width, `>` quotes, pipe tables
 * (a layout table is read as paragraphs), math as `\(…\)` / `\[…\]`. Link URLs and images are dropped.
 *
 * The text is written in one pass, so [of]'s `onElement` is told exactly how much text came before each
 * element (the same contract as [ReadableText]): a book finds where a table-of-contents `id` is in the text.
 * Elements inside a table are all reported at the table's offset, because a table is built cell by cell first.
 */
internal object MarkdownText {
    fun of(root: Element, onElement: (Element, Int) -> Unit = { _, _ -> }): String {
        val converter = Converter(onElement, inline = false)
        converter.run(root)
        return converter.result()
    }

    private val BLOCKS = setOf(
        "p", "div", "section", "article", "main", "blockquote", "dl", "dt", "dd", "table", "figure", "figcaption",
        "header", "footer", "nav", "aside", "address", "details", "summary", "form", "fieldset", "legend", "center",
        "body", "html", "caption", "tr", "td", "th", "thead", "tbody", "tfoot", "hgroup",
    )
    private val HEADINGS = mapOf("h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6)
    private val DROPPED = setOf(
        "img", "svg", "video", "audio", "picture", "source", "track", "canvas", "iframe", "object", "embed",
        "script", "style", "noscript", "template", "head", "link", "meta", "input", "button", "select", "textarea",
    )
    private val EMPHASIS = mapOf("strong" to "**", "b" to "**", "em" to "*", "i" to "*", "s" to "~~", "del" to "~~", "strike" to "~~")
    private const val PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
    private val DISPLAY_WRAPPER = Regex("""^\{\\(?:displaystyle|textstyle|scriptstyle)\s+(.*)\}$""", RegexOption.DOT_MATCHES_ALL)
    private val LANGUAGE_CLASS = Regex("""(?:^|\s)(?:language|lang)-([\w+#.-]+)""")

    private fun isSpace(c: Char) = c.isWhitespace() || c == ' ' || Character.isSpaceChar(c)

    /** Zero-width characters Wikipedia puts around formulas; they would only be noise in a prompt. */
    private fun isInvisible(c: Char) = c == '⁠' || c == '​' || c == '‌' || c == '﻿' || c == '­'

    private class Opener(val marker: String) {
        var open = false
    }

    private class ListContext(val ordered: Boolean, var next: Int)

    private class Converter(val onElement: (Element, Int) -> Unit, val inline: Boolean) {
        private val out = StringBuilder()

        /** One entry per enclosing quote and list item: the text that starts a continuation line. */
        private val prefixes = ArrayList<String>()
        private var markerIndex = -1
        private var marker = ""
        private var pendingBreak = 0
        private var blankPrefix: String? = null
        private var pendingSpace = false
        private var lineOpen = false
        private var atLineStart = true
        private var headingPrefix: String? = null
        private var headingDepth = 0
        private val openers = ArrayList<Opener>()
        private val lists = ArrayList<ListContext>()

        /** Elements whose subtree was written in one go, or whose end must undo something. */
        private val undo = ArrayList<Pair<Element, () -> Unit>>()

        fun result(): String = out.toString()

        fun run(root: Element) {
            NodeTraversor.filter(
                object : NodeFilter {
                    override fun head(node: Node, depth: Int): NodeFilter.FilterResult = when (node) {
                        is TextNode -> {
                            text(node.wholeText)
                            NodeFilter.FilterResult.CONTINUE
                        }
                        is Element -> element(node)
                        else -> NodeFilter.FilterResult.CONTINUE
                    }

                    override fun tail(node: Node, depth: Int): NodeFilter.FilterResult {
                        if (node is Element && undo.isNotEmpty() && undo.last().first === node) undo.removeAt(undo.lastIndex).second()
                        return NodeFilter.FilterResult.CONTINUE
                    }
                },
                root,
            )
        }

        // ---- elements ----

        private fun element(el: Element): NodeFilter.FilterResult {
            onElement(el, out.length)
            val name = el.normalName()
            return when {
                isMath(el) -> math(el)
                name in DROPPED -> skip(el)
                name == "br" -> {
                    breakLine(1)
                    NodeFilter.FilterResult.CONTINUE
                }
                name == "hr" -> {
                    breakLine(2)
                    NodeFilter.FilterResult.CONTINUE
                }
                name in HEADINGS -> heading(el, HEADINGS.getValue(name))
                name in EMPHASIS -> emphasis(el, EMPHASIS.getValue(name))
                name == "code" -> inlineCode(el)
                name == "pre" -> preformatted(el)
                name == "blockquote" -> quote(el)
                name == "ul" || name == "ol" -> list(el)
                name == "li" -> item(el)
                name == "table" -> table(el)
                name in BLOCKS -> block(el)
                else -> NodeFilter.FilterResult.CONTINUE
            }
        }

        /** An element read in full by its handler: its descendants are only reported, not walked. */
        private fun skip(el: Element): NodeFilter.FilterResult {
            reportDescendants(el, out.length)
            return NodeFilter.FilterResult.SKIP_CHILDREN
        }

        private fun reportDescendants(el: Element, offset: Int) {
            for (child in el.getAllElements()) if (child !== el) onElement(child, offset)
        }

        private fun block(el: Element): NodeFilter.FilterResult {
            breakLine(2)
            undo += el to { breakLine(2) }
            return NodeFilter.FilterResult.CONTINUE
        }

        private fun heading(el: Element, level: Int): NodeFilter.FilterResult {
            breakLine(2)
            if (!inline) headingPrefix = "#".repeat(level) + " "
            headingDepth++
            undo += el to {
                headingDepth--
                headingPrefix = null
                breakLine(2)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        private fun emphasis(el: Element, mark: String): NodeFilter.FilterResult {
            // The same mark inside itself (<b><strong>) would double it.
            if (openers.any { it.marker == mark }) return NodeFilter.FilterResult.CONTINUE
            val opener = Opener(mark)
            openers += opener
            undo += el to {
                if (opener.open) out.append(mark)
                openers.remove(opener)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        private fun inlineCode(el: Element): NodeFilter.FilterResult {
            val content = collapse(codeText(el))
            if (content.isNotEmpty()) {
                val longest = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
                val fence = "`".repeat(longest + 1)
                val pad = if (content.startsWith('`') || content.endsWith('`')) " " else ""
                raw("$fence$pad$content$pad$fence")
            }
            return skip(el)
        }

        private fun preformatted(el: Element): NodeFilter.FilterResult {
            val code = codeText(el).replace("\r\n", "\n").replace('\r', '\n').let { if (it.endsWith('\n')) it.dropLast(1) else it }
            if (code.isBlank()) return skip(el)
            if (inline) {
                raw("`" + collapse(code) + "`")
                return skip(el)
            }
            val language = language(el)
            val longest = Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(maxOf(3, longest + 1))
            breakLine(2)
            lines(listOf(fence + language) + code.split('\n') + fence)
            breakLine(2)
            return skip(el)
        }

        private fun language(pre: Element): String {
            val classes = listOfNotNull(pre.selectFirst("code")?.className(), pre.className(), pre.parent()?.className())
            for (name in classes) LANGUAGE_CLASS.find(name)?.let { return it.groupValues[1] }
            return ""
        }

        private fun quote(el: Element): NodeFilter.FilterResult {
            breakLine(2)
            if (inline) return NodeFilter.FilterResult.CONTINUE
            prefixes += "> "
            undo += el to {
                prefixes.removeAt(prefixes.lastIndex)
                breakLine(2)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        private fun list(el: Element): NodeFilter.FilterResult {
            breakLine(if (el.parent()?.normalName() == "li") 1 else 2)
            val ordered = el.normalName() == "ol"
            lists += ListContext(ordered, el.attr("start").trim().toIntOrNull() ?: 1)
            undo += el to {
                lists.removeAt(lists.lastIndex)
                breakLine(if (el.parent()?.normalName() == "li") 1 else 2)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        private fun item(el: Element): NodeFilter.FilterResult {
            breakLine(1)
            if (inline) {
                undo += el to { breakLine(1) }
                return NodeFilter.FilterResult.CONTINUE
            }
            val context = lists.lastOrNull()
            val text = if (context?.ordered == true) "${context.next++}. " else "- "
            prefixes += " ".repeat(text.length)
            markerIndex = prefixes.lastIndex
            marker = text
            val depth = prefixes.size
            undo += el to {
                // An item that never got any text leaves no marker behind.
                if (markerIndex == depth - 1) markerIndex = -1
                while (prefixes.size >= depth) prefixes.removeAt(prefixes.lastIndex)
                // A paragraph that ended the item must not put a blank line before the next one.
                pendingBreak = 0
                breakLine(1)
            }
            return NodeFilter.FilterResult.CONTINUE
        }

        // ---- math ----

        private fun isMath(el: Element): Boolean {
            val name = el.normalName()
            return name == "math" || (name == "img" && el.className().contains("mwe-math-fallback-image")) ||
                el.className().split(' ').contains("mwe-math-element")
        }

        private fun math(el: Element): NodeFilter.FilterResult {
            val name = el.normalName()
            val wrapper = name != "math" && name != "img"
            val tex = when {
                wrapper -> el.selectFirst("math[alttext]")?.attr("alttext")?.takeIf { it.isNotBlank() }
                    ?: el.selectFirst("img[alt]")?.attr("alt")
                name == "math" -> el.attr("alttext").takeIf { it.isNotBlank() } ?: el.text()
                else -> el.attr("alt")
            }.orEmpty()
            val body = tex.trim().let { DISPLAY_WRAPPER.find(it)?.groupValues?.get(1) ?: it }.trim().replace(Regex("""\s+"""), " ")
            if (body.isEmpty()) return skip(el)
            val display = el.className().contains("mwe-math-element-block") || el.attr("display") == "block" ||
                el.selectFirst("math[display=block]") != null
            if (display && !inline) {
                breakLine(2)
                raw("\\[$body\\]")
                breakLine(2)
            } else {
                raw("\\($body\\)")
            }
            return skip(el)
        }

        // ---- tables ----

        private fun table(el: Element): NodeFilter.FilterResult {
            val rows = el.select("tr").filter { it.closest("table") === el }
            val cells = rows.map { row -> row.children().filter { it.normalName() == "td" || it.normalName() == "th" } }
            // role=presentation is the page saying it is layout (Wikipedia's numbered equations: formula | | Eq.1).
            val layout = el.attr("role") == "presentation" || el.select("table").any { it !== el } || cells.all { it.size <= 1 }
            if (rows.isEmpty() || layout || inline) return block(el)

            val offset = out.length
            val width = cells.maxOf { row -> row.sumOf { maxOf(1, it.attr("colspan").trim().toIntOrNull() ?: 1).coerceAtMost(MAX_COLSPAN) } }
            val grid = cells.map { row ->
                val texts = ArrayList<String>()
                for (cell in row) {
                    val converter = Converter({ _, _ -> }, inline = true)
                    converter.run(cell)
                    texts += converter.result().replace("|", "\\|")
                    repeat((maxOf(1, cell.attr("colspan").trim().toIntOrNull() ?: 1).coerceAtMost(MAX_COLSPAN)) - 1) { texts += "" }
                }
                while (texts.size < width) texts += ""
                texts
            }.filter { row -> row.any { it.isNotEmpty() } }
            reportDescendants(el, offset)
            if (grid.isEmpty()) return NodeFilter.FilterResult.SKIP_CHILDREN
            fun row(texts: List<String>) = texts.joinToString(" | ", prefix = "| ", postfix = " |")
            breakLine(2)
            lines(listOf(row(grid[0]), row(List(width) { "---" })) + grid.drop(1).map(::row))
            breakLine(2)
            return NodeFilter.FilterResult.SKIP_CHILDREN
        }

        // ---- text ----

        /** The text of [el] as written, for code: `<br>` is a line break. */
        private fun codeText(el: Element): String {
            val sb = StringBuilder()
            NodeTraversor.traverse(
                object : org.jsoup.select.NodeVisitor {
                    override fun head(node: Node, depth: Int) {
                        if (node is TextNode) sb.append(node.wholeText) else if (node is Element && node.normalName() == "br") sb.append('\n')
                    }

                    override fun tail(node: Node, depth: Int) {}
                },
                el,
            )
            return sb.toString()
        }

        private fun collapse(s: String): String {
            val sb = StringBuilder()
            var space = false
            for (c in s) {
                when {
                    isInvisible(c) -> {}
                    isSpace(c) -> space = sb.isNotEmpty()
                    else -> {
                        if (space) sb.append(' ')
                        space = false
                        sb.append(c)
                    }
                }
            }
            return sb.toString()
        }

        private fun text(s: String) {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (isInvisible(c)) {
                    i++
                } else if (isSpace(c)) {
                    pendingSpace = true
                    i++
                } else {
                    var end = i
                    while (end < s.length && !isSpace(s[end])) end++
                    val run = s.substring(i, end).filterNot(::isInvisible)
                    if (run.isNotEmpty()) writeContent(run, escapeText = true)
                    i = end
                }
            }
        }

        /** Content that is already Markdown (code, math, a table): no escaping, but it joins the line like text. */
        private fun raw(s: String) = writeContent(s, escapeText = false)

        private fun writeContent(s: String, escapeText: Boolean) {
            beginContent()
            val lineStart = atLineStart && openers.none { !it.open }
            for (opener in openers) {
                if (!opener.open) {
                    out.append(opener.marker)
                    opener.open = true
                }
            }
            out.append(if (escapeText) escape(s, lineStart) else s)
            atLineStart = false
        }

        /** Newlines owed, the line's prefix (with a list item's marker), a space, a heading's marks. */
        private fun beginContent() {
            if (out.isEmpty()) lineOpen = false
            if (pendingBreak > 0 && out.isNotEmpty()) {
                out.append('\n')
                repeat(pendingBreak - 1) { out.append(blankPrefix.orEmpty()).append('\n') }
                lineOpen = false
            }
            pendingBreak = 0
            blankPrefix = null
            if (!lineOpen) {
                val sb = StringBuilder()
                for ((index, prefix) in prefixes.withIndex()) sb.append(if (index == markerIndex) marker else prefix)
                markerIndex = -1
                out.append(sb)
                lineOpen = true
                atLineStart = true
                pendingSpace = false
            } else if (pendingSpace) {
                out.append(' ')
            }
            pendingSpace = false
            headingPrefix?.let {
                out.append(it)
                headingPrefix = null
                atLineStart = false
            }
        }

        /** Whole lines (a fence, a table): the first one joins the open line, the rest get their own prefix. */
        private fun lines(rows: List<String>) {
            beginContent()
            val continuation = prefixes.joinToString("")
            val blank = continuation.trimEnd()
            for ((index, row) in rows.withIndex()) {
                if (index > 0) out.append('\n').append(if (row.isEmpty()) blank else continuation)
                out.append(row)
            }
            atLineStart = false
        }

        /** Ask for [lines] line breaks (1: a new line, 2: a blank line) before the next text. */
        private fun breakLine(lines: Int) {
            // Emphasis cannot run across lines: close it here and open it again on the next one.
            for (opener in openers.asReversed()) {
                if (opener.open) {
                    out.append(opener.marker)
                    opener.open = false
                }
            }
            if (inline || headingDepth > 0) {
                pendingSpace = true
                return
            }
            // A list item still waiting for its first text takes no blank line before it.
            if (markerIndex >= 0 && out.isNotEmpty() && lines > 1) return
            // The blank lines between two blocks belong to the outermost quote that holds both.
            if (lines > 1) {
                val prefix = prefixes.joinToString("").trimEnd()
                if (blankPrefix.let { it == null || prefix.length < it.length }) blankPrefix = prefix
            }
            if (lines > pendingBreak) pendingBreak = lines
        }

        // ---- escaping ----

        /** [s] has no spaces. Marks are escaped only where they would change the meaning. */
        private fun escape(s: String, lineStart: Boolean): String {
            val sb = StringBuilder()
            for ((i, c) in s.withIndex()) {
                val prev = if (i > 0) s[i - 1] else null
                val next = s.getOrNull(i + 1)
                when {
                    c == '\\' && next != null && next in PUNCTUATION -> sb.append("\\\\")
                    c == '`' -> sb.append("\\`")
                    c == '*' && !(s.length == 1 && !lineStart) -> sb.append("\\*")
                    c == '_' && s.length > 1 && !(prev?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true) -> sb.append("\\_")
                    c == '~' && (prev == '~' || next == '~') -> sb.append("\\~")
                    else -> sb.append(c)
                }
            }
            var escaped = sb.toString()
            if (lineStart) escaped = escapeLineStart(s, escaped)
            return escaped
        }

        private fun escapeLineStart(s: String, escaped: String): String = when {
            s.startsWith("#") && s.all { it == '#' } && s.length <= 6 -> "\\" + escaped
            s.startsWith(">") -> "\\" + escaped
            (s == "-" || s == "+") -> "\\" + escaped
            s.length >= 2 && s.all { it == s[0] } && s[0] in "-=" -> "\\" + escaped
            s.length >= 2 && s.length <= 10 && s.last() in ".)" && s.dropLast(1).all(Char::isDigit) ->
                escaped.dropLast(1) + "\\" + s.last()
            else -> escaped
        }

        private companion object {
            const val MAX_COLSPAN = 50
        }
    }
}
