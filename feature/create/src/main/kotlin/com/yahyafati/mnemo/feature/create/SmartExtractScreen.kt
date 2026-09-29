package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.ai.AiSetupPrompt

@Composable
internal fun SmartExtractScreen(
    uiState: SmartExtractUiState,
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
        contentAlignment = Alignment.TopCenter,
    ) {
        val content = Modifier.widthIn(max = 680.dp)
        when (uiState) {
            SmartExtractUiState.Loading -> Unit
            SmartExtractUiState.NeedsProvider -> AiSetupPrompt(
                onSetUp = onSetUpAi,
                message = stringResource(R.string.feature_create_smart_setup),
                modifier = content,
            )
            is SmartExtractUiState.Ready -> Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = content.fillMaxWidth(),
            ) {
                Column(Modifier.padding(MnemoTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    Text(stringResource(R.string.feature_create_smart_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.feature_create_smart_soon), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${uiState.route.provider.name} · ${uiState.route.modelId}",
                        style = MnemoTheme.typography.metricSm,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MnemoButton(stringResource(R.string.feature_create_smart_manage), onSetUpAi, style = MnemoButtonStyle.Text)
                }
            }
        }
    }
}

@Preview
@Composable
private fun SmartExtractSetupPreview() {
    MnemoTheme { SmartExtractScreen(SmartExtractUiState.NeedsProvider, onSetUpAi = {}) }
}
