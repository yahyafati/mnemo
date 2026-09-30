package com.yahyafati.mnemo.core.ui.format.android

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun androidIs24HourFormat(): Boolean = DateFormat.is24HourFormat(LocalContext.current)
