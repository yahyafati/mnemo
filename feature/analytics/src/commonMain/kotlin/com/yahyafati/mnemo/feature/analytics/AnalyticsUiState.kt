package com.yahyafati.mnemo.feature.analytics

import com.yahyafati.mnemo.core.model.RetentionStats
import java.time.DayOfWeek
import java.time.LocalDate

sealed interface AnalyticsUiState {
    data object Loading : AnalyticsUiState

    /** Nothing has been reviewed yet. */
    data object Empty : AnalyticsUiState

    data class Ready(
        val stats: RetentionStats,
        val today: LocalDate,
        /** Five weeks of activity, one row per week starting on [firstDayOfWeek], this week last. */
        val calendar: List<List<CalendarDay>>,
        val firstDayOfWeek: DayOfWeek,
        val hardestCards: List<HardCard>,
    ) : AnalyticsUiState {
        /** Days so far in [calendar] with at least one answer, and days so far. */
        val activeDays: Int get() = calendar.flatten().count { (it.reviews ?: 0) > 0 }
        val pastDays: Int get() = calendar.flatten().count { it.reviews != null }
    }
}

/** A calendar cell: [reviews] is null for days after today. */
data class CalendarDay(val date: LocalDate, val reviews: Int?)

/** A card forgotten often, as the Hardest cards list shows it. */
data class HardCard(
    val cardId: String,
    val noteId: String,
    val front: String,
    val deckName: String,
    val lapses: Int,
) {
    val isLeech: Boolean get() = lapses >= RetentionStats.LEECH_LAPSES
}
