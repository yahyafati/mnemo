package com.yahyafati.mnemo.feature.study

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.feature.study.resources.Res
import com.yahyafati.mnemo.feature.study.resources.feature_study_close
import com.yahyafati.mnemo.feature.study.resources.feature_study_done
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * A focused session for one deck. Not a tab, so it gets the whole window: its own top bar with a
 * close button, and no bottom bar.
 */
@Composable
internal fun StudySessionScreen(
    onClose: () -> Unit,
    onEditNote: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = uiState.deckName.orEmpty(),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.Close,
                        contentDescription = stringResource(Res.string.feature_study_close),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        StudyScreen(
            onEditNote = onEditNote,
            onDone = onClose,
            doneLabel = stringResource(Res.string.feature_study_done),
            viewModel = viewModel,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}
