package com.yahyafati.mnemo.core.ingest

/**
 * Splits long sources into parts a model can take in one request (PROJECT_OVERVIEW §5.3, step 1).
 * Parts break between paragraphs, else between sentences, else between words, and stay under
 * [maxWords]. A short tail is folded into the part before it rather than sent alone.
 */
object TextChunker {
    /** About 1,600 tokens: small enough for any context window, big enough that a note is one request. */
    const val DEFAULT_MAX_WORDS = 1_200

    private val PARAGRAPH = Regex("""\n\s*\n""")
    private val SENTENCE_END = Regex("""(?<=[.!?。！？])\s+""")
    private val WHITESPACE = Regex("""\s+""")

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
            val words = words(paragraph)
            if (words <= maxWords) {
                add(paragraph, words, "\n\n")
                continue
            }
            // Too long for one part: break it by sentences, and sentences by words.
            var first = true
            for (sentence in paragraph.split(SENTENCE_END)) {
                val sentenceWords = sentence.split(WHITESPACE).filter { it.isNotEmpty() }
                for (piece in sentenceWords.chunked(maxWords)) {
                    add(piece.joinToString(" "), piece.size, if (first) "\n\n" else " ")
                    first = false
                }
            }
        }
        if (current.isNotEmpty()) chunks += current

        val joined = chunks.map { it.joinToString("") }.toMutableList()
        if (joined.size >= 2) {
            val last = joined.last()
            val before = joined[joined.size - 2]
            if (words(last) < maxWords / TAIL_FRACTION && words(before) + words(last) <= maxWords + maxWords / TAIL_FRACTION) {
                joined[joined.size - 2] = before + "\n\n" + last
                joined.removeAt(joined.lastIndex)
            }
        }
        return joined
    }

    private fun words(text: String) = text.split(WHITESPACE).count { it.isNotEmpty() }

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
