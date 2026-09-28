package com.yahyafati.mnemo.feature.settings

import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StepsFormatTest {
    @Test
    fun parsesUnitsAndBareMinutes() {
        assertEquals(
            listOf(Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofDays(2), Duration.ofMinutes(5)),
            StepsFormat.parse("30s 10m, 1h 2d 5"),
        )
        assertEquals(emptyList(), StepsFormat.parse("  "))
    }

    @Test
    fun rejectsNonsense() {
        assertNull(StepsFormat.parse("10x"))
        assertNull(StepsFormat.parse("0m"))
        assertNull(StepsFormat.parse("soon"))
    }

    @Test
    fun formatRoundTrips() {
        val steps = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofDays(1), Duration.ofSeconds(45))
        assertEquals("1m 10m 1h 1d 45s", StepsFormat.format(steps))
        assertEquals(steps, StepsFormat.parse(StepsFormat.format(steps)))
    }
}
