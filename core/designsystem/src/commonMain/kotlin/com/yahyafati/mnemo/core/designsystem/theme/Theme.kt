package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities

internal val LocalMnemoTypography = staticCompositionLocalOf { DefaultMnemoTypography }
internal val LocalMnemoFonts = staticCompositionLocalOf { MnemoFonts.System }
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
        dynamicColor && LocalPlatformCapabilities.current.dynamicColor -> platformDynamicColorScheme(darkTheme)

        darkTheme -> MnemoDarkColorScheme
        else -> MnemoLightColorScheme
    }

    val fonts = rememberMnemoFonts()
    val typography = remember(fonts) { mnemoTypography(fonts) }
    val materialTypography = remember(fonts) { mnemoMaterialTypography(fonts) }

    CompositionLocalProvider(
        LocalMnemoFonts provides fonts,
        LocalMnemoTypography provides typography,
        LocalMnemoSpacing provides MnemoSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
            shapes = MnemoShapes,
            content = content,
        )
    }
}

/**
 * The wallpaper's colors (Material You), for a platform that has them: only called where
 * [PlatformCapabilities.dynamicColor] is on. Elsewhere it returns the brand palette.
 */
@Composable
internal expect fun platformDynamicColorScheme(darkTheme: Boolean): ColorScheme

/** Mnemo tokens that Material 3 has no slot for. Colors, shapes and M3 type stay on [MaterialTheme]. */
object MnemoTheme {
    val typography: MnemoTypography
        @Composable @ReadOnlyComposable
        get() = LocalMnemoTypography.current

    val spacing: MnemoSpacing
        @Composable @ReadOnlyComposable
        get() = LocalMnemoSpacing.current

    /** The bundled font families, for text that sets its own `fontFamily` (code, monospace digits). */
    val fonts: MnemoFonts
        @Composable @ReadOnlyComposable
        get() = LocalMnemoFonts.current
}
