package com.yahyafati.mnemo.core.model.markdown

/**
 * The Markdown subset cards use (ADR 0002): headings, paragraphs, fenced code, quotes, lists and
 * rules; inline bold, italic, strikethrough, code, links, images, math, Anki sound tags and cloze
 * deletions.
 *
 * Unlike CommonMark, a single newline inside a paragraph is kept as a line break: card text is
 * short and people press Enter meaning it.
 *
 * Math uses the MathJax delimiters Anki uses, so imported cards keep working: `\(inline\)`,
 * `\[display\]`, and also `$$display$$`. A single `$` is literal text (prices are common on cards).
 *
 * Pure Kotlin, so it is unit-tested on the JVM, shared by the card renderer (`:core:ui`) and the
 * Anki exporter (`:core:anki`), and parsed once per card, off the hot path.
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

        /** `![alt](src)`. [src] is usually a [com.yahyafati.mnemo.core.model.MediaRef]. */
        data class Image(val alt: String, val src: String) : Inline

        /** `\(tex\)` inline, or `\[tex\]` / `$$tex$$` when [display]. */
        data class Math(val tex: String, val display: Boolean) : Inline

        /** Anki's `[sound:src]`. Kept so audio survives import and export; playback comes later. */
        data class Sound(val src: String) : Inline

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
    private val IMAGE = Regex("""!\[([^\]]*)\]\(([^)\s]*)\)""")
    private val SOUND = Regex("""\[sound:([^\]]+)\]""")

    /** Characters a backslash makes literal. */
    const val ESCAPABLE = "\\`*_{}[]()#+-.!~>$"

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
            // Math first: `\(` would otherwise read as an escaped parenthesis.
            val math = mathAt(text, i)
            if (math != null) {
                emit(math.first)
                i = math.second
                continue
            }
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
            if (c == '!' && text.startsWith("![", i)) {
                val match = IMAGE.matchAt(text, i)
                if (match != null) {
                    emit(Inline.Image(match.groupValues[1], match.groupValues[2]))
                    i = match.range.last + 1
                    continue
                }
            }
            if (c == '[') {
                val sound = if (text.startsWith("[sound:", i)) SOUND.matchAt(text, i) else null
                if (sound != null) {
                    emit(Inline.Sound(sound.groupValues[1]))
                    i = sound.range.last + 1
                    continue
                }
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

    /** A math span opening at [start], and the index just past it. Unclosed delimiters are text. */
    private fun mathAt(text: String, start: Int): Pair<Inline.Math, Int>? {
        val (close, display) = when {
            text.startsWith("\\(", start) -> "\\)" to false
            text.startsWith("\\[", start) -> "\\]" to true
            text.startsWith("$$", start) -> "$$" to true
            else -> return null
        }
        val end = text.indexOf(close, start + 2)
        if (end < 0 || end == start + 2) return null
        return Inline.Math(text.substring(start + 2, end), display) to end + close.length
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

    /** Every inline in [blocks], depth first, including those nested in emphasis, links and clozes. */
    fun inlines(blocks: List<Block>): Sequence<Inline> = sequence {
        suspend fun SequenceScope<Inline>.visit(inlines: List<Inline>) {
            for (inline in inlines) {
                yield(inline)
                when (inline) {
                    is Inline.Bold -> visit(inline.children)
                    is Inline.Italic -> visit(inline.children)
                    is Inline.Strike -> visit(inline.children)
                    is Inline.Link -> visit(inline.children)
                    is Inline.Cloze -> visit(inline.answer)
                    else -> Unit
                }
            }
        }
        suspend fun SequenceScope<Inline>.visitBlocks(blocks: List<Block>) {
            for (block in blocks) {
                when (block) {
                    is Block.Heading -> visit(block.content)
                    is Block.Paragraph -> visit(block.content)
                    is Block.Quote -> visitBlocks(block.blocks)
                    is Block.ListBlock -> block.items.forEach { visit(it) }
                    is Block.CodeBlock, Block.Rule -> Unit
                }
            }
        }
        visitBlocks(blocks)
    }

    /** Whether [markdown] has math, which needs the KaTeX renderer instead of native text. */
    fun containsMath(markdown: String): Boolean =
        MATH_HINT.containsMatchIn(markdown) && inlines(parse(markdown)).any { it is Inline.Math }

    private val MATH_HINT = Regex("""\\\(|\\\[|\$\$""")

    /**
     * [markdown] as plain text on one line, for lists and previews: markup removed, clozes
     * revealed, images and sounds dropped, math kept as its source.
     */
    fun plainText(markdown: String): String {
        val out = StringBuilder()
        fun appendInlines(inlines: List<Inline>) {
            for (inline in inlines) {
                when (inline) {
                    is Inline.Text -> out.append(inline.text)
                    is Inline.Bold -> appendInlines(inline.children)
                    is Inline.Italic -> appendInlines(inline.children)
                    is Inline.Strike -> appendInlines(inline.children)
                    is Inline.Link -> appendInlines(inline.children)
                    is Inline.Cloze -> appendInlines(inline.answer)
                    is Inline.Code -> out.append(inline.code)
                    is Inline.Math -> out.append(inline.tex)
                    is Inline.Image -> if (inline.alt.isNotBlank()) out.append(inline.alt)
                    is Inline.Sound -> Unit
                }
            }
        }
        fun appendBlocks(blocks: List<Block>) {
            for (block in blocks) {
                if (out.isNotEmpty()) out.append(' ')
                when (block) {
                    is Block.Heading -> appendInlines(block.content)
                    is Block.Paragraph -> appendInlines(block.content)
                    is Block.CodeBlock -> out.append(block.code)
                    is Block.Quote -> appendBlocks(block.blocks)
                    is Block.ListBlock -> block.items.forEachIndexed { index, item ->
                        if (index > 0) out.append(' ')
                        appendInlines(item)
                    }
                    Block.Rule -> Unit
                }
            }
        }
        appendBlocks(parse(markdown))
        return out.toString().replace(WHITESPACE, " ").trim()
    }

    private val WHITESPACE = Regex("""\s+""")
}
