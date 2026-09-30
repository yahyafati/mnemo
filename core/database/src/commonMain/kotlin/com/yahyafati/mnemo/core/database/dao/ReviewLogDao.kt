package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewLogDao {
    @Insert
    suspend fun insert(log: ReviewLogEntity)

    @Insert
    suspend fun insertAll(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_logs WHERE cardId IN (:cardIds) AND deletedAt IS NULL ORDER BY reviewedAt")
    suspend fun getForCards(cardIds: List<String>): List<ReviewLogEntity>

    /** Undo removes the row outright: an undone answer never happened. */
    @Query("DELETE FROM review_logs WHERE id = :id")
    suspend fun delete(id: String)

    /** What was studied since [dayStart], for the daily limits and the header. */
    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN stateBefore = 0 THEN 1 ELSE 0 END), 0) AS newStudied,
            COALESCE(SUM(CASE WHEN stateBefore = 2 THEN 1 ELSE 0 END), 0) AS reviewsDone,
            COUNT(*) AS total
        FROM review_logs WHERE deletedAt IS NULL AND reviewedAt >= :dayStart
        """,
    )
    fun observeCountsSince(dayStart: Long): Flow<ReviewCounts>

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN stateBefore = 0 THEN 1 ELSE 0 END), 0) AS newStudied,
            COALESCE(SUM(CASE WHEN stateBefore = 2 THEN 1 ELSE 0 END), 0) AS reviewsDone,
            COUNT(*) AS total
        FROM review_logs WHERE deletedAt IS NULL AND reviewedAt >= :dayStart
        """,
    )
    suspend fun getCountsSince(dayStart: Long): ReviewCounts

    /**
     * Distinct study days that have at least one review, newest first. A day number is
     * `(reviewedAt + offset) / 1 day`, where the caller's [offsetMs] folds in the time zone and
     * the day rollover hour.
     */
    @Query(
        """
        SELECT DISTINCT (reviewedAt + :offsetMs) / 86400000 AS day FROM review_logs
        WHERE deletedAt IS NULL ORDER BY day DESC LIMIT :limit
        """,
    )
    fun observeReviewDays(offsetMs: Long, limit: Int = 3660): Flow<List<Long>>

    @Query("SELECT AVG(durationMs) FROM review_logs WHERE deletedAt IS NULL AND reviewedAt >= :since")
    fun observeAverageDurationMs(since: Long): Flow<Double?>

    /** Every card that has a review, so review histories can be read a few cards at a time. */
    @Query("SELECT DISTINCT cardId FROM review_logs WHERE deletedAt IS NULL ORDER BY cardId")
    suspend fun getReviewedCardIds(): List<String>

    /** The reviews of [cardIds], grouped by card, oldest first: what the FSRS optimizer needs. */
    @Query(
        """
        SELECT cardId, reviewedAt, rating FROM review_logs
        WHERE deletedAt IS NULL AND cardId IN (:cardIds) ORDER BY cardId, reviewedAt
        """,
    )
    suspend fun getReviewPoints(cardIds: List<String>): List<ReviewPoint>
}

data class ReviewPoint(
    val cardId: String,
    val reviewedAt: Long,
    val rating: Int,
)

data class ReviewCounts(
    val newStudied: Int,
    val reviewsDone: Int,
    val total: Int,
)
