package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.resources.Res
import com.yahyafati.mnemo.core.designsystem.resources.hanken_grotesk
import com.yahyafati.mnemo.core.designsystem.resources.jetbrains_mono
import com.yahyafati.mnemo.core.designsystem.resources.newsreader
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * The three bundled fonts (`composeResources/font`), so text renders the same offline on every
 * platform. `Font(Res.font…)` is composable, which is why the families are built inside the theme
 * ([rememberMnemoFonts]) and read through `MnemoTheme.fonts`.
 */
@Immutable
data class MnemoFonts(
    /** Display, headlines and the study prompt. */
    val newsreader: FontFamily,
    /** Body text and labels. */
    val hankenGrotesk: FontFamily,
    /** Metrics, counters and code. */
    val jetBrainsMono: FontFamily,
) {
    companion object {
        /** The platform's own serif, sans and monospace: what text uses outside [MnemoTheme]. */
        val System = MnemoFonts(FontFamily.Serif, FontFamily.SansSerif, FontFamily.Monospace)
    }
}

@Composable
private fun variableFamily(resource: FontResource, vararg weights: FontWeight): FontFamily {
    val fonts = weights.map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
    return remember(fonts) { FontFamily(fonts) }
}

/** Loads the bundled fonts. Fonts load asynchronously: until they are read, text uses a fallback. */
@Composable
internal fun rememberMnemoFonts(): MnemoFonts {
    val newsreader = variableFamily(Res.font.newsreader, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
    val hanken = variableFamily(
        Res.font.hanken_grotesk,
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    )
    val mono = variableFamily(
        Res.font.jetbrains_mono,
        FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
    )
    return remember(newsreader, hanken, mono) { MnemoFonts(newsreader, hanken, mono) }
}

// Type scale from the mockups (ARCHITECTURE §7). Comments name the mockup token.
internal fun mnemoMaterialTypography(fonts: MnemoFonts) = Typography(
    // display-hero
    displayLarge = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em,
    ),
    // display-hero-mobile
    displayMedium = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em,
    ),
    displaySmall = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em,
    ),
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em,
    ),
    // headline-md
    headlineMedium = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
    ),
    // headline-sm
    headlineSmall = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 28.sp,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // body-sm
    bodySmall = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // label-md
    labelLarge = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em,
    ),
    // label-sm
    labelMedium = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = fonts.hankenGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
    ),
)

/** Mockup text styles with no Material 3 equivalent. Read through `MnemoTheme.typography`. */
@Immutable
data class MnemoTypography(
    /** study-prompt: the question on a study card. */
    val studyPrompt: TextStyle,
    /** study-prompt-mobile: [studyPrompt] on compact widths. */
    val studyPromptCompact: TextStyle,
    /** metric-mono-lg: large counters and KPI values. */
    val metricLg: TextStyle,
    /** metric-mono-sm: small counters, stat labels, timestamps. */
    val metricSm: TextStyle,
)

internal fun mnemoTypography(fonts: MnemoFonts) = MnemoTypography(
    studyPrompt = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 38.sp,
    ),
    studyPromptCompact = TextStyle(
        fontFamily = fonts.newsreader, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    metricLg = TextStyle(
        fontFamily = fonts.jetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em,
    ),
    metricSm = TextStyle(
        fontFamily = fonts.jetBrainsMono, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
    ),
)

/** The type styles with the platform's fonts: the default outside [MnemoTheme]. */
internal val DefaultMnemoTypography = mnemoTypography(MnemoFonts.System)
