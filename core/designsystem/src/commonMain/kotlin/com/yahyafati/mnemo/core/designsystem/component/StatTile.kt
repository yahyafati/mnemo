package com.yahyafati.mnemo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

/**
 * Compact metric tile from the Decks mockup's stats strip: an uppercase mono label with an
 * optional icon, then a large mono value with an optional unit ("14 days", "94.2%").
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val colors = MaterialTheme.colorScheme
    val mnemoType = MnemoTheme.typography
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(MnemoTheme.spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.uppercase(),
                    style = mnemoType.metricSm.copy(fontSize = 11.sp),
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                }
            }
            Row(
                modifier = Modifier.padding(top = MnemoTheme.spacing.xs),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = value,
                    style = mnemoType.metricLg.copy(fontWeight = FontWeight.Bold),
                    color = valueColor,
                    maxLines = 1,
                )
                if (unit != null) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
