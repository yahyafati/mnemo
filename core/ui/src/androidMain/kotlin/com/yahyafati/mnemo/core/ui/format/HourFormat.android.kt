package com.yahyafati.mnemo.core.ui.format

import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.ui.format.android.androidIs24HourFormat

@Composable
actual fun rememberIs24HourFormat(): Boolean = androidIs24HourFormat()
