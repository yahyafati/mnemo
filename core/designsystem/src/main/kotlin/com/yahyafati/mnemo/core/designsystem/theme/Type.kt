package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.R

// Bundled variable fonts (res/font), so text renders the same offline.
private fun variableFamily(resId: Int, vararg weights: FontWeight) = FontFamily(
    weights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/** Display, headlines and the study prompt. */
val Newsreader = variableFamily(
    R.font.newsreader,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold,
)

/** Body text and labels. */
val HankenGrotesk = variableFamily(
    R.font.hanken_grotesk,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

/** Metrics and counters. */
val JetBrainsMono = variableFamily(
    R.font.jetbrains_mono,
    FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

// Type scale from the mockups (ARCHITECTURE §7). Comments name the mockup token.
internal val MnemoMaterialTypography = Typography(
    // display-hero
    displayLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = (-0.02).em,
    ),
    // display-hero-mobile
    displayMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em,
    ),
    displaySmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em,
    ),
    // headline-lg
    headlineLarge = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.015).em,
    ),
    // headline-md
    headlineMedium = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em,
    ),
    // headline-sm
    headlineSmall = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // body-lg
    bodyLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 28.sp,
    ),
    // body-md
    bodyMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // body-sm
    bodySmall = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    // label-md
    labelLarge = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.01.em,
    ),
    // label-sm
    labelMedium = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium,
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

internal val DefaultMnemoTypography = MnemoTypography(
    studyPrompt = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 38.sp,
    ),
    studyPromptCompact = TextStyle(
        fontFamily = Newsreader, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    metricLg = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em,
    ),
    metricSm = TextStyle(
        fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em,
    ),
)
