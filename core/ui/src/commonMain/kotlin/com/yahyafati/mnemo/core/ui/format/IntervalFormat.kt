package com.yahyafati.mnemo.core.ui.format

import java.time.Duration
import java.util.Locale

/**
 * Compact interval for the rating buttons: "<1m", "10m", "12h", "3d", "1.5mo", "2.1y". Units are
 * the same short symbols in every language, as in Anki.
 */
fun formatInterval(interval: Duration): String {
    val seconds = interval.seconds
    val days = seconds / 86_400.0
    return when {
        seconds < 60 -> "<1m"
        seconds < 3_600 -> "${Math.round(seconds / 60.0)}m"
        seconds < 86_400 -> "${Math.round(seconds / 3_600.0)}h"
        days < 30 -> "${Math.round(days)}d"
        days < 365 -> "${oneDecimal(days / DAYS_PER_MONTH)}mo"
        else -> "${oneDecimal(days / 365.0)}y"
    }
}

private const val DAYS_PER_MONTH = 30.44

private fun oneDecimal(value: Double): String =
    String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
