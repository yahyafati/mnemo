package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

// Placeholder until the Create screen is built (docs/ROADMAP.md).
@Composable
internal fun CreateScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = MnemoIcons.Create,
            title = stringResource(R.string.feature_create_empty_title),
            message = stringResource(R.string.feature_create_empty_message),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateScreenPreview() {
    MnemoTheme {
        CreateScreen()
    }
}
