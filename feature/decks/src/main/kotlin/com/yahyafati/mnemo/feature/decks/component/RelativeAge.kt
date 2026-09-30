package com.yahyafati.mnemo.feature.decks.component

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * How long ago something happened, as a deck card shows its last review. It follows what Android's
 * `DateUtils.getRelativeTimeSpanString` did, so cards read the same as before, without depending on
 * it: minutes under an hour, hours under a day, then calendar days up to a week, then the date.
 */
internal sealed interface RelativeAge {
    data object JustNow : RelativeAge

    data class MinutesAgo(val minutes: Int) : RelativeAge

    data class HoursAgo(val hours: Int) : RelativeAge

    data class DaysAgo(val days: Int) : RelativeAge

    /** A week or more ago: [date] is in the zone the age was measured in. */
    data class Earlier(val date: LocalDate) : RelativeAge

    companion object {
        private const val WEEK_DAYS = 7L

        /** The age of [time] seen from [now]; a time in the future counts as just now. */
        fun of(time: Instant, now: Instant, zone: ZoneId): RelativeAge {
            val elapsed = Duration.between(time, now)
            return when {
                elapsed < Duration.ofMinutes(1) -> JustNow
                elapsed < Duration.ofHours(1) -> MinutesAgo(elapsed.toMinutes().toInt())
                elapsed < Duration.ofDays(1) -> HoursAgo(elapsed.toHours().toInt())
                else -> {
                    val then = time.atZone(zone).toLocalDate()
                    val days = ChronoUnit.DAYS.between(then, now.atZone(zone).toLocalDate())
                    if (days < WEEK_DAYS) DaysAgo(days.toInt()) else Earlier(then)
                }
            }
        }

        /** "Sep 3", or "Sep 3, 2025" when it isn't this year, in [locale]. */
        fun formatDate(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
            val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
            return DateTimeFormatter.ofPattern(pattern, locale).format(date)
        }
    }
}
