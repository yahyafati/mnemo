package com.yahyafati.mnemo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from the mockups. Read through `MnemoTheme.spacing`. */
@Immutable
data class MnemoSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 40.dp,
    /** Horizontal screen margin on phones (margin-mobile). */
    val screenMargin: Dp = 20.dp,
    /** Gap between grid columns on phones (gutter-mobile). */
    val gutter: Dp = 16.dp,
)
