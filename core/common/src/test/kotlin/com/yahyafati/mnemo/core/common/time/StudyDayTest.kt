package com.yahyafati.mnemo.core.common.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StudyDayTest {
    private val zone = ZoneId.of("Europe/Berlin") // UTC+2 in summer

    @Test
    fun `day rolls over at 4am local time`() {
        val beforeRollover = Instant.parse("2026-07-10T01:30:00Z") // 03:30 local
        val afterRollover = Instant.parse("2026-07-10T02:30:00Z") // 04:30 local
        assertEquals(LocalDate.of(2026, 7, 9), StudyDay.date(beforeRollover, zone))
        assertEquals(LocalDate.of(2026, 7, 10), StudyDay.date(afterRollover, zone))
        assertEquals(Instant.parse("2026-07-10T02:00:00Z"), StudyDay.start(afterRollover, zone))
        assertEquals(Instant.parse("2026-07-11T02:00:00Z"), StudyDay.end(afterRollover, zone))
    }

    @Test
    fun `offset maps millis to study epoch days`() {
        val now = Instant.parse("2026-07-10T02:30:00Z")
        val day = Math.floorDiv(now.toEpochMilli() + StudyDay.epochDayOffsetMillis(now, zone), 86_400_000L)
        assertEquals(StudyDay.date(now, zone).toEpochDay(), day)
    }
}
