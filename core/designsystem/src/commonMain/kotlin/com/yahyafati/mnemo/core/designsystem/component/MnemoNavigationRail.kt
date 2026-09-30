package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * [MnemoNavigationBar] for wide windows (tablets, unfolded foldables, landscape): the same tabs
 * down the start edge, with [header] (the settings button) above them. Pads itself for the
 * system bars on its side.
 */
@Composable
fun MnemoNavigationRail(
    modifier: Modifier = Modifier,
    header: @Composable (ColumnScope.() -> Unit)? = null,
    windowInsets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .width(88.dp)
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            header?.invoke(this)
            Column(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
fun MnemoNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selectedIcon: ImageVector = icon,
) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant, label = "railItemTint")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 36.dp),
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The M3 rail's pill marks the selected tab; the label below carries the name for everyone.
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(32.dp)
                .background(if (selected) colors.secondaryContainer else colors.surface, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (selected) selectedIcon else icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.025.em),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
