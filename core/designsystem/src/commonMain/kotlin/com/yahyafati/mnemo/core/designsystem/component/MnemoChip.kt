package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * Filter pill from the Decks mockup ("All Decks", "Due Today", "Starred"). Selected pills invert to
 * on-surface; unselected ones sit on surface-container-high.
 */
@Composable
fun MnemoChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.clickCursor(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.onSurface else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.surface else colors.onSurfaceVariant,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.02.em),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
