package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.android.androidDynamicColorScheme

internal val LocalMnemoTypography = staticCompositionLocalOf { DefaultMnemoTypography }
internal val LocalMnemoSpacing = staticCompositionLocalOf { MnemoSpacing() }

/**
 * Mnemo's theme. The brand palette from the mockups is the default; Material You dynamic color
 * is opt-in, and falls back to the brand palette where the platform has none ([PlatformCapabilities.dynamicColor]).
 */
@Composable
fun MnemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && LocalPlatformCapabilities.current.dynamicColor -> androidDynamicColorScheme(darkTheme)

        darkTheme -> MnemoDarkColorScheme
        else -> MnemoLightColorScheme
    }

    CompositionLocalProvider(
        LocalMnemoTypography provides DefaultMnemoTypography,
        LocalMnemoSpacing provides MnemoSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MnemoMaterialTypography,
            shapes = MnemoShapes,
            content = content,
        )
    }
}

/** Mnemo tokens that Material 3 has no slot for. Colors, shapes and M3 type stay on [MaterialTheme]. */
object MnemoTheme {
    val typography: MnemoTypography
        @Composable @ReadOnlyComposable
        get() = LocalMnemoTypography.current

    val spacing: MnemoSpacing
        @Composable @ReadOnlyComposable
        get() = LocalMnemoSpacing.current
}
