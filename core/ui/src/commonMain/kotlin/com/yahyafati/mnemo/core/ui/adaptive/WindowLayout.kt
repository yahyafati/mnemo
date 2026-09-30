package com.yahyafati.mnemo.core.ui.adaptive

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass

/**
 * What the window allows, from `material3-adaptive`: [wide] on tablets, unfolded foldables and
 * landscape phones (width class medium or larger); [tabletop] when a foldable stands half-open
 * with a horizontal hinge, so content can sit above the fold and controls below it.
 */
@Immutable
data class WindowLayout(
    val wide: Boolean = false,
    val tabletop: Boolean = false,
    /** Short windows (landscape phones), where a side-by-side layout saves scrolling. */
    val short: Boolean = false,
)

/** The current [WindowLayout]; provided by the app shell. Features read it instead of measuring. */
val LocalWindowLayout = staticCompositionLocalOf { WindowLayout() }

@Composable
fun currentWindowLayout(): WindowLayout {
    val info = currentWindowAdaptiveInfo()
    val sizeClass = info.windowSizeClass
    return WindowLayout(
        wide = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND),
        tabletop = info.windowPosture.isTabletop,
        short = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
    )
}

/**
 * How wide the body of a screen may get before it stops growing and stays centered, so lines stay
 * readable on a maximized desktop window (desktop ROADMAP D7). Unlimited until the shell sets it: a
 * phone or tablet uses all its width.
 */
val LocalMaxContentWidth = staticCompositionLocalOf { Dp.Unspecified }

/**
 * Caps this element at [LocalMaxContentWidth] and centers it in the space it was given. Put it on
 * the body of a screen, below the top bar, which stays as wide as the window.
 */
@Composable
fun Modifier.readingWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = LocalMaxContentWidth.current)
