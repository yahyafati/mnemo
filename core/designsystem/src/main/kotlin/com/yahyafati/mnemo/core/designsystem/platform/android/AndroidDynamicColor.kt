package com.yahyafati.mnemo.core.designsystem.platform.android

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The wallpaper's Material You colors. Only call it where
 * [androidPlatformCapabilities][com.yahyafati.mnemo.core.designsystem.platform.android.androidPlatformCapabilities]
 * says `dynamicColor`, which is Android 12 and up: the theme checks that, and lint can't see it.
 */
@SuppressLint("NewApi")
@Composable
fun androidDynamicColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
