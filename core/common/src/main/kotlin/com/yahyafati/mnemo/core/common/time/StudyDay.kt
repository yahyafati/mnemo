package com.yahyafati.mnemo.core.common.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A study day starts at [ROLLOVER_HOUR] local time rather than midnight (as in Anki), so a late
 * session still counts toward the day it started. Daily limits, "due today", and the streak all use
 * these boundaries.
 */
object StudyDay {
    const val ROLLOVER_HOUR = 4L

    /** The study date [instant] falls on. */
    fun date(instant: Instant, zone: ZoneId): LocalDate =
        instant.atZone(zone).minusHours(ROLLOVER_HOUR).toLocalDate()

    /** When the study day containing [now] started. */
    fun start(now: Instant, zone: ZoneId): Instant =
        date(now, zone).atStartOfDay(zone).plusHours(ROLLOVER_HOUR).toInstant()

    /** When the study day containing [now] ends: the next day's start. */
    fun end(now: Instant, zone: ZoneId): Instant =
        date(now, zone).plusDays(1).atStartOfDay(zone).plusHours(ROLLOVER_HOUR).toInstant()

    /**
     * Offset that turns epoch millis into study-day numbers with integer division:
     * `(millis + offset) / 1 day` is [date]'s epoch day. Uses the zone's offset at [now], so it can
     * be off by one hour for reviews on the other side of a DST change; that only matters for
     * reviews within an hour of the rollover.
     */
    fun epochDayOffsetMillis(now: Instant, zone: ZoneId): Long =
        zone.rules.getOffset(now).totalSeconds * 1000L - Duration.ofHours(ROLLOVER_HOUR).toMillis()
}
