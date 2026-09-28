package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

// Placeholder until settings are built (docs/ROADMAP.md, Phase 1). Settings is not a tab, so it
// owns its Scaffold and top bar; the app shell hides its own bars on this screen.
@Composable
internal fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MnemoTopBar(
                title = stringResource(R.string.feature_settings_title),
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(MnemoIcons.ArrowBack, contentDescription = stringResource(R.string.feature_settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = MnemoIcons.Settings,
                title = stringResource(R.string.feature_settings_empty_title),
                message = stringResource(R.string.feature_settings_empty_message),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    MnemoTheme {
        SettingsScreen(onBackClick = {})
    }
}
