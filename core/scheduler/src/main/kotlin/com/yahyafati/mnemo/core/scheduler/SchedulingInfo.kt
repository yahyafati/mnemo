package com.yahyafati.mnemo.core.scheduler

import java.time.Duration
import java.time.Instant

/** The next state for each rating, computed when a card is shown. */
data class SchedulingInfo(
    val reviewedAt: Instant,
    val outcomes: Map<FsrsRating, FsrsCard>,
) {
    operator fun get(rating: FsrsRating): FsrsCard = outcomes.getValue(rating)

    /** How long until the card comes back after [rating]: the label on that rating button. */
    fun interval(rating: FsrsRating): Duration = Duration.between(reviewedAt, get(rating).due)
}
