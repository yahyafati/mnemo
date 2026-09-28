package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import com.yahyafati.mnemo.core.scheduler.FsrsState
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/** The result of answering a card: its new state, the log row, and the state before (for undo). */
data class CardAnswer(
    val previous: Card,
    val card: Card,
    val log: ReviewLog,
) {
    val rating: Rating get() = log.rating

    /** Time until the card is due again: the label on the rating button. */
    val interval: Duration get() = Duration.between(log.reviewedAt, card.due)
}

/**
 * Applies FSRS to model [Card]s, with the user's retention and steps. Pure and fast, so the study
 * screen can call it on the main thread while showing a card.
 *
 * Interval fuzz is seeded from the card id and its review count, so previewing the four ratings
 * and then answering gives the same result.
 */
class StudyScheduler(settings: UserSettings) {
    private val fsrs = Fsrs(
        FsrsParameters(
            desiredRetention = settings.desiredRetention,
            learningSteps = settings.learningSteps,
            relearningSteps = settings.relearningSteps,
        ),
    )

    fun answer(card: Card, rating: Rating, reviewedAt: Instant, durationMs: Long = 0): CardAnswer {
        val next = fsrs.review(card.toFsrs(), rating.toFsrs(), reviewedAt, Random(fuzzSeed(card)))
        val updated = card.copy(
            state = next.state.toModel(),
            due = next.due,
            stability = next.stability,
            difficulty = next.difficulty,
            step = next.step,
            lastReview = reviewedAt,
            reps = card.reps + 1,
            lapses = card.lapses + if (card.state == CardState.Review && rating == Rating.Again) 1 else 0,
            updatedAt = reviewedAt,
        )
        val log = ReviewLog(
            id = UUID.randomUUID().toString(),
            cardId = card.id,
            rating = rating,
            stateBefore = card.state,
            reviewedAt = reviewedAt,
            elapsedDays = card.lastReview?.let { wholeDays(it, reviewedAt) } ?: 0,
            scheduledDays = wholeDays(reviewedAt, next.due),
            durationMs = durationMs,
            stabilityAfter = checkNotNull(next.stability),
            difficultyAfter = checkNotNull(next.difficulty),
        )
        return CardAnswer(previous = card, card = updated, log = log)
    }

    /** Every rating's outcome for [card] at [now]. */
    fun preview(card: Card, now: Instant): Map<Rating, CardAnswer> =
        Rating.entries.associateWith { answer(card, it, now) }

    private fun fuzzSeed(card: Card): Long = card.id.hashCode().toLong() * 31 + card.reps

    private fun wholeDays(from: Instant, to: Instant): Int =
        Math.floorDiv(Duration.between(from, to).toMillis(), Duration.ofDays(1).toMillis()).toInt().coerceAtLeast(0)

    private fun Card.toFsrs(): FsrsCard = when (state) {
        CardState.New -> FsrsCard(due = due)
        CardState.Learning -> FsrsCard(due, FsrsState.Learning, step ?: 0, stability, difficulty, lastReview)
        CardState.Review -> FsrsCard(due, FsrsState.Review, null, stability, difficulty, lastReview)
        CardState.Relearning -> FsrsCard(due, FsrsState.Relearning, step ?: 0, stability, difficulty, lastReview)
    }

    private fun Rating.toFsrs(): FsrsRating = FsrsRating.entries.first { it.value == value }

    private fun FsrsState.toModel(): CardState = when (this) {
        FsrsState.Learning -> CardState.Learning
        FsrsState.Review -> CardState.Review
        FsrsState.Relearning -> CardState.Relearning
    }
}
