package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.database.dao.StatsDao
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

internal class OfflineStatsRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val cardRepository: CardRepository,
    private val clock: Clock,
) : StatsRepository {
    override fun observePassCounts(from: Instant, until: Instant?): Flow<ReviewPassCounts> =
        statsDao.observePassCounts(from.toEpochMilli(), until?.toEpochMilli() ?: Long.MAX_VALUE)
            .map { ReviewPassCounts(it.reviews, it.passed) }

    override fun observeDailyReviews(from: LocalDate): Flow<List<DailyReviews>> =
        statsDao.observeDailyReviews(StudyDay.start(from, clock.zone()).toEpochMilli(), dayOffset())
            .map { rows -> rows.map { DailyReviews(LocalDate.ofEpochDay(it.day), it.count) } }

    override fun observeDeckMaturity(): Flow<List<DeckMaturity>> =
        statsDao.observeDeckMaturity(DeckMaturity.MATURE_STABILITY_DAYS).map { rows ->
            rows.map { DeckMaturity(it.deckId, it.newCards, it.learning, it.young, it.mature, it.reviewStabilitySum) }
        }

    override fun observeRetrievability(): Flow<List<RetrievabilityBucket>> =
        statsDao.observeRetrievabilityBuckets(clock.now().toEpochMilli()).map { rows ->
            rows.map { RetrievabilityBucket(it.deckId, it.cards, it.meanRatio) }
        }

    override fun observeDueForecast(days: Int): Flow<List<DueForecast>> {
        val zone = clock.zone()
        val today = StudyDay.date(clock.now(), zone)
        return statsDao.observeDueForecast(
            todayStart = StudyDay.start(today, zone).toEpochMilli(),
            today = today.toEpochDay(),
            until = StudyDay.start(today.plusDays(days.toLong()), zone).toEpochMilli(),
            offsetMs = dayOffset(),
        ).map { rows -> rows.map { DueForecast(LocalDate.ofEpochDay(it.day), it.count) } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMostLapsed(minLapses: Int, limit: Int): Flow<List<StudyCard>> =
        statsDao.observeMostLapsed(minLapses, limit).mapLatest { ids -> cardRepository.getStudyCards(ids) }

    override fun observeDailyReviewBaseline(from: Instant): Flow<Long> =
        statsDao.observeDailyReviewBaseline(from.toEpochMilli(), clock.now().toEpochMilli())

    private fun dayOffset(): Long = StudyDay.epochDayOffsetMillis(clock.now(), clock.zone())
}
