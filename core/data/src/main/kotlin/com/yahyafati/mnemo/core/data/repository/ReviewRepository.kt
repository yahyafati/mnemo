package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.ReviewLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ReviewRepository {
    /** Saves an answer: the updated card and its review log, in one transaction. */
    suspend fun recordAnswer(card: Card, log: ReviewLog)

    /** Reverts an answer: restores [previous] and deletes the log, in one transaction. */
    suspend fun undoAnswer(previous: Card, logId: String)

    /** What has been studied since the current study day started. */
    suspend fun getTodayCounts(): DailyReviewCounts

    fun observeTodayCounts(): Flow<DailyReviewCounts>

    /** Study dates with at least one review, newest first. */
    fun observeStudyDates(): Flow<List<LocalDate>>

    /** Mean answer time over the last 30 days, or null with no reviews. */
    fun observeAverageAnswerMs(): Flow<Double?>
}
