package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Analytics, computed by SQLite (ARCHITECTURE §6, ADR 0007). Every query returns aggregates (a
 * row per day, deck or bucket), never one row per card or review, so the screens stay fast with
 * hundreds of thousands of reviews. Day numbers are `(millis + offsetMs) / 1 day`, with the
 * caller's offset folding in the time zone and the study-day rollover.
 */
@Dao
interface StatsDao {
    /** Answers to review-state cards in [from, until), and how many were not Again. */
    @Query(
        """
        SELECT COUNT(*) AS reviews, COALESCE(SUM(CASE WHEN rating > 1 THEN 1 ELSE 0 END), 0) AS passed
        FROM review_logs
        WHERE deletedAt IS NULL AND stateBefore = 2 AND reviewedAt >= :from AND reviewedAt < :until
        """,
    )
    fun observePassCounts(from: Long, until: Long): Flow<PassCounts>

    /** Answers per study day since [from], oldest first. Days without reviews are left out. */
    @Query(
        """
        SELECT (reviewedAt + :offsetMs) / 86400000 AS day, COUNT(*) AS count FROM review_logs
        WHERE deletedAt IS NULL AND reviewedAt >= :from
        GROUP BY day ORDER BY day
        """,
    )
    fun observeDailyReviews(from: Long, offsetMs: Long): Flow<List<DayCount>>

    /**
     * Per-deck card counts by maturity, over unsuspended cards. Review cards whose stability is at
     * least [matureDays] are mature.
     */
    @Query(
        """
        SELECT deckId,
            COALESCE(SUM(CASE WHEN state = 0 THEN 1 ELSE 0 END), 0) AS newCards,
            COALESCE(SUM(CASE WHEN state IN (1, 3) THEN 1 ELSE 0 END), 0) AS learning,
            COALESCE(SUM(CASE WHEN state = 2 AND stability < :matureDays THEN 1 ELSE 0 END), 0) AS young,
            COALESCE(SUM(CASE WHEN state = 2 AND stability >= :matureDays THEN 1 ELSE 0 END), 0) AS mature,
            COALESCE(SUM(CASE WHEN state = 2 THEN stability ELSE 0 END), 0) AS reviewStabilitySum
        FROM cards
        WHERE deletedAt IS NULL AND suspended = 0
        GROUP BY deckId
        """,
    )
    fun observeDeckMaturity(matureDays: Double): Flow<List<DeckMaturityRow>>

    /**
     * Studied, unsuspended cards grouped per deck by `elapsed whole days / stability` at [now]:
     * 0.1-wide buckets below 10, 1-wide up to 60, and one bucket beyond. Retrievability depends
     * only on that ratio, so the caller evaluates it once per bucket at the bucket's mean.
     */
    @Query(
        """
        SELECT deckId, COUNT(*) AS cards, AVG(ratio) AS meanRatio FROM (
            SELECT deckId, MAX((:now - lastReview) / 86400000, 0) / stability AS ratio FROM cards
            WHERE deletedAt IS NULL AND suspended = 0 AND state != 0
                AND lastReview IS NOT NULL AND stability > 0
        )
        GROUP BY deckId, CASE WHEN ratio < 10 THEN CAST(ratio * 10 AS INTEGER) ELSE 100 + CAST(MIN(ratio, 60) AS INTEGER) END
        """,
    )
    fun observeRetrievabilityBuckets(now: Long): Flow<List<RetrievabilityRow>>

    /**
     * Unsuspended studied cards due before [until], per study day. Cards due before [todayStart]
     * (overdue) count toward [today], the current day number.
     */
    @Query(
        """
        SELECT CASE WHEN due < :todayStart THEN :today ELSE (due + :offsetMs) / 86400000 END AS day, COUNT(*) AS count
        FROM cards
        WHERE deletedAt IS NULL AND suspended = 0 AND state != 0 AND due < :until
        GROUP BY day ORDER BY day
        """,
    )
    fun observeDueForecast(todayStart: Long, today: Long, until: Long, offsetMs: Long): Flow<List<DayCount>>

    /** The cards forgotten most often (at least [minLapses] times), most lapses first. */
    @Query(
        """
        SELECT id FROM cards WHERE deletedAt IS NULL AND lapses >= :minLapses
        ORDER BY lapses DESC, difficulty DESC LIMIT :limit
        """,
    )
    fun observeMostLapsed(minLapses: Int, limit: Int): Flow<List<String>>

    /**
     * How many answers a routine of reviewing every card once a day would have taken since [from]:
     * for each card, the whole days between its first review (or [from], if later) and [now].
     */
    @Query(
        """
        SELECT COALESCE(SUM((:now - MAX(firstReview, :from)) / 86400000), 0) FROM (
            SELECT MIN(l.reviewedAt) AS firstReview FROM review_logs l
            JOIN cards c ON c.id = l.cardId AND c.deletedAt IS NULL
            WHERE l.deletedAt IS NULL
            GROUP BY l.cardId
        )
        WHERE firstReview < :now
        """,
    )
    fun observeDailyReviewBaseline(from: Long, now: Long): Flow<Long>
}

data class PassCounts(val reviews: Int, val passed: Int)

data class DayCount(val day: Long, val count: Int)

data class DeckMaturityRow(
    val deckId: String,
    val newCards: Int,
    val learning: Int,
    val young: Int,
    val mature: Int,
    val reviewStabilitySum: Double,
)

data class RetrievabilityRow(val deckId: String, val cards: Int, val meanRatio: Double)
