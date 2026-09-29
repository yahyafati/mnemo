package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Cloze
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.MultipleChoice
import com.yahyafati.mnemo.core.model.NoteKind

/** Why a generated (or edited) card can't be saved as it is. */
enum class GeneratedCardProblem {
    EmptyFront,

    /** A question with no answer. */
    EmptyBack,

    /** A cloze card with no complete `{{c1::…}}` deletion. */
    NoCloze,

    /** A deletion that was opened and never closed. */
    BrokenCloze,

    /** A multiple-choice card with nothing to choose between. */
    NoWrongAnswers,

    TooLong,
}

/**
 * Checks proposed cards before they reach the review queue, and again before they're saved
 * (ARCHITECTURE §5.2, step 5): required fields, cloze syntax, sane lengths. Also defines when two
 * cards are the same, for deduplication.
 */
object GeneratedCardValidator {
    const val MAX_FRONT_CHARS = 2_000
    const val MAX_BACK_CHARS = 4_000

    private val CLOZE_OPENING = Regex("""\{\{c\d+::""")
    private val MARKUP = Regex("""[*_`#>~\[\]()\\|{}:;,.!?¿¡"'“”‘’«»\-–—]+""")
    private val WHITESPACE = Regex("""\s+""")

    fun problem(card: GeneratedCard): GeneratedCardProblem? {
        val front = card.front.trim()
        val back = card.back.trim()
        return when {
            front.isEmpty() -> GeneratedCardProblem.EmptyFront
            front.length > MAX_FRONT_CHARS || back.length > MAX_BACK_CHARS -> GeneratedCardProblem.TooLong
            card.kind == NoteKind.Cloze && CLOZE_OPENING.findAll(front).count() > Cloze.parse(front).count { it is Cloze.Segment.Deletion } ->
                GeneratedCardProblem.BrokenCloze
            card.kind == NoteKind.Cloze && Cloze.ordinals(front).isEmpty() -> GeneratedCardProblem.NoCloze
            card.kind != NoteKind.Cloze && back.isEmpty() -> GeneratedCardProblem.EmptyBack
            card.kind == NoteKind.MultipleChoice && MultipleChoice.wrongAnswers(MultipleChoice.wrongField(card.wrongAnswers), back).isEmpty() ->
                GeneratedCardProblem.NoWrongAnswers
            else -> null
        }
    }

    /** [card] trimmed, or null if it has a [problem]. */
    fun validate(card: GeneratedCard): GeneratedCard? {
        if (problem(card) != null) return null
        return card.copy(
            front = card.front.trim(),
            back = card.back.trim(),
            tags = card.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
            wrongAnswers = if (card.kind == NoteKind.MultipleChoice) {
                MultipleChoice.wrongAnswers(MultipleChoice.wrongField(card.wrongAnswers), card.back)
            } else {
                emptyList()
            },
        )
    }

    /**
     * What a card says, ignoring case, punctuation, Markdown and cloze markup: two cards with the
     * same key ask the same thing.
     */
    fun key(front: String): String =
        Cloze.reveal(front).lowercase().replace(MARKUP, " ").replace(WHITESPACE, " ").trim()
}
