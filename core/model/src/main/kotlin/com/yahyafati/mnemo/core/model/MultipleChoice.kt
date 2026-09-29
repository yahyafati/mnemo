package com.yahyafati.mnemo.core.model

import kotlin.random.Random

/**
 * Multiple-choice notes: fields are the question, the correct answer, and the wrong answers, one
 * per line (a leading "- " or "A. " is dropped, so a pasted list works). The card shows every
 * answer as an option, shuffled.
 */
object MultipleChoice {
    /** Most options a card shows: the answer plus up to seven wrong ones. */
    const val MAX_WRONG = 7

    data class Options(val choices: List<String>, val correct: Int)

    private val BULLET = Regex("""^\s*(?:[-*+•]|[A-Ha-h][.)])\s+""")

    /** The wrong answers in [field], cleaned up: no blanks, no repeats, no copy of [answer]. */
    fun wrongAnswers(field: String, answer: String = ""): List<String> =
        field.lines()
            .map { it.replace(BULLET, "").trim() }
            .filter { it.isNotEmpty() && !it.equals(answer.trim(), ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(MAX_WRONG)

    /** The options for [answer] and [wrongField], shuffled by [seed]. */
    fun options(answer: String, wrongField: String, seed: Long): Options {
        val correct = answer.trim()
        val all = (listOf(correct) + wrongAnswers(wrongField, correct)).shuffled(Random(seed))
        return Options(all, all.indexOf(correct))
    }

    /** The field value for [wrong] answers. */
    fun wrongField(wrong: List<String>): String = wrong.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
}
