package com.yahyafati.mnemo.core.common.time

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DailyTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun laterTodayOrTomorrow() {
        val morning = Instant.parse("2026-03-10T06:00:00Z") // 07:00 in Berlin
        assertEquals(Instant.parse("2026-03-10T18:00:00Z"), DailyTime.nextAfter(morning, zone, LocalTime.of(19, 0)))
        assertEquals(Instant.parse("2026-03-11T05:30:00Z"), DailyTime.nextAfter(morning, zone, LocalTime.of(6, 30)))
        // Exactly at the time: the next one is tomorrow.
        assertEquals(Instant.parse("2026-03-11T06:00:00Z"), DailyTime.nextAfter(morning, zone, LocalTime.of(7, 0)))
    }

    @Test
    fun acrossDaylightSavingTime() {
        // Clocks go forward on 29 March 2026 in Berlin: 19:00 is then UTC+2.
        val before = Instant.parse("2026-03-28T19:00:00Z")
        assertEquals(Instant.parse("2026-03-29T17:00:00Z"), DailyTime.nextAfter(before, zone, LocalTime.of(19, 0)))
    }
}
