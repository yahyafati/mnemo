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

    // After . ! ? and a space (the space is the break), or after 。！？ (no space) with closing quotes kept on the sentence.
    private val SENTENCE_END = Regex("""(?<=[.!?])\s+|(?<=[。！？][」』）)”’]{0,2})(?![」』）)”’])""")
    private const val CJK_TERMINATORS = "。！？"

    fun chunk(text: String, maxWords: Int = DEFAULT_MAX_WORDS): List<String> {
        require(maxWords > 0)
        val paragraphs = text.split(PARAGRAPH).map { it.trim() }.filter { it.isNotEmpty() }
        val chunks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var currentWords = 0

        fun add(piece: String, words: Int, separator: String) {
            if (currentWords + words > maxWords && current.isNotEmpty()) {
                chunks += current
                current = mutableListOf()
                currentWords = 0
            }
            current += if (current.isEmpty()) piece else separator + piece
            currentWords += words
        }

        for (paragraph in paragraphs) {
            val words = WordCount.count(paragraph)
            if (words <= maxWords) {
                add(paragraph, words, "\n\n")
                continue
            }
            // Too long for one part: break it by sentences, and sentences by words (or, in Japanese, characters).
            var previous: String? = null
            for (sentence in paragraph.split(SENTENCE_END).filter { it.isNotBlank() }) {
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
        if (current.isNotEmpty()) chunks += current

        val joined = chunks.map { it.joinToString("") }.toMutableList()
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
