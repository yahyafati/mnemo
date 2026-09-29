package com.yahyafati.mnemo.core.ai.parse

import com.yahyafati.mnemo.core.model.NoteKind

/**
 * The last resort for a reply with no JSON at all: small models sometimes answer with
 * "Q: … / A: …" pairs, or one cloze sentence per line. Labels may be bold, numbered or bulleted,
 * but need a colon, so the options of a multiple-choice question ("A. Oxygen") stay in the question.
 */
internal object PlainTextCards {
    private val LIST_MARK = Regex("""^\s*(?:[-*•]\s+|\d+[.)]\s+)?""")
    private val QUESTION = Regex("""^(?:\*\*|__)?(?:q|question|front)(?:\s*\d+)?\s*:\s*(?:\*\*|__)?\s*""", RegexOption.IGNORE_CASE)
    private val ANSWER = Regex("""^(?:\*\*|__)?(?:a|answer|back)(?:\s*\d+)?\s*:\s*(?:\*\*|__)?\s*""", RegexOption.IGNORE_CASE)
    private val CLOZE = Regex("""\{\{\s*[cC]\s*\d+\s*::""")

    fun parse(text: String): List<ParsedCard> {
        val cards = mutableListOf<ParsedCard>()
        var question: StringBuilder? = null
        var answer: StringBuilder? = null

        fun flush() {
            val q = question?.toString()?.trim().orEmpty()
            val a = answer?.toString()?.trim().orEmpty()
            if (q.isNotEmpty() && a.isNotEmpty()) cards += ParsedCard(NoteKind.Basic, q, a)
            question = null
            answer = null
        }

        for (rawLine in text.lines()) {
            val line = rawLine.replace(LIST_MARK, "").trim()
            when {
                QUESTION.containsMatchIn(line) -> {
                    flush()
                    question = StringBuilder(line.replace(QUESTION, ""))
                }
                ANSWER.containsMatchIn(line) && question != null -> answer = StringBuilder(line.replace(ANSWER, ""))
                CLOZE.containsMatchIn(line) && question == null -> {
                    cards += ParsedCard(NoteKind.Cloze, CardFields.normalizeCloze(line), "")
                }
                line.isEmpty() -> if (answer != null) flush()
                answer != null -> answer!!.append('\n').append(line)
                question != null -> question!!.append('\n').append(line)
            }
        }
        flush()
        return cards
    }
}
