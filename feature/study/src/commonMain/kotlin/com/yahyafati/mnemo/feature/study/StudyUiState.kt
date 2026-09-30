package com.yahyafati.mnemo.feature.study

import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.ui.card.CardResponse
import java.time.Duration

data class StudyUiState(
    /** The deck being studied, or null for the Daily Mix. */
    val deckName: String? = null,
    val phase: StudyPhase = StudyPhase.Loading,
)

sealed interface StudyPhase {
    data object Loading : StudyPhase

    /** Nothing to study. [laterCount] learning cards come back later today. */
    data class Empty(val laterCount: Int) : StudyPhase

    data class Reviewing(
        val card: StudyCard,
        val revealed: Boolean,
        /** Time until the card is due again after each rating: the button labels. */
        val intervals: Map<Rating, Duration>,
        /** 1-based position of this card in the session. */
        val position: Int,
        /** Cards answered plus cards left; grows when a card is sent back to learning. */
        val total: Int,
        val canUndo: Boolean,
        /** What was typed, picked, or asked for (hint) on this card so far. */
        val response: CardResponse = CardResponse(),
        /**
         * For type-in and multiple-choice cards once answered: Good if the answer was right, Again
         * if not. Only a suggestion; the learner still rates.
         */
        val suggestedRating: Rating? = null,
    ) : StudyPhase {
        val progress: Float get() = if (total == 0) 0f else (position - 1).toFloat() / total
    }

    data class Finished(
        val summary: SessionSummary,
        val laterCount: Int,
        val canUndo: Boolean,
    ) : StudyPhase
}

data class SessionSummary(
    val ratings: Map<Rating, Int>,
    val totalTimeMs: Long,
) {
    val reviewed: Int get() = ratings.values.sum()

    /** Share of answers that were not "Again", 0–100. */
    val accuracyPercent: Int
        get() = if (reviewed == 0) 0 else Math.round(100.0 * (reviewed - (ratings[Rating.Again] ?: 0)) / reviewed).toInt()
}

sealed interface StudyAction {
    /** Tap on the card: show (or hide again) the answer. */
    data object Flip : StudyAction

    /**
     * The keyboard's Space or Enter: show the answer, and never hide it again. (A click on the card
     * toggles, [Flip]; a key can arrive twice, once from a button's own handling and once as the
     * typed character, and must not undo itself.)
     */
    data object Reveal : StudyAction

    data class Rate(val rating: Rating) : StudyAction

    /** The type-in answer changed. */
    data class TypeAnswer(val text: String) : StudyAction

    /** Picked multiple-choice option [index]: shows the answer. */
    data class Choose(val index: Int) : StudyAction

    data object ShowHint : StudyAction

    data object Undo : StudyAction

    data object ToggleStar : StudyAction

    data object ToggleFlag : StudyAction

    /** Hide the card until tomorrow. */
    data object Bury : StudyAction

    /** Take the card out of study until unsuspended. */
    data object Suspend : StudyAction

    /** Check again for learning cards that have come due. */
    data object Continue : StudyAction
}
