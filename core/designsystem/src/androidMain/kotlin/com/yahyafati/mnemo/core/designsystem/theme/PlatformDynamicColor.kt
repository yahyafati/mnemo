package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.designsystem.platform.android.androidDynamicColorScheme

@Composable
internal actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme = androidDynamicColorScheme(darkTheme)
