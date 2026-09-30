package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

/**
 * The mockups' 64dp top bar: optional navigation icon, a serif title with an optional accessory
 * (e.g. a streak badge), and trailing actions. It pads itself for the status bar, so it can sit
 * in a `Scaffold` topBar slot in an edge-to-edge window.
 */
@Composable
fun MnemoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    titleAccessory: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        // Explicit: contentColorFor() can't resolve a translucent surface and would fall back to black.
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .height(64.dp)
                .padding(
                    start = if (navigationIcon != null) MnemoTheme.spacing.xs else MnemoTheme.spacing.screenMargin,
                    end = MnemoTheme.spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) navigationIcon()
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (titleAccessory != null) titleAccessory()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
