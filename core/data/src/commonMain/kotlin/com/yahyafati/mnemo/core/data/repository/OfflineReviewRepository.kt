package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.ReviewLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate

internal class OfflineReviewRepository(
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
) : ReviewRepository {
    override suspend fun recordAnswer(card: Card, log: ReviewLog) = transaction {
        cardDao.update(card.toEntity())
        reviewLogDao.insert(log.toEntity())
    }

    override suspend fun undoAnswer(previous: Card, logId: String) = transaction {
        cardDao.update(previous.toEntity())
        reviewLogDao.softDelete(logId, clock.now().toEpochMilli())
    }

    override suspend fun getTodayCounts(): DailyReviewCounts =
        reviewLogDao.getCountsSince(todayStart()).toModel()

    override fun observeTodayCounts(): Flow<DailyReviewCounts> =
        reviewLogDao.observeCountsSince(todayStart()).map { it.toModel() }

    override fun observeStudyDates(): Flow<List<LocalDate>> {
        val offset = StudyDay.epochDayOffsetMillis(clock.now(), clock.zone())
        return reviewLogDao.observeReviewDays(offset).map { days -> days.map(LocalDate::ofEpochDay) }
    }

    override fun observeAverageAnswerMs(): Flow<Double?> =
        reviewLogDao.observeAverageDurationMs(clock.now().minus(Duration.ofDays(30)).toEpochMilli())

    private fun todayStart(): Long = StudyDay.start(clock.now(), clock.zone()).toEpochMilli()
}
