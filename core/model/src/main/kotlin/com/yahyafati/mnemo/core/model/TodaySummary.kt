package com.yahyafati.mnemo.core.model

/** Today's queue across all decks, after daily limits, for the Decks header. */
data class TodaySummary(
    val dueCount: Int,
    val newCount: Int,
    val learningCount: Int,
    val estimatedMinutes: Int,
    val streakDays: Int,
    val reviewedToday: Int,
    val totalCards: Int,
) {
    val totalToStudy: Int get() = dueCount + newCount + learningCount

    companion object {
        val Empty = TodaySummary(0, 0, 0, 0, 0, 0, 0)
    }
}
