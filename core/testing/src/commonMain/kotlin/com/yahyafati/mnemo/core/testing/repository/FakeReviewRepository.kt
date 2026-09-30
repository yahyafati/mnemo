package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.ReviewLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory [ReviewRepository]. Answers are kept in [logs] and, when [cards] is given, applied to
 * it, so a test can follow the whole answer → save → undo loop.
 */
class FakeReviewRepository(
    private val cards: FakeCardRepository? = null,
) : ReviewRepository {
    val logs = MutableStateFlow<List<ReviewLog>>(emptyList())
    val studyDates = MutableStateFlow<List<LocalDate>>(emptyList())
    val averageAnswerMs = MutableStateFlow<Double?>(null)

    /** Counts before any answer recorded here, e.g. from an earlier session today. */
    val baseCounts = MutableStateFlow(DailyReviewCounts.None)
    private val todayCounts = MutableStateFlow(DailyReviewCounts.None)

    override suspend fun recordAnswer(card: Card, log: ReviewLog) {
        cards?.putCard(card)
        logs.update { it + log }
        refreshCounts()
    }

    override suspend fun undoAnswer(previous: Card, logId: String) {
        cards?.putCard(previous)
        logs.update { list -> list.filterNot { it.id == logId } }
        refreshCounts()
    }

    override suspend fun getTodayCounts(): DailyReviewCounts = counts()

    override fun observeTodayCounts(): Flow<DailyReviewCounts> = todayCounts.also { refreshCounts() }

    override fun observeStudyDates(): Flow<List<LocalDate>> = studyDates

    override fun observeAverageAnswerMs(): Flow<Double?> = averageAnswerMs

    private fun refreshCounts() {
        todayCounts.value = counts()
    }

    private fun counts(): DailyReviewCounts {
        val base = baseCounts.value
        val list = logs.value
        return DailyReviewCounts(
            newStudied = base.newStudied + list.count { it.stateBefore == CardState.New },
            reviewsDone = base.reviewsDone + list.count { it.stateBefore == CardState.Review },
            total = base.total + list.size,
        )
    }
}
