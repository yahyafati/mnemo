package com.yahyafati.mnemo.desktop

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalFormatTest {
    @Test
    fun `formats each range with its own unit`() {
        assertEquals("<1m", formatInterval(Duration.ofSeconds(30)))
        assertEquals("10m", formatInterval(Duration.ofMinutes(10)))
        assertEquals("59m", formatInterval(Duration.ofMinutes(59)))
        assertEquals("5h", formatInterval(Duration.ofHours(5)))
        assertEquals("3d", formatInterval(Duration.ofDays(3)))
        assertEquals("29d", formatInterval(Duration.ofDays(29)))
        assertEquals("1.5mo", formatInterval(Duration.ofDays(46)))
        assertEquals("2.0y", formatInterval(Duration.ofDays(730)))
    }
}
