package com.yahyafati.mnemo.core.ui.card.markdown

/**
 * The Markdown subset cards use (ADR 0002): headings, paragraphs, fenced code, quotes, lists and
 * rules; inline bold, italic, strikethrough, code, links, and Anki cloze deletions.
 *
 * Unlike CommonMark, a single newline inside a paragraph is kept as a line break: card text is
 * short and people press Enter meaning it.
 *
 * Pure Kotlin, so it is unit-tested on the JVM and parsed once per card, off the hot path.
 */
object Markdown {
    sealed interface Block {
        data class Heading(val level: Int, val content: List<Inline>) : Block

        data class Paragraph(val content: List<Inline>) : Block

        data class CodeBlock(val language: String?, val code: String) : Block

        data class Quote(val blocks: List<Block>) : Block

        data class ListBlock(val ordered: Boolean, val start: Int, val items: List<List<Inline>>) : Block

        data object Rule : Block
    }

    sealed interface Inline {
        data class Text(val text: String) : Inline

        data class Bold(val children: List<Inline>) : Inline

        data class Italic(val children: List<Inline>) : Inline

        data class Strike(val children: List<Inline>) : Inline

        data class Code(val code: String) : Inline

        data class Link(val children: List<Inline>, val url: String) : Inline

        data class Cloze(val ordinal: Int, val answer: List<Inline>, val hint: String?) : Inline
    }

    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val FENCE = Regex("""^\s*(```|~~~)\s*([\w+-]*)\s*$""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^\s*(\d{1,9})[.)]\s+(.*)$""")
    private val QUOTE = Regex("""^\s*>\s?(.*)$""")
    private val CLOZE = Regex("""\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val LINK = Regex("""\[([^\]]*)\]\(([^)\s]*)\)""")
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~>"

    fun parse(markdown: String): List<Block> = parseBlocks(markdown.replace("\r\n", "\n").lines())

    private fun parseBlocks(lines: List<String>): List<Block> {
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++

                FENCE.matches(line) -> {
                    val (fence, language) = FENCE.find(line)!!.destructured
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim() != fence) code += lines[i++]
                    i++ // closing fence (or end of text)
                    blocks += Block.CodeBlock(language.ifEmpty { null }, code.joinToString("\n"))
                }

                HEADING.matches(line) -> {
                    val (hashes, text) = HEADING.find(line)!!.destructured
                    blocks += Block.Heading(hashes.length, parseInline(text))
                    i++
                }

                RULE.matches(line) -> {
                    blocks += Block.Rule
                    i++
                }

                QUOTE.matches(line) -> {
                    val quoted = mutableListOf<String>()
                    while (i < lines.size && QUOTE.matches(lines[i])) quoted += QUOTE.find(lines[i++])!!.groupValues[1]
                    blocks += Block.Quote(parseBlocks(quoted))
                }

                BULLET.matches(line) || ORDERED.matches(line) -> {
                    val ordered = !BULLET.matches(line)
                    val pattern = if (ordered) ORDERED else BULLET
                    val start = if (ordered) ORDERED.find(line)!!.groupValues[1].toInt() else 1
                    val items = mutableListOf<List<Inline>>()
                    while (i < lines.size && pattern.matches(lines[i])) {
                        items += parseInline(pattern.find(lines[i++])!!.groupValues.last())
                    }
                    blocks += Block.ListBlock(ordered, start, items)
                }

                else -> {
                    val paragraph = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i])) paragraph += lines[i++]
                    blocks += Block.Paragraph(parseInline(paragraph.joinToString("\n")))
                }
            }
        }
        return blocks
    }

    private fun startsBlock(line: String) = FENCE.matches(line) || HEADING.matches(line) || RULE.matches(line) ||
        QUOTE.matches(line) || BULLET.matches(line) || ORDERED.matches(line)

    fun parseInline(text: String): List<Inline> {
        val out = mutableListOf<Inline>()
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) {
                out += Inline.Text(buffer.toString())
                buffer.clear()
            }
        }
        fun emit(inline: Inline) {
            flush()
            out += inline
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE) {
                buffer.append(text[i + 1])
                i += 2
                continue
            }
            if (text.startsWith("{{c", i)) {
                val match = CLOZE.matchAt(text, i)
                if (match != null) {
                    val (ordinal, answer, hint) = match.destructured
                    emit(Inline.Cloze(ordinal.toInt(), parseInline(answer), hint.ifEmpty { null }))
                    i = match.range.last + 1
                    continue
                }
            }
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    emit(Inline.Code(text.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }
            if (c == '[') {
                val match = LINK.matchAt(text, i)
                if (match != null) {
                    emit(Inline.Link(parseInline(match.groupValues[1]), match.groupValues[2]))
                    i = match.range.last + 1
                    continue
                }
            }
            val delimited = delimiterSpan(text, i)
            if (delimited != null) {
                val (delimiter, end) = delimited
                val inner = parseInline(text.substring(i + delimiter.length, end))
                emit(
                    when (delimiter) {
                        "**", "__" -> Inline.Bold(inner)
                        "~~" -> Inline.Strike(inner)
                        else -> Inline.Italic(inner)
                    },
                )
                i = end + delimiter.length
                continue
            }
            buffer.append(c)
            i++
        }
        flush()
        return out
    }

    /** If an emphasis span opens at [start], its delimiter and the index of its closing delimiter. */
    private fun delimiterSpan(text: String, start: Int): Pair<String, Int>? {
        val delimiter = listOf("**", "__", "~~", "*", "_").firstOrNull { text.startsWith(it, start) } ?: return null
        val contentStart = start + delimiter.length
        // Opening delimiters must touch their content: "2 * 3 * 4" is not italic.
        if (contentStart >= text.length || text[contentStart].isWhitespace()) return null
        // Underscores inside words (snake_case) are literal.
        if (delimiter[0] == '_' && start > 0 && text[start - 1].isLetterOrDigit()) return null

        var search = contentStart
        while (true) {
            var end = text.indexOf(delimiter, search)
            if (end < 0) return null
            // In a run like "***", a double delimiter closes on the last two, so "**a *b***" nests.
            if (delimiter.length == 2) {
                while (end + 2 < text.length && text[end + 2] == delimiter[0]) end++
            }
            val closesCleanly = !text[end - 1].isWhitespace() &&
                // A single "*" must not be half of a "**".
                !(delimiter.length == 1 && text.startsWith(delimiter + delimiter, end)) &&
                !(delimiter[0] == '_' && end + delimiter.length < text.length && text[end + delimiter.length].isLetterOrDigit())
            if (end > contentStart && closesCleanly) return delimiter to end
            search = end + if (delimiter.length == 1 && text.startsWith(delimiter + delimiter, end)) 2 else 1
        }
    }
}
