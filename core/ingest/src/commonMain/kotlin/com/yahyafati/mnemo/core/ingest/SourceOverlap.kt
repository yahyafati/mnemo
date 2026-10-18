package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount

/**
 * The end of a part, sent with the part after it as context only (ADR 0015, C3): its last paragraphs, as many as fit
 * in [OVERLAP_WORDS]. When the last paragraph alone is longer than that it is cut at a sentence, and a single sentence
 * longer than that at a word (or, in Japanese and Chinese, a character), with "…" in front. Sizes are [WordCount].
 */
object SourceOverlap {
    /** The most words of overlap. */
    const val OVERLAP_WORDS = 100

    private val PARAGRAPH_BREAK = Regex("""\n\s*\n""")
    private const val ELLIPSIS = "…"

    /** The overlap [text] gives the part after it; empty for blank text. Never longer than [maxWords]. */
    fun tail(text: String, maxWords: Int = OVERLAP_WORDS): String {
        require(maxWords > 0)
        val paragraphs = text.trim().split(PARAGRAPH_BREAK).map { it.trim() }.filter { it.isNotEmpty() }
        val last = paragraphs.lastOrNull() ?: return ""
        if (WordCount.count(last) > maxWords) return sentenceTail(last, maxWords)
        var start = paragraphs.size - 1
        var words = WordCount.count(last)
        while (start > 0) {
            val next = WordCount.count(paragraphs[start - 1])
            if (words + next > maxWords) break
            words += next
            start--
        }
        return paragraphs.subList(start, paragraphs.size).joinToString("\n\n")
    }

    /** The last sentences of [paragraph] that fit in [maxWords]. */
    private fun sentenceTail(paragraph: String, maxWords: Int): String {
        val sentences = paragraph.split(TextChunker.SENTENCE_END).filter { it.isNotBlank() }
        var start = sentences.size
        var words = 0
        while (start > 0) {
            val next = WordCount.count(sentences[start - 1])
            if (words + next > maxWords) break
            words += next
            start--
        }
        if (start == sentences.size) return ELLIPSIS + wordTail(sentences.last().trim(), maxWords)
        // The sentences were split on the break after them, so what joins them again is a space (none in Japanese).
        return sentences.subList(start, sentences.size).fold("") { joined, sentence ->
            if (joined.isEmpty()) sentence.trim() else joined + separator(joined) + sentence.trim()
        }
    }

    private fun separator(before: String) = if (before.last() in "。！？」』）)”’") "" else " "

    /** The end of one very long sentence: the longest suffix of at most [maxWords], starting at a word. */
    private fun wordTail(sentence: String, maxWords: Int): String {
        var low = 0
        var high = sentence.length
        // The smallest start whose suffix fits; the count only falls as the start moves right.
        while (low < high) {
            val middle = (low + high) / 2
            if (WordCount.count(sentence.substring(middle)) <= maxWords) high = middle else low = middle + 1
        }
        var start = low
        // Start on a word, not inside one (Japanese and Chinese have no spaces and can start anywhere).
        if (start > 0 && start < sentence.length && !sentence[start - 1].isWhitespace() && !WordCount.isCjk(sentence.codePointAt(start))) {
            while (start < sentence.length && !sentence[start].isWhitespace()) start++
        }
        return sentence.substring(start).trim()
    }
}
