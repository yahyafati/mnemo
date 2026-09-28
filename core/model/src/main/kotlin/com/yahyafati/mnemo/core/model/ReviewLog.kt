package com.yahyafati.mnemo.core.model

import java.time.Instant

/**
 * One answer. Holds what FSRS replay and the optimizer need (ADR 0001): the rating, when, the
 * state before, and the gap since the previous review.
 */
data class ReviewLog(
    val id: String,
    val cardId: String,
    val rating: Rating,
    val stateBefore: CardState,
    val reviewedAt: Instant,
    /** Whole days since the previous review; 0 for the first one. */
    val elapsedDays: Int,
    /** Whole days until the next review as scheduled by this answer. */
    val scheduledDays: Int,
    val durationMs: Long,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
)
