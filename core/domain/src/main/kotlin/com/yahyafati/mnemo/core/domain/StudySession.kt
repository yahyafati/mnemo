package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.StudyCard
import java.time.Duration
import java.time.Instant

/**
 * The in-memory queue of a study session (ARCHITECTURE §5.1). Immutable, so undo can restore an
 * earlier session as-is.
 *
 * Order: learning cards as they come due, then [queue] (reviews with new cards spread among
 * them). A card answered back into learning (e.g. "Again") returns to [learning] and is shown
 * again once due, or early if nothing else is left and it is due within [learnAhead].
 */
data class StudySession(
    val queue: List<StudyCard>,
    /** Learning and relearning cards due today, earliest first. */
    val learning: List<StudyCard>,
    val dayEnd: Instant,
    val answeredCount: Int = 0,
    val learnAhead: Duration = DEFAULT_LEARN_AHEAD,
) {
    val remainingCount: Int get() = queue.size + learning.size

    /** The card to show at [now], or null when nothing is left for now. */
    fun next(now: Instant): StudyCard? {
        learning.firstOrNull()?.takeIf { !it.card.due.isAfter(now) }?.let { return it }
        queue.firstOrNull()?.let { return it }
        return learning.firstOrNull()?.takeIf { !it.card.due.isAfter(now.plus(learnAhead)) }
    }

    /** The session after [answer] was given to its card. */
    fun afterAnswer(answer: CardAnswer): StudySession {
        val rest = without(answer.card.id)
        val updated = (queue + learning).firstOrNull { it.card.id == answer.card.id }
            ?.copy(card = answer.card)
        val returnsToday = answer.card.state.isLearning && answer.card.due.isBefore(dayEnd)
        return rest.copy(
            learning = if (updated != null && returnsToday) (rest.learning + updated).sortedBy { it.card.due } else rest.learning,
            answeredCount = answeredCount + 1,
        )
    }

    /** The session without the card [cardId] (buried, suspended or deleted). */
    fun without(cardId: String): StudySession = copy(
        queue = queue.filterNot { it.card.id == cardId },
        learning = learning.filterNot { it.card.id == cardId },
    )

    /** Swaps in fresh copies of cards (after an edit), matched by id. */
    fun refreshed(cards: List<StudyCard>): StudySession {
        val byId = cards.associateBy { it.card.id }
        fun List<StudyCard>.refresh() = map { current ->
            byId[current.card.id]?.let { fresh -> fresh.copy(card = current.card) } ?: current
        }
        return copy(queue = queue.refresh(), learning = learning.refresh())
    }

    /** Learning cards left that are not due within the learn-ahead window: "come back later". */
    fun laterCount(now: Instant): Int = if (next(now) == null) learning.size else 0

    companion object {
        /** Like Anki's default: learning cards due within 20 minutes may be shown early. */
        val DEFAULT_LEARN_AHEAD: Duration = Duration.ofMinutes(20)

        fun create(
            learning: List<StudyCard>,
            review: List<StudyCard>,
            new: List<StudyCard>,
            dayEnd: Instant,
        ) = StudySession(
            queue = interleave(review, new),
            learning = learning.sortedBy { it.card.due },
            dayEnd = dayEnd,
        )

        /** Spreads [new] evenly through [review], starting half a gap in. */
        internal fun <T> interleave(review: List<T>, new: List<T>): List<T> {
            if (new.isEmpty()) return review
            if (review.isEmpty()) return new
            val total = review.size + new.size
            val result = ArrayList<T>(total)
            var r = 0
            var n = 0
            for (i in 0 until total) {
                val placeNew = n < new.size && (r >= review.size || (2 * n + 1) * total <= 2 * (i + 1) * new.size)
                if (placeNew) result += new[n++] else result += review[r++]
            }
            return result
        }
    }
}

private val CardState.isLearning: Boolean
    get() = this == CardState.Learning || this == CardState.Relearning
