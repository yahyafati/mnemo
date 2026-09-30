package com.yahyafati.mnemo.desktop

import java.time.Duration
import java.util.Locale

/**
 * A short label for the time until a card comes back: "10m", "5h", "3d", "1.5mo", "2.0y". The
 * rating buttons of the study screen use the same shape.
 */
fun formatInterval(interval: Duration): String {
    val minutes = interval.toMinutes()
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        interval.toHours() < 24 -> "${interval.toHours()}h"
        interval.toDays() < 30 -> "${interval.toDays()}d"
        interval.toDays() < 365 -> String.format(Locale.ROOT, "%.1fmo", interval.toDays() / 30.4375)
        else -> String.format(Locale.ROOT, "%.1fy", interval.toDays() / 365.25)
    }
}
