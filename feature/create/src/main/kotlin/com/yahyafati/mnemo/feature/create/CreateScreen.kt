package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons

/** The Create tab: add new notes. AI Smart Extract joins this tab in Phase 4. */
@Composable
internal fun CreateScreen(
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NoteEditorScreen(uiState = uiState, onAction = viewModel::onAction, modifier = modifier)
}

/**
 * The editor as its own screen: adding cards to a given deck, or editing a note in place from a
 * study session. Not a tab, so it owns its top bar.
 */
@Composable
internal fun NoteEditorFullScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.closeRequested) {
        if (uiState.closeRequested) onClose()
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(if (uiState.isEditing) R.string.feature_create_title_edit else R.string.feature_create_title_new),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(MnemoIcons.Close, stringResource(R.string.feature_create_close))
                    }
                },
            )
        },
    ) { padding ->
        NoteEditorScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}
