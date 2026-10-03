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
    /**
     * The schedule this answer produced, so that a replay (sync) can start from any review. All null for
     * reviews imported from Anki and for those made before schema v6; otherwise all set, except [stepAfter],
     * which a card in the Review state does not have.
     */
    val stateAfter: CardState? = null,
    val stepAfter: Int? = null,
    val dueAfter: Instant? = null,
    val repsAfter: Int? = null,
    val lapsesAfter: Int? = null,
)
