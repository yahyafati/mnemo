package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Read-only analytics over the review log and cards (ADR 0007). Everything is aggregated in the
 * database; "today" and day boundaries follow `StudyDay`.
 */
interface StatsRepository {
    /**
     * Answers to review-state cards in [from, until) and how many were recalled. With no [until],
     * answers given while the flow is observed count too.
     */
    fun observePassCounts(from: Instant, until: Instant? = null): Flow<ReviewPassCounts>

    /** Answers per study day from [from] on, oldest first; days without reviews are left out. */
    fun observeDailyReviews(from: LocalDate): Flow<List<DailyReviews>>

    /** Every deck's unsuspended cards by maturity (decks without cards are left out). */
    fun observeDeckMaturity(): Flow<List<DeckMaturity>>

    /** Studied cards per deck, bucketed by elapsed days / stability, as of now. */
    fun observeRetrievability(): Flow<List<RetrievabilityBucket>>

    /** Cards due on each of the next [days] study days, today first; days with none are left out. */
    fun observeDueForecast(days: Int): Flow<List<DueForecast>>

    /** Cards forgotten at least [minLapses] times, most lapses (then highest difficulty) first. */
    fun observeMostLapsed(minLapses: Int, limit: Int): Flow<List<StudyCard>>

    /**
     * Answers a "review every card once a day" routine would have taken since [from]: the
     * baseline for time saved.
     */
    fun observeDailyReviewBaseline(from: Instant): Flow<Long>
}
