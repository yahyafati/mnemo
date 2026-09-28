package com.yahyafati.mnemo.feature.settings

import java.time.Duration

/**
 * Learning steps as the user types them: "1m 10m", "30s 5m 1h", "1d". A bare number is minutes,
 * as in Anki. Returns null if any step can't be read or isn't positive.
 */
internal object StepsFormat {
    private val STEP = Regex("""(\d+(?:\.\d+)?)\s*([smhd]?)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Duration>? {
        val tokens = text.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        return tokens.map { token ->
            val match = STEP.matchEntire(token) ?: return null
            val (amount, unit) = match.destructured
            val seconds = amount.toDouble() * when (unit.lowercase()) {
                "s" -> 1
                "h" -> 3_600
                "d" -> 86_400
                else -> 60
            }
            if (seconds < 1) return null
            Duration.ofSeconds(seconds.toLong())
        }
    }

    fun format(steps: List<Duration>): String = steps.joinToString(" ") { step ->
        val s = step.seconds
        when {
            s % 86_400 == 0L -> "${s / 86_400}d"
            s % 3_600 == 0L -> "${s / 3_600}h"
            s % 60 == 0L -> "${s / 60}m"
            else -> "${s}s"
        }
    }
}
