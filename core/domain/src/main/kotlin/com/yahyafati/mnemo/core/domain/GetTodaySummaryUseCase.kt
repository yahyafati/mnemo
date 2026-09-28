package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.DeckSummary
import com.yahyafati.mnemo.core.model.TodaySummary
import com.yahyafati.mnemo.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.ceil

/** Today's due / new / learning counts across all decks, after daily limits, plus streak and time estimate. */
class GetTodaySummaryUseCase @Inject constructor(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val reviewRepository: ReviewRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) {
    operator fun invoke(): Flow<TodaySummary> = combine(
        settingsRepository.settings,
        deckRepository.observeDeckSummaries(),
        reviewRepository.observeTodayCounts(),
        reviewRepository.observeStudyDates(),
        combine(reviewRepository.observeAverageAnswerMs(), cardRepository.observeTotalCardCount(), ::Pair),
    ) { settings, decks, today, studyDates, (averageMs, totalCards) ->
        summarize(settings, decks, today, studyDates, averageMs, totalCards)
    }

    private fun summarize(
        settings: UserSettings,
        decks: List<DeckSummary>,
        today: DailyReviewCounts,
        studyDates: List<LocalDate>,
        averageMs: Double?,
        totalCards: Int,
    ): TodaySummary {
        val learning = decks.sumOf { it.learningCount }
        val reviewDue = decks.sumOf { it.dueCount - it.learningCount }
            .coerceAtMost((settings.reviewsPerDay - today.reviewsDone).coerceAtLeast(0))
        val new = decks.sumOf { it.newCount }
            .coerceAtMost((settings.newCardsPerDay - today.newStudied).coerceAtLeast(0))
        // New cards go through a few learning steps before they graduate.
        val answers = reviewDue + learning + new * NEW_CARD_ANSWERS
        val minutes = ceil(answers * (averageMs ?: DEFAULT_ANSWER_MS) / 60_000.0).toInt()
        return TodaySummary(
            dueCount = reviewDue,
            newCount = new,
            learningCount = learning,
            estimatedMinutes = minutes,
            streakDays = streak(studyDates, StudyDay.date(clock.now(), clock.zone())),
            reviewedToday = today.total,
            totalCards = totalCards,
        )
    }

    private companion object {
        const val DEFAULT_ANSWER_MS = 10_000.0
        const val NEW_CARD_ANSWERS = 3

        /**
         * Consecutive study days ending today, or ending yesterday if nothing has been studied yet
         * today (the streak is still alive until the day ends). [dates] are newest first.
         */
        fun streak(dates: List<LocalDate>, today: LocalDate): Int {
            val latest = dates.firstOrNull() ?: return 0
            if (latest.isBefore(today.minusDays(1))) return 0
            var expected = latest
            var count = 0
            for (date in dates) {
                if (date != expected) break
                count++
                expected = expected.minusDays(1)
            }
            return count
        }
    }
}
