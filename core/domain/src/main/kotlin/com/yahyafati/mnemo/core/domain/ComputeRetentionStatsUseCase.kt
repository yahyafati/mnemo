package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.StatsRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.domain.GetRetentionOverviewUseCase.Companion.RETENTION_WINDOW_DAYS
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DeckRetention
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.RecallTotal
import com.yahyafati.mnemo.core.model.RetentionStats
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything on the Analytics screen (ROADMAP Phase 5, ADR 0007): true retention, review volume,
 * average stability, time saved, the forgetting curve, activity and streak, per-deck maturity and
 * recall, the due forecast and the hardest cards. The database aggregates; this combines the
 * aggregates with the user's FSRS model.
 */
class ComputeRetentionStatsUseCase @Inject constructor(
    private val statsRepository: StatsRepository,
    private val reviewRepository: ReviewRepository,
    private val deckRepository: DeckRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<RetentionStats> {
        val zone = clock.zone()
        val today = StudyDay.date(clock.now(), zone)
        val windowStart = StudyDay.start(today.minusDays(RETENTION_WINDOW_DAYS - 1), zone)
        val previousStart = StudyDay.start(today.minusDays(2 * RETENTION_WINDOW_DAYS - 1), zone)
        val activityStart = today.minusDays(ACTIVITY_DAYS - 1)

        val retention = combine(
            statsRepository.observePassCounts(windowStart),
            statsRepository.observePassCounts(previousStart, windowStart),
            statsRepository.observeDailyReviewBaseline(windowStart),
            reviewRepository.observeAverageAnswerMs(),
            ::RetentionInputs,
        )
        val activity = combine(
            statsRepository.observeDailyReviews(activityStart),
            reviewRepository.observeStudyDates(),
            statsRepository.observeDueForecast(FORECAST_DAYS),
            statsRepository.observeMostLapsed(HARDEST_MIN_LAPSES, HARDEST_LIMIT),
            ::ActivityInputs,
        )
        val decks = combine(
            deckRepository.observeDecks(),
            statsRepository.observeDeckMaturity(),
            statsRepository.observeRetrievability(),
            ::DeckInputs,
        )
        return combine(settingsRepository.settings, retention, activity, decks) { settings, r, a, d ->
            build(settings, today, r, a, d)
        }
    }

    private fun build(settings: UserSettings, today: LocalDate, r: RetentionInputs, a: ActivityInputs, d: DeckInputs): RetentionStats {
        val fsrs = RetentionMath.fsrs(settings)
        val byDay = a.dailyReviews.associate { it.date to it.reviews }
        val activity = (ACTIVITY_DAYS - 1 downTo 0).map { back ->
            today.minusDays(back).let { DailyReviews(it, byDay[it] ?: 0) }
        }
        val reviewsInWindow = activity.takeLast(RETENTION_WINDOW_DAYS.toInt()).sumOf { it.reviews }
        val averageAnswer = Duration.ofMillis((r.averageAnswerMs ?: DEFAULT_ANSWER_MS).toLong())
        val forecastByDay = a.forecast.associate { it.date to it.cards }
        val reviewCards = d.maturity.sumOf { it.reviewCards }
        return RetentionStats(
            hasReviews = a.studyDates.isNotEmpty(),
            desiredRetention = settings.desiredRetention,
            fsrsWeights = settings.fsrsWeights,
            retention = r.current,
            previousRetention = r.previous,
            reviewsLast30Days = reviewsInWindow,
            averageStability = if (reviewCards == 0) null else d.maturity.sumOf { it.reviewStabilitySum } / reviewCards,
            // Only answers the daily routine would have needed beyond what was actually done.
            timeSaved = averageAnswer.multipliedBy((r.dailyBaseline - reviewsInWindow).coerceAtLeast(0)),
            averageAnswer = averageAnswer,
            forgettingCurve = RetentionMath.forgettingCurve(settings),
            activity = activity,
            streakDays = currentStreak(a.studyDates, today),
            decks = deckRetention(d, RetentionMath.deckRecall(d.buckets, fsrs)),
            forecast = (0 until FORECAST_DAYS).map { ahead ->
                today.plusDays(ahead.toLong()).let { DueForecast(it, forecastByDay[it] ?: 0) }
            },
            hardestCards = a.hardestCards,
        )
    }

    /** Top-level decks with their subdecks' cards rolled in; decks without cards are left out. */
    private fun deckRetention(d: DeckInputs, recall: Map<String, RecallTotal>): List<DeckRetention> {
        val roots = RetentionMath.roots(d.decks)
        val maturity = mutableMapOf<String, DeckMaturity>()
        val rootRecall = mutableMapOf<String, RecallTotal>()
        d.maturity.forEach { deck ->
            val root = roots[deck.deckId] ?: return@forEach
            maturity[root.id] = maturity[root.id]?.plus(deck) ?: deck.copy(deckId = root.id)
        }
        recall.forEach { (deckId, total) ->
            val root = roots[deckId] ?: return@forEach
            rootRecall[root.id] = (rootRecall[root.id] ?: RecallTotal.None) + total
        }
        val names = d.decks.associate { it.id to it.name }
        return maturity.values
            .map { DeckRetention(it.deckId, names.getValue(it.deckId), it, rootRecall[it.deckId]?.average) }
            .sortedWith(compareByDescending<DeckRetention> { it.maturity.studiedCards }.thenBy { it.name.lowercase() })
    }

    private data class RetentionInputs(
        val current: ReviewPassCounts,
        val previous: ReviewPassCounts,
        val dailyBaseline: Long,
        val averageAnswerMs: Double?,
    )

    private data class ActivityInputs(
        val dailyReviews: List<DailyReviews>,
        val studyDates: List<LocalDate>,
        val forecast: List<DueForecast>,
        val hardestCards: List<StudyCard>,
    )

    private data class DeckInputs(
        val decks: List<Deck>,
        val maturity: List<DeckMaturity>,
        val buckets: List<RetrievabilityBucket>,
    )

    private companion object {
        /** Six weeks, enough for a five-week calendar that starts on any weekday. */
        const val ACTIVITY_DAYS = 42L
        const val FORECAST_DAYS = 14
        const val HARDEST_MIN_LAPSES = 2
        const val HARDEST_LIMIT = 5
        const val DEFAULT_ANSWER_MS = 10_000.0
    }
}
