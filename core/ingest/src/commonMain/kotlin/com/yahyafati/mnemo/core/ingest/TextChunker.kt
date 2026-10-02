package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount

/**
 * Splits long sources into parts a model can take in one request (PROJECT_OVERVIEW §5.3, step 1).
 * Parts break between paragraphs, else between sentences, else between words, and stay under
 * [maxWords]. A short tail is folded into the part before it rather than sent alone.
 *
 * Size is [WordCount]: Japanese and Chinese, which have no spaces, count by character, and a sentence
 * ends at 。！？ as well as at a full stop followed by a space.
 */
object TextChunker {
    /** About 1,600 tokens: small enough for any context window, big enough that a note is one request. */
    const val DEFAULT_MAX_WORDS = 1_200

    private val PARAGRAPH = Regex("""\n\s*\n""")

    // Markdown the converter writes (MarkdownText): fenced blocks and pipe tables stay whole, a heading starts a part.
    private val HEADING = Regex("""^#{1,6}\s+\S""")
    private val FENCE_OPEN = Regex("""^((?:\s*>)*\s*)(`{3,}|~{3,})[^`]*$""")
    private val TABLE_DELIMITER = Regex("""^\s*\|(\s*:?-+:?\s*\|)+\s*$""")
    private val STRUCTURE = Regex("""^\s*(?:>\s*)*(?:`{3,}|~{3,})|^\s*\|(?:\s*:?-+:?\s*\|)+\s*$""", RegexOption.MULTILINE)

    // After . ! ? and a space (the space is the break), or after 。！？ (no space) with closing quotes kept on the sentence.
    private val SENTENCE_END = Regex("""(?<=[.!?])\s+|(?<=[。！？][」』）)”’]{0,2})(?![」』）)”’])""")
    private const val CJK_TERMINATORS = "。！？"

    fun chunk(text: String, maxWords: Int = DEFAULT_MAX_WORDS): List<String> {
        require(maxWords > 0)
        val chunks = mutableListOf<String>()
        var current = mutableListOf<Piece>()
        var currentWords = 0

        fun join(pieces: List<Piece>) = pieces.withIndex().joinToString("") { (index, piece) -> if (index == 0) piece.text else piece.separator + piece.text }

        /** Where to cut [current] so that a part starts at its section's title: before the last heading, if that leaves enough. */
        fun cut(): Int {
            var before = currentWords
            for (index in current.lastIndex downTo 1) {
                before -= current[index].words
                if (current[index].heading && (index == current.lastIndex || before >= maxWords / HEADING_FRACTION)) return index
            }
            return current.size
        }

        fun add(piece: String, words: Int, separator: String, heading: Boolean = false) {
            while (currentWords + words > maxWords && current.isNotEmpty()) {
                val at = cut()
                chunks += join(current.subList(0, at))
                current = current.subList(at, current.size).toMutableList()
                currentWords = current.sumOf { it.words }
            }
            current += Piece(piece, words, heading, separator)
            currentWords += words
        }

        for (block in blocks(text)) {
            val words = WordCount.count(block.text)
            if (words <= maxWords) {
                add(block.text, words, "\n\n", block.heading)
                continue
            }
            if (block is Block.Fence) {
                for (piece in block.split(maxWords)) add(piece, WordCount.count(piece), "\n\n")
                continue
            }
            if (block is Block.Table) {
                for (piece in block.split(maxWords)) add(piece, WordCount.count(piece), "\n\n")
                continue
            }
            // Too long for one part: break it by sentences, and sentences by words (or, in Japanese, characters).
            var previous: String? = null
            for (sentence in block.text.split(SENTENCE_END).filter { it.isNotBlank() }) {
                for ((index, piece) in pieces(sentence, maxWords).withIndex()) {
                    val separator = when {
                        previous == null -> "\n\n"
                        index == 0 && previous.last() in CJK_TERMINATORS -> ""
                        else -> " "
                    }
                    add(piece, WordCount.count(piece), separator)
                    previous = piece
                }
            }
        }
        if (current.isNotEmpty()) chunks += join(current)

        val joined = chunks.toMutableList()
        if (joined.size >= 2) {
            val last = joined.last()
            val before = joined[joined.size - 2]
            val lastWords = WordCount.count(last)
            if (lastWords < maxWords / TAIL_FRACTION && WordCount.count(before) + lastWords <= maxWords + maxWords / TAIL_FRACTION) {
                joined[joined.size - 2] = before + "\n\n" + last
                joined.removeAt(joined.lastIndex)
            }
        }
        return joined
    }

    private class Piece(val text: String, val words: Int, val heading: Boolean, val separator: String)

    /** A unit that is cut only as a last resort: a paragraph, a fenced block (blank lines and all) or a pipe table. */
    private sealed class Block(val text: String) {
        val heading: Boolean get() = this is Paragraph && HEADING.containsMatchIn(text)

        class Paragraph(text: String) : Block(text)

        class Fence(val open: String, val body: List<String>, val close: String) : Block((listOf(open) + body + close).joinToString("\n")) {
            /** Parts of at most [maxWords], each a whole fence again (the opening line repeated). */
            fun split(maxWords: Int): List<String> {
                val budget = (maxWords - 2).coerceAtLeast(1)
                val out = mutableListOf<String>()
                var lines = mutableListOf<String>()
                var words = 0

                fun flush() {
                    if (lines.isEmpty()) return
                    out += (listOf(open) + lines + close).joinToString("\n")
                    lines = mutableListOf()
                    words = 0
                }
                for (line in body.flatMap { cutLine(it, budget) }) {
                    val n = WordCount.count(line)
                    if (words + n > budget) flush()
                    lines += line
                    words += n
                }
                flush()
                return out
            }
        }

        class Table(val header: List<String>, val rows: List<String>) : Block((header + rows).joinToString("\n")) {
            /** Parts of at most [maxWords], each with the header again. */
            fun split(maxWords: Int): List<String> {
                val head = header.sumOf { WordCount.count(it) }
                val budget = (maxWords - head).coerceAtLeast(1)
                val out = mutableListOf<String>()
                var lines = mutableListOf<String>()
                var words = 0

                fun flush() {
                    if (lines.isEmpty()) return
                    out += (header + lines).joinToString("\n")
                    lines = mutableListOf()
                    words = 0
                }
                for (row in rows) {
                    val n = WordCount.count(row)
                    if (words + n > budget) flush()
                    lines += row
                    words += n
                }
                flush()
                return out
            }
        }

        protected fun cutLine(line: String, maxWords: Int): List<String> =
            if (WordCount.count(line) <= maxWords) listOf(line) else pieces(line, maxWords)
    }

    private fun blocks(text: String): List<Block> {
        if (!STRUCTURE.containsMatchIn(text)) return text.split(PARAGRAPH).map { it.trim() }.filter { it.isNotEmpty() }.map { Block.Paragraph(it) }
        val lines = text.replace("\r\n", "\n").split('\n')
        val out = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()

        fun endParagraph() {
            paragraph.joinToString("\n").trim().takeIf { it.isNotEmpty() }?.let { out += Block.Paragraph(it) }
            paragraph.clear()
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val fence = FENCE_OPEN.matchEntire(line)
            when {
                line.isBlank() -> {
                    endParagraph()
                    i++
                }
                fence != null -> {
                    endParagraph()
                    val marker = fence.groupValues[2]
                    var end = i + 1
                    while (end < lines.size && !isFenceClose(lines[end], marker)) end++
                    val closed = end < lines.size
                    out += Block.Fence(line, lines.subList(i + 1, end), if (closed) lines[end] else fence.groupValues[1] + marker)
                    i = if (closed) end + 1 else end
                }
                line.trimStart().startsWith("|") && lines.getOrNull(i + 1)?.let { TABLE_DELIMITER.matches(it) } == true -> {
                    endParagraph()
                    var end = i + 2
                    while (end < lines.size && lines[end].trimStart().startsWith("|")) end++
                    out += Block.Table(lines.subList(i, i + 2), lines.subList(i + 2, end))
                    i = end
                }
                else -> {
                    paragraph += line
                    i++
                }
            }
        }
        endParagraph()
        return out
    }

    private fun isFenceClose(line: String, marker: String): Boolean {
        val trimmed = line.trim().trimStart('>', ' ')
        return trimmed.length >= marker.length && trimmed.all { it == marker[0] }
    }

    /**
     * [sentence] cut into pieces of at most [maxWords]: the sentence itself when it fits, else runs of words
     * (rejoined with single spaces) or, for text without spaces, runs of characters, never inside a word.
     */
    private fun pieces(sentence: String, maxWords: Int): List<String> {
        if (WordCount.count(sentence) <= maxWords) return listOf(sentence.trim())
        // Half-words, so a CJK character (half a word) and a word (two halves) share one budget.
        val budget = maxWords * WordCount.CJK_CHARS_PER_WORD
        val out = mutableListOf<String>()
        val piece = StringBuilder()
        var used = 0

        fun flush() {
            if (piece.isNotBlank()) out += piece.toString().trim()
            piece.setLength(0)
            used = 0
        }

        var i = 0
        while (i < sentence.length) {
            val codePoint = sentence.codePointAt(i)
            when {
                WordCount.isCjk(codePoint) -> {
                    if (used + 1 > budget) flush()
                    piece.appendCodePoint(codePoint)
                    used += 1
                    i += Character.charCount(codePoint)
                }
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> {
                    if (piece.isNotEmpty() && piece.last() != ' ') piece.append(' ')
                    i += Character.charCount(codePoint)
                }
                else -> {
                    var end = i
                    while (end < sentence.length) {
                        val c = sentence.codePointAt(end)
                        if (Character.isWhitespace(c) || Character.isSpaceChar(c) || WordCount.isCjk(c)) break
                        end += Character.charCount(c)
                    }
                    if (used + WordCount.CJK_CHARS_PER_WORD > budget) flush()
                    piece.append(sentence, i, end)
                    used += WordCount.CJK_CHARS_PER_WORD
                    i = end
                }
            }
        }
        flush()
        return out
    }

    /** A tail under a quarter of a part joins the part before, which may grow by a quarter. */
    private const val TAIL_FRACTION = 4

    /** A part is cut before a heading only if what comes before it is at least a quarter of a part. */
    private const val HEADING_FRACTION = 4
}

/** Tidies extracted text: control characters, runs of spaces, trailing blanks, extra blank lines. */
object TextCleanup {
    private val CONTROL = Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F\u00AD\uFEFF]""")
    private val SPACES = Regex("""[ \t  -   　]+""")
    private val BLANK_LINES = Regex("""\n{3,}""")

    fun normalize(text: String): String = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(CONTROL, "")
        .replace(SPACES, " ")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(BLANK_LINES, "\n\n")
        .trim()

    private val FENCE = Regex("""^((?:\s*>)*\s*)(`{3,}|~{3,})(.*)$""")

    /**
     * [normalize] for Markdown ([MarkdownText]): the same tidying, but a line keeps its leading indentation
     * (nested lists), and the lines of a fenced code block are left exactly as written, blank ones included.
     * Outside fences at most one blank line in a row.
     */
    fun normalizeMarkdown(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').replace(CONTROL, "").split('\n')
        val out = ArrayList<String>(lines.size)
        var fence: String? = null
        var blank = 0
        for (line in lines) {
            val open = fence
            if (open != null) {
                out += line
                val close = FENCE.matchEntire(line)
                if (close != null && close.groupValues[3].isBlank() && close.groupValues[2][0] == open[0] && close.groupValues[2].length >= open.length) fence = null
                continue
            }
            val tidy = tidy(line)
            if (tidy.isEmpty()) {
                if (++blank == 1) out += ""
                continue
            }
            blank = 0
            out += tidy
            val start = FENCE.matchEntire(line)
            // A backtick fence's info string has no backticks (else it is inline code at the start of a line).
            if (start != null && !(start.groupValues[2][0] == '`' && '`' in start.groupValues[3])) fence = start.groupValues[2]
        }
        while (out.isNotEmpty() && out.first().isEmpty()) out.removeAt(0)
        return out.joinToString("\n").trimEnd()
    }

    /** A line without trailing blanks or runs of spaces, but with the spaces it starts with. */
    private fun tidy(line: String): String {
        val indent = line.takeWhile { it == ' ' }
        val rest = line.substring(indent.length).replace(SPACES, " ").trim()
        return if (rest.isEmpty()) "" else indent + rest
    }

    /**
     * Joins lines a PDF wrapped mid-sentence: a line that doesn't end a sentence, followed by one
     * that starts in lower case. A hyphen at the break joins the word (`photo-` + `synthesis`).
     */
    fun joinWrappedLines(text: String): String {
        val lines = text.lines()
        val out = StringBuilder()
        for ((index, line) in lines.withIndex()) {
            out.append(line)
            val next = lines.getOrNull(index + 1) ?: break
            val wrapped = line.isNotEmpty() && next.isNotEmpty() && next[0].isLowerCase() && line.last() !in ".!?:;"
            when {
                wrapped && line.endsWith('-') && line.length > 1 && line[line.length - 2].isLetter() -> out.setLength(out.length - 1)
                wrapped -> out.append(' ')
                else -> out.append('\n')
            }
        }
        return out.toString()
    }
}
