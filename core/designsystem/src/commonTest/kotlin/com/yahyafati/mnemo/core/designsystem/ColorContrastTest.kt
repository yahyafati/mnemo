package com.yahyafati.mnemo.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.yahyafati.mnemo.core.designsystem.theme.MnemoDarkColorScheme
import com.yahyafati.mnemo.core.designsystem.theme.MnemoLightColorScheme
import org.junit.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA contrast for the brand palette in both themes: 4.5:1 for text roles on the
 * backgrounds they are used on, 3:1 for outlines and large text (ROADMAP Phase 6, accessibility).
 * Dynamic color comes from the system and is Android's responsibility.
 */
class ColorContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    private fun ColorScheme.textPairs() = listOf(
        "onPrimary/primary" to (onPrimary to primary),
        "onPrimaryContainer/primaryContainer" to (onPrimaryContainer to primaryContainer),
        "onSecondary/secondary" to (onSecondary to secondary),
        "onSecondaryContainer/secondaryContainer" to (onSecondaryContainer to secondaryContainer),
        "onTertiary/tertiary" to (onTertiary to tertiary),
        "onTertiaryContainer/tertiaryContainer" to (onTertiaryContainer to tertiaryContainer),
        "onError/error" to (onError to error),
        "onErrorContainer/errorContainer" to (onErrorContainer to errorContainer),
        "onSurface/surface" to (onSurface to surface),
        "onSurfaceVariant/surface" to (onSurfaceVariant to surface),
        "onSurfaceVariant/surfaceContainerHighest" to (onSurfaceVariant to surfaceContainerHighest),
        // Accent text: rating labels, links, cloze answers, counts.
        "primary/surfaceContainerLowest" to (primary to surfaceContainerLowest),
        "primary/surfaceContainerLow" to (primary to surfaceContainerLow),
        "secondary/surfaceContainerLowest" to (secondary to surfaceContainerLowest),
        "tertiary/surfaceContainerLowest" to (tertiary to surfaceContainerLowest),
        "error/surfaceContainerLowest" to (error to surfaceContainerLowest),
        "error/surfaceContainer" to (error to surfaceContainer),
    )

    private fun check(name: String, scheme: ColorScheme) {
        val failures = scheme.textPairs().mapNotNull { (label, pair) ->
            val r = ratio(pair.first, pair.second)
            "$label = ${"%.2f".format(r)}".takeIf { r < TEXT }
        } + listOf("outline/surface" to (scheme.outline to scheme.surface)).mapNotNull { (label, pair) ->
            val r = ratio(pair.first, pair.second)
            "$label = ${"%.2f".format(r)}".takeIf { r < NON_TEXT }
        }
        assertTrue(failures.isEmpty(), "$name fails WCAG AA: $failures")
    }

    @Test
    fun lightSchemeMeetsAa() = check("light", MnemoLightColorScheme)

    @Test
    fun darkSchemeMeetsAa() = check("dark", MnemoDarkColorScheme)

    private companion object {
        const val TEXT = 4.5
        const val NON_TEXT = 3.0
    }
}
