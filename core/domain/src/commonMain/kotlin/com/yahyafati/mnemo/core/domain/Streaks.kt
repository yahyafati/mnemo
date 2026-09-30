package com.yahyafati.mnemo.core.domain

import java.time.LocalDate

/**
 * Consecutive study days ending today, or ending yesterday if nothing has been studied yet today
 * (the streak is still alive until the day ends). [dates] are newest first.
 */
internal fun currentStreak(dates: List<LocalDate>, today: LocalDate): Int {
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
