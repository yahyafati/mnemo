package com.yahyafati.mnemo.core.scheduler

import java.time.Instant

// The scheduler has its own small types so it depends on nothing but the JDK (ARCHITECTURE §3,
// rule 2). `:core:domain` maps them to and from `:core:model`.

enum class FsrsRating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
}

/** A never-reviewed card is [Learning] at step 0 with no memory state yet. */
enum class FsrsState {
    Learning,
    Review,
    Relearning,
}

/**
 * The scheduling state of one card.
 *
 * @property step index into the learning or relearning steps; null in [FsrsState.Review].
 * @property stability days until retrievability falls to 90%; null before the first review.
 * @property difficulty 1 (easy) to 10 (hard); null before the first review.
 */
data class FsrsCard(
    val due: Instant,
    val state: FsrsState = FsrsState.Learning,
    val step: Int? = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val lastReview: Instant? = null,
)

/** Stability (days) and difficulty (1–10) without the rest of a card's schedule. */
data class FsrsMemoryState(
    val stability: Double,
    val difficulty: Double,
)
