package com.yahyafati.mnemo.core.model

/** What has been studied so far today, for the daily limits. */
data class DailyReviewCounts(
    /** New cards seen for the first time today. */
    val newStudied: Int,
    /** Answers to cards that were in review (not learning) state. */
    val reviewsDone: Int,
    /** Every answer today, including learning steps. */
    val total: Int,
) {
    companion object {
        val None = DailyReviewCounts(0, 0, 0)
    }
}
