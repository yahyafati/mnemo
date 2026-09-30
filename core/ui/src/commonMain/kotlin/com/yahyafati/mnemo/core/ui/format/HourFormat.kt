package com.yahyafati.mnemo.core.ui.format

import androidx.compose.runtime.Composable

/**
 * Whether the user reads clock times as 24-hour (14:30) rather than 12-hour (2:30 PM), by the
 * system's setting where there is one.
 */
@Composable
expect fun rememberIs24HourFormat(): Boolean
