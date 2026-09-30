package com.yahyafati.mnemo.core.ui.menu

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.DefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
actual fun ContextMenuHost(actions: List<ContextAction>, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Compose's default menu picks light or dark colors of its own; this one wears the app's.
    val representation = remember(colors.surfaceContainerHigh, colors.onSurface) {
        DefaultContextMenuRepresentation(
            backgroundColor = colors.surfaceContainerHigh,
            textColor = colors.onSurface,
            itemHoverColor = colors.onSurface.copy(alpha = 0.08f),
        )
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides representation) {
        ContextMenuArea(items = { actions.map { ContextMenuItem(it.label, it.onClick) } }, content = content)
    }
}
