package com.yahyafati.mnemo.core.model

import com.yahyafati.mnemo.core.model.markdown.Markdown
import java.text.Normalizer

/**
 * Checks a type-in answer. The expected answer is the back's text without Markdown. An answer is
 * correct when it matches ignoring case, accents' composition, repeated spaces and punctuation
 * at the ends; otherwise [diff] shows where it went wrong, as Anki does.
 */
object TypedAnswer {
    /** Longest answers diffed character by character; longer ones are compared whole. */
    private const val MAX_DIFF_CHARS = 400

    enum class Kind {
        /** Typed and expected agree here. */
        Same,

        /** Typed, but not in the expected answer. */
        Extra,

        /** In the expected answer, but not typed. */
        Missing,
    }

    data class Segment(val kind: Kind, val text: String)

    data class Result(val correct: Boolean, val expected: String, val diff: List<Segment>)

    fun expectedText(back: String): String = Markdown.plainText(back)

    fun check(typed: String, back: String): Result {
        val expected = expectedText(back)
        val correct = normalize(typed) == normalize(expected)
        return Result(correct, expected, if (correct) listOf(Segment(Kind.Same, typed.trim())) else diff(typed.trim(), expected))
    }

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
        .lowercase()
        .replace(WHITESPACE, " ")
        .trim()
        .trim { !it.isLetterOrDigit() }

    /** A character diff of [typed] against [expected] (longest common subsequence, case-insensitive). */
    fun diff(typed: String, expected: String): List<Segment> {
        if (typed.length > MAX_DIFF_CHARS || expected.length > MAX_DIFF_CHARS) {
            return listOfNotNull(
                typed.takeIf { it.isNotEmpty() }?.let { Segment(Kind.Extra, it) },
                expected.takeIf { it.isNotEmpty() }?.let { Segment(Kind.Missing, it) },
            )
        }
        val a = typed.lowercase()
        val b = expected.lowercase()
        // lengths[i][j]: LCS of a[i..] and b[j..].
        val lengths = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lengths[i][j] = if (a[i] == b[j]) lengths[i + 1][j + 1] + 1 else maxOf(lengths[i + 1][j], lengths[i][j + 1])
            }
        }
        val out = mutableListOf<Segment>()
        fun add(kind: Kind, c: Char) {
            val last = out.lastOrNull()
            if (last?.kind == kind) out[out.lastIndex] = last.copy(text = last.text + c) else out += Segment(kind, c.toString())
        }
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            when {
                a[i] == b[j] -> {
                    add(Kind.Same, expected[j])
                    i++
                    j++
                }
                lengths[i + 1][j] >= lengths[i][j + 1] -> add(Kind.Extra, typed[i++])
                else -> add(Kind.Missing, expected[j++])
            }
        }
        while (i < a.length) add(Kind.Extra, typed[i++])
        while (j < b.length) add(Kind.Missing, expected[j++])
        return out
    }

    private val WHITESPACE = Regex("""\s+""")
}
