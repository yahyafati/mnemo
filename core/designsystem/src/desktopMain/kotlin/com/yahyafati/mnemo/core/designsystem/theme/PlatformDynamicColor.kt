package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

// A computer has no wallpaper palette; the capability is off, so this is not reached.
@Composable
internal actual fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) MnemoDarkColorScheme else MnemoLightColorScheme
