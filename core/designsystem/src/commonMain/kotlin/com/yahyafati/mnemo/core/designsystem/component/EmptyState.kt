package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

/** Centered icon, title, message and optional action, for screens with nothing to show yet. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = MnemoTheme.spacing.sm),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = MnemoTheme.spacing.sm)) { action() }
        }
    }
}
