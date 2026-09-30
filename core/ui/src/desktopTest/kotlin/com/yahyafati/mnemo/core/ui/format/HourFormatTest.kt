package com.yahyafati.mnemo.core.ui.format

import org.junit.Test
import java.util.Locale
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HourFormatTest {
    @Test
    fun americansReadTwelveHoursAndGermansTwentyFour() {
        assertFalse(is24HourLocale(Locale.US))
        assertTrue(is24HourLocale(Locale.GERMANY))
        assertTrue(is24HourLocale(Locale.FRANCE))
    }
}
