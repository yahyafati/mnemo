package com.yahyafati.mnemo.core.ui.format

import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class IntervalFormatTest {
    @Test
    fun formatsEachUnit() {
        assertEquals("<1m", formatInterval(Duration.ofSeconds(30)))
        assertEquals("6m", formatInterval(Duration.ofSeconds(330)))
        assertEquals("10m", formatInterval(Duration.ofMinutes(10)))
        assertEquals("12h", formatInterval(Duration.ofHours(12)))
        assertEquals("2d", formatInterval(Duration.ofDays(2)))
        assertEquals("1.5mo", formatInterval(Duration.ofDays(46)))
        assertEquals("2mo", formatInterval(Duration.ofDays(61)))
        assertEquals("1.4y", formatInterval(Duration.ofDays(498)))
    }
}
