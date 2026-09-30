package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.StatsRepository
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * [StatsRepository] with aggregates the test sets directly. Pass counts are keyed by the window's
 * start, so the current and previous 30 days can differ.
 */
class FakeStatsRepository : StatsRepository {
    val passCounts = MutableStateFlow<Map<Instant, ReviewPassCounts>>(emptyMap())
    val dailyReviews = MutableStateFlow<List<DailyReviews>>(emptyList())
    val deckMaturity = MutableStateFlow<List<DeckMaturity>>(emptyList())
    val retrievability = MutableStateFlow<List<RetrievabilityBucket>>(emptyList())
    val dueForecast = MutableStateFlow<List<DueForecast>>(emptyList())
    val mostLapsed = MutableStateFlow<List<StudyCard>>(emptyList())
    val dailyReviewBaseline = MutableStateFlow(0L)

    override fun observePassCounts(from: Instant, until: Instant?): Flow<ReviewPassCounts> =
        passCounts.map { it[from] ?: ReviewPassCounts.None }

    override fun observeDailyReviews(from: LocalDate): Flow<List<DailyReviews>> =
        dailyReviews.map { days -> days.filter { !it.date.isBefore(from) } }

    override fun observeDeckMaturity(): Flow<List<DeckMaturity>> = deckMaturity

    override fun observeRetrievability(): Flow<List<RetrievabilityBucket>> = retrievability

    override fun observeDueForecast(days: Int): Flow<List<DueForecast>> = dueForecast

    override fun observeMostLapsed(minLapses: Int, limit: Int): Flow<List<StudyCard>> =
        mostLapsed.map { cards -> cards.filter { it.card.lapses >= minLapses }.take(limit) }

    override fun observeDailyReviewBaseline(from: Instant): Flow<Long> = dailyReviewBaseline
}
