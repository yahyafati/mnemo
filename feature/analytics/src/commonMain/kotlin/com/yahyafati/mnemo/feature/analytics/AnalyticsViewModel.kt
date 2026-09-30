package com.yahyafati.mnemo.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.domain.ComputeRetentionStatsUseCase
import com.yahyafati.mnemo.core.model.RetentionStats
import com.yahyafati.mnemo.core.model.markdown.Markdown
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

class AnalyticsViewModel(
    computeRetentionStats: ComputeRetentionStatsUseCase,
    private val clock: Clock,
) : ViewModel() {
    /** Weeks start where the user's locale starts them. */
    internal val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    val uiState: StateFlow<AnalyticsUiState> = computeRetentionStats()
        .map { stats -> if (stats.hasReviews) ready(stats) else AnalyticsUiState.Empty }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState.Loading)

    private fun ready(stats: RetentionStats): AnalyticsUiState.Ready {
        val today = StudyDay.date(clock.now(), clock.zone())
        val reviews = stats.activity.associate { it.date to it.reviews }
        val start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(CALENDAR_WEEKS - 1L)
        val calendar = (0 until CALENDAR_WEEKS).map { week ->
            (0 until 7).map { day ->
                val date = start.plusDays(week * 7L + day)
                CalendarDay(date, if (date.isAfter(today)) null else reviews[date] ?: 0)
            }
        }
        return AnalyticsUiState.Ready(
            stats = stats,
            today = today,
            calendar = calendar,
            firstDayOfWeek = firstDayOfWeek,
            hardestCards = stats.hardestCards.map { card ->
                HardCard(
                    cardId = card.card.id,
                    noteId = card.note.id,
                    front = Markdown.plainText(card.sides.front),
                    deckName = card.deckName,
                    lapses = card.card.lapses,
                )
            },
        )
    }

    private companion object {
        const val CALENDAR_WEEKS = 5
    }
}

