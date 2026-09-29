package com.yahyafati.mnemo.feature.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.feature.settings.R

/** One choice of a [DropdownField]. */
internal class DropdownOption(val label: String, val onSelect: () -> Unit)

/**
 * A field that opens a menu. Built on the stable `DropdownMenu` rather than the exposed-dropdown
 * API, which keeps changing between Material 3 releases.
 */
@Composable
internal fun DropdownField(
    label: String,
    value: String,
    options: List<DropdownOption>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = colors.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled, role = Role.DropdownList, onClickLabel = label) { expanded = true }
                .semantics { contentDescription = "$label: $value" },
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(MnemoIcons.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        option.onSelect()
                    },
                )
            }
        }
    }
}

/** A small tag on a provider row: "Default", "Local", "No key". */
@Composable
internal fun StatusLabel(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
internal fun taskLabel(task: AiTask): String = stringResource(
    when (task) {
        AiTask.Extract -> R.string.feature_settings_ai_task_extract
        AiTask.CoAuthor -> R.string.feature_settings_ai_task_coauthor
        AiTask.Explain -> R.string.feature_settings_ai_task_explain
        AiTask.Rewrite -> R.string.feature_settings_ai_task_rewrite
    },
)

@Composable
internal fun taskHint(task: AiTask): String = stringResource(
    when (task) {
        AiTask.Extract -> R.string.feature_settings_ai_task_extract_hint
        AiTask.CoAuthor -> R.string.feature_settings_ai_task_coauthor_hint
        AiTask.Explain -> R.string.feature_settings_ai_task_explain_hint
        AiTask.Rewrite -> R.string.feature_settings_ai_task_rewrite_hint
    },
)

