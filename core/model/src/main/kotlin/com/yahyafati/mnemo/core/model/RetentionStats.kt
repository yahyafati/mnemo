package com.yahyafati.mnemo.core.model

import java.time.Duration

/** Everything the Analytics screen shows, computed on device from the review log (ADR 0007). */
data class RetentionStats(
    /** False until the first answer: the screen shows an empty state instead. */
    val hasReviews: Boolean,
    val desiredRetention: Double,
    /** Weights fitted by the optimizer, or null for the FSRS-6 defaults. */
    val fsrsWeights: FsrsWeights?,
    /** True retention over the last 30 days, and over the 30 days before that. */
    val retention: ReviewPassCounts,
    val previousRetention: ReviewPassCounts,
    /** Every answer in the last 30 days, learning steps included. */
    val reviewsLast30Days: Int,
    /** Mean stability of review cards in days, or null with none. */
    val averageStability: Double?,
    /** Estimated study time saved in the last 30 days compared with reviewing every card daily. */
    val timeSaved: Duration,
    val averageAnswer: Duration,
    val forgettingCurve: ForgettingCurve,
    /** Answers per study day, oldest first, ending today (days without reviews included). */
    val activity: List<DailyReviews>,
    val streakDays: Int,
    /** Top-level decks, subdecks included, most studied first. */
    val decks: List<DeckRetention>,
    /** Cards due on each of the coming study days, today first (days with none included). */
    val forecast: List<DueForecast>,
    /** The cards forgotten most often, most lapses first. */
    val hardestCards: List<StudyCard>,
) {
    companion object {
        /** A card forgotten this many times is a leech (Anki's default threshold). */
        const val LEECH_LAPSES = 8
    }
}

/**
 * How recall of one typical card changes over [horizonDays]: reviewed with Good whenever FSRS
 * schedules it (at the desired retention), and left alone after its first review.
 */
data class ForgettingCurve(
    val horizonDays: Int,
    /** Recall probability with the scheduled reviews; it jumps back to 1 at each of [reviewDays]. */
    val scheduled: List<CurvePoint>,
    /** Days (since the first review) on which the scheduled reviews happen. */
    val reviewDays: List<Int>,
    /** Recall probability with no review after the first. */
    val passive: List<CurvePoint>,
) {
    /** Mean recall probability over the horizon, with and without reviews. */
    val scheduledAverage: Double get() = scheduled.map { it.recall }.average()
    val passiveAverage: Double get() = passive.map { it.recall }.average()
}

data class CurvePoint(val day: Double, val recall: Double)

/** A top-level deck (subdecks included) in Analytics' stability breakdown. */
data class DeckRetention(
    val deckId: String,
    val name: String,
    val maturity: DeckMaturity,
    /** Average probability of recalling its studied cards right now, or null with none. */
    val recallNow: Double?,
)

/**
 * The retention numbers on the Decks screen: the "Retained" and "Mastered" tiles and each deck's
 * retention health.
 */
data class RetentionOverview(
    /** True retention over the last 30 days, or null without reviews. */
    val retention: Double?,
    val matureCards: Int,
    /** Current recall of each deck's own cards (not its subdecks'), by deck id. */
    val deckRecall: Map<String, RecallTotal>,
) {
    companion object {
        val Empty = RetentionOverview(retention = null, matureCards = 0, deckRecall = emptyMap())
    }
}

/** Summed recall probabilities of [cards] cards, so averages can be combined across decks. */
data class RecallTotal(val cards: Int, val total: Double) {
    val average: Double? get() = if (cards == 0) null else total / cards

    operator fun plus(other: RecallTotal) = RecallTotal(cards + other.cards, total + other.total)

    companion object {
        val None = RecallTotal(0, 0.0)
    }
}
