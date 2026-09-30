package com.yahyafati.mnemo.feature.decks.component

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Test
import kotlin.test.assertEquals

class RelativeAgeTest {
    private val now = Instant.parse("2026-10-22T09:00:00Z")

    private fun ago(duration: Duration, zone: ZoneId = ZoneOffset.UTC) = RelativeAge.of(now - duration, now, zone)

    @Test
    fun underAMinuteIsJustNow() {
        assertEquals(RelativeAge.JustNow, ago(Duration.ZERO))
        assertEquals(RelativeAge.JustNow, ago(Duration.ofSeconds(59)))
    }

    @Test
    fun aTimeInTheFutureIsJustNow() {
        assertEquals(RelativeAge.JustNow, RelativeAge.of(now + Duration.ofHours(3), now, ZoneOffset.UTC))
    }

    @Test
    fun minutesRunUpToAnHour() {
        assertEquals(RelativeAge.MinutesAgo(1), ago(Duration.ofMinutes(1)))
        assertEquals(RelativeAge.MinutesAgo(59), ago(Duration.ofMinutes(59).plusSeconds(59)))
    }

    @Test
    fun hoursRunUpToADay() {
        assertEquals(RelativeAge.HoursAgo(1), ago(Duration.ofMinutes(60)))
        assertEquals(RelativeAge.HoursAgo(2), ago(Duration.ofHours(2)))
        assertEquals(RelativeAge.HoursAgo(23), ago(Duration.ofHours(23).plusMinutes(59)))
    }

    @Test
    fun daysAreCalendarDaysInTheZone() {
        assertEquals(RelativeAge.DaysAgo(1), ago(Duration.ofHours(24)))
        assertEquals(RelativeAge.DaysAgo(6), ago(Duration.ofDays(6)))
        // 42 hours ago: two calendar days back in UTC, one in Auckland (UTC+13 in October).
        val then = Instant.parse("2026-10-20T15:00:00Z")
        assertEquals(RelativeAge.DaysAgo(2), RelativeAge.of(then, now, ZoneOffset.UTC))
        assertEquals(RelativeAge.DaysAgo(1), RelativeAge.of(then, now, ZoneId.of("Pacific/Auckland")))
    }

    @Test
    fun aWeekOrMoreIsADate() {
        assertEquals(RelativeAge.Earlier(LocalDate.of(2026, 10, 15)), ago(Duration.ofDays(7)))
        assertEquals(RelativeAge.Earlier(LocalDate.of(2025, 1, 2)), RelativeAge.of(Instant.parse("2025-01-02T05:00:00Z"), now, ZoneOffset.UTC))
    }

    @Test
    fun datesLeaveOutTheCurrentYear() {
        val today = LocalDate.of(2026, 10, 22)
        assertEquals("Sep 3", RelativeAge.formatDate(LocalDate.of(2026, 9, 3), today, Locale.US))
        assertEquals("Sep 3, 2025", RelativeAge.formatDate(LocalDate.of(2025, 9, 3), today, Locale.US))
    }
}
