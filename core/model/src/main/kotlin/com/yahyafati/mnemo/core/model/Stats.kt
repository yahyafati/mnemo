package com.yahyafati.mnemo.core.model

import java.time.LocalDate

// Aggregates behind Analytics and the Decks screen's retention tiles (ADR 0007). The database
// computes them; nothing here holds individual cards or reviews, except the few leeches.

/**
 * Answers to cards that were in review state, and how many of them were recalled (anything but
 * Again). Their ratio is the "true retention" to compare with the desired retention.
 */
data class ReviewPassCounts(val reviews: Int, val passed: Int) {
    /** Share of reviews recalled, or null with no reviews. */
    val rate: Double? get() = if (reviews == 0) null else passed.toDouble() / reviews

    companion object {
        val None = ReviewPassCounts(0, 0)
    }
}

/** Answers given on one study day. */
data class DailyReviews(val date: LocalDate, val reviews: Int)

/** Cards that come due on one study day; overdue cards count toward today. */
data class DueForecast(val date: LocalDate, val cards: Int)

/**
 * One deck's cards (not its subdecks) by maturity. Suspended cards are left out. A review card is
 * mature once its stability reaches [MATURE_STABILITY_DAYS].
 */
data class DeckMaturity(
    val deckId: String,
    val newCards: Int,
    val learning: Int,
    val young: Int,
    val mature: Int,
    /** Sum of the review cards' stabilities (days), for averages across decks. */
    val reviewStabilitySum: Double,
) {
    val reviewCards: Int get() = young + mature

    /** Cards that have been studied: everything but new cards. */
    val studiedCards: Int get() = learning + young + mature

    /** Both decks' counts together, under this deck's id (for rolling subdecks up). */
    operator fun plus(other: DeckMaturity) = DeckMaturity(
        deckId = deckId,
        newCards = newCards + other.newCards,
        learning = learning + other.learning,
        young = young + other.young,
        mature = mature + other.mature,
        reviewStabilitySum = reviewStabilitySum + other.reviewStabilitySum,
    )

    companion object {
        /** Anki calls a card mature at a 21-day interval; FSRS stability is the interval at 90% retention. */
        const val MATURE_STABILITY_DAYS = 21.0
    }
}

/**
 * Cards of one deck whose "elapsed days / stability" ratios fall close together. Retrievability
 * depends only on that ratio, so a few of these per deck give the deck's average retrievability
 * without reading its cards (ADR 0007).
 */
data class RetrievabilityBucket(
    val deckId: String,
    val cards: Int,
    /** Mean of elapsed whole days since the last review divided by stability. */
    val meanElapsedRatio: Double,
)

/** What the FSRS optimizer did (Settings › Scheduling). Losses are mean log loss: lower is better. */
sealed interface FsrsOptimizationOutcome {
    /** The fitted weights predicted the history better than the current ones and are now in use. */
    data class Applied(val trainingReviews: Int, val previousLoss: Double, val loss: Double) : FsrsOptimizationOutcome

    /** The fit was no better than the weights in use, which were kept. */
    data class NoImprovement(val trainingReviews: Int, val previousLoss: Double, val loss: Double) : FsrsOptimizationOutcome

    /** Too little history to fit: [trainingReviews] of the [required] reviews made a day or more after the previous one. */
    data class NotEnoughReviews(val trainingReviews: Int, val required: Int) : FsrsOptimizationOutcome
}
