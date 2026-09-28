package com.yahyafati.mnemo.core.model

/**
 * Anki cloze syntax: `{{c1::answer}}` or `{{c1::answer::hint}}`. A cloze note gets one card per
 * distinct number; on card N, deletion N is hidden and the others are shown as plain text.
 */
object Cloze {
    private val DELETION = Regex("""\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}""", RegexOption.DOT_MATCHES_ALL)

    sealed interface Segment {
        data class Text(val text: String) : Segment

        data class Deletion(val ordinal: Int, val answer: String, val hint: String?) : Segment
    }

    fun parse(text: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        var last = 0
        for (match in DELETION.findAll(text)) {
            if (match.range.first > last) segments += Segment.Text(text.substring(last, match.range.first))
            val (number, answer, hint) = match.destructured
            segments += Segment.Deletion(number.toInt(), answer, hint.ifEmpty { null })
            last = match.range.last + 1
        }
        if (last < text.length) segments += Segment.Text(text.substring(last))
        return segments
    }

    /** The cloze numbers used in [text], ascending. Numbers below 1 are ignored, as in Anki. */
    fun ordinals(text: String): List<Int> =
        DELETION.findAll(text).map { it.groupValues[1].toInt() }.filter { it >= 1 }.distinct().sorted().toList()

    /** The number a new deletion should get: one past the highest used. */
    fun nextOrdinal(text: String): Int = (ordinals(text).maxOrNull() ?: 0) + 1

    /** Wraps [answer] as a deletion with [ordinal]. */
    fun wrap(answer: String, ordinal: Int): String = "{{c$ordinal::$answer}}"

    /** [text] with every deletion replaced by its answer: what the note says with nothing hidden. */
    fun reveal(text: String): String = DELETION.replace(text) { it.groupValues[2] }
}
