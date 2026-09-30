package com.yahyafati.mnemo.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.chrono.IsoChronology
import java.util.Locale

/** The locale's short time pattern tells: 24-hour patterns use `H`/`k`, 12-hour ones `h`/`K`. */
@Composable
actual fun rememberIs24HourFormat(): Boolean = remember { is24HourLocale(Locale.getDefault()) }

internal fun is24HourLocale(locale: Locale): Boolean {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
    // Text in single quotes is literal.
    return pattern.split('\'').filterIndexed { i, _ -> i % 2 == 0 }.any { 'H' in it || 'k' in it }
}
