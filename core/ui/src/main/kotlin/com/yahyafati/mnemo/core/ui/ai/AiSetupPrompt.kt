package com.yahyafati.mnemo.core.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.R

/**
 * Shown by every AI entry point while no provider is usable (PROJECT_OVERVIEW §5.1): AI is
 * optional, so the feature explains itself and links to setup instead of failing.
 */
@Composable
fun AiSetupPrompt(
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.core_ui_ai_setup_message),
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(MnemoTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
        ) {
            Icon(MnemoIcons.CreateSelected, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
            Text(
                text = stringResource(R.string.core_ui_ai_setup_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            MnemoButton(
                text = stringResource(R.string.core_ui_ai_setup_action),
                onClick = onSetUp,
                leadingIcon = MnemoIcons.Add,
                modifier = Modifier.padding(top = MnemoTheme.spacing.sm),
            )
        }
    }
}

@Preview
@Composable
private fun AiSetupPromptPreview() {
    MnemoTheme { AiSetupPrompt(onSetUp = {}) }
}
