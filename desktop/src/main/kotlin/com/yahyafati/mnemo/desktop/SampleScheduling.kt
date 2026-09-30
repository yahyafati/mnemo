package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import com.yahyafati.mnemo.core.scheduler.FsrsState
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** A sample card and what FSRS would do with each answer: proof that the JVM modules run here. */
data class SampleCard(
    val title: String,
    val card: Card,
    val intervals: Map<Rating, Duration>,
)

/** Two cards: one never studied and one last reviewed 12 days ago, at [now]. */
fun sampleCards(now: Instant): List<SampleCard> {
    val fresh = Card(
        id = "sample-new",
        noteId = "sample-note",
        deckId = "sample-deck",
        templateOrd = 0,
        state = CardState.New,
        due = now,
        createdAt = now,
        updatedAt = now,
    )
    val lastReview = now.minus(Duration.ofDays(12))
    val seasoned = fresh.copy(
        id = "sample-review",
        state = CardState.Review,
        due = now,
        stability = 12.4,
        difficulty = 5.3,
        lastReview = lastReview,
        reps = 4,
    )
    return listOf(
        SampleCard("New card", fresh, intervalsFor(fresh, now)),
        SampleCard("Review card, last seen 12 days ago", seasoned, intervalsFor(seasoned, now)),
    )
}

/** Time until [card] is due again for each rating. Fuzz is seeded, so the labels don't jump. */
fun intervalsFor(card: Card, now: Instant): Map<Rating, Duration> {
    val info = Fsrs().preview(card.toFsrs(), now, random = { Random(0) })
    return Rating.entries.associateWith { info.interval(it.toFsrs()) }
}

private fun Card.toFsrs() = FsrsCard(
    due = due,
    state = when (state) {
        CardState.New, CardState.Learning -> FsrsState.Learning
        CardState.Review -> FsrsState.Review
        CardState.Relearning -> FsrsState.Relearning
    },
    step = if (state == CardState.Review) null else step ?: 0,
    stability = stability,
    difficulty = difficulty,
    lastReview = lastReview,
)

private fun Rating.toFsrs() = FsrsRating.entries.first { it.value == value }
