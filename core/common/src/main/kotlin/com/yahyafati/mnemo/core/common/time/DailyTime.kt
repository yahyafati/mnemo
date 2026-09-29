package com.yahyafati.mnemo.core.common.time

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Times of day that repeat every day, such as the study reminder. */
object DailyTime {
    /**
     * The next moment strictly after [now] when the local clock in [zone] shows [time]. On a day
     * when [time] doesn't exist (skipped by a DST change) it is the first moment after the gap.
     */
    fun nextAfter(now: Instant, zone: ZoneId, time: LocalTime): Instant {
        val today = now.atZone(zone).toLocalDate()
        val candidate = today.atTime(time).atZone(zone).toInstant()
        return if (candidate.isAfter(now)) candidate else today.plusDays(1).atTime(time).atZone(zone).toInstant()
    }
}
