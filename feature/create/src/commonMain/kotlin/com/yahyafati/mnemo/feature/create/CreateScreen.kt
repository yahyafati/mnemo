package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorScreen
import com.yahyafati.mnemo.feature.create.coauthor.CoAuthorViewModel
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_close
import com.yahyafati.mnemo.feature.create.resources.feature_create_mode_coauthor
import com.yahyafati.mnemo.feature.create.resources.feature_create_mode_manual
import com.yahyafati.mnemo.feature.create.resources.feature_create_mode_smart
import com.yahyafati.mnemo.feature.create.resources.feature_create_title_edit
import com.yahyafati.mnemo.feature.create.resources.feature_create_title_new
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Which side of the Create tab is showing. */
internal enum class CreateMode { SmartExtract, CoAuthor, Manual }

/**
 * The Create tab: Smart Extract and Co-Author (AI), and the manual editor. The AI modes show the
 * provider setup prompt until a provider is ready.
 */
@Composable
internal fun CreateScreen(
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = koinViewModel(),
    smartExtractViewModel: SmartExtractViewModel = koinViewModel(),
    coAuthorViewModel: CoAuthorViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val smartExtract by smartExtractViewModel.uiState.collectAsStateWithLifecycle()
    val coAuthor by coAuthorViewModel.uiState.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(CreateMode.Manual) }
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.sm),
        ) {
            CreateMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { mode = option },
                    shape = SegmentedButtonDefaults.itemShape(index, CreateMode.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (option) {
                                    CreateMode.SmartExtract -> Res.string.feature_create_mode_smart
                                    CreateMode.CoAuthor -> Res.string.feature_create_mode_coauthor
                                    CreateMode.Manual -> Res.string.feature_create_mode_manual
                                },
                            ),
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        when (mode) {
            CreateMode.Manual -> NoteEditorScreen(uiState = uiState, onAction = viewModel::onAction, modifier = Modifier.weight(1f))
            CreateMode.SmartExtract -> SmartExtractScreen(
                uiState = smartExtract,
                onAction = smartExtractViewModel::onAction,
                onSetUpAi = onSetUpAi,
                modifier = Modifier.weight(1f),
            )
            CreateMode.CoAuthor -> CoAuthorScreen(
                uiState = coAuthor,
                onAction = coAuthorViewModel::onAction,
                onSetUpAi = onSetUpAi,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The editor as its own screen: adding cards to a given deck, or editing a note in place from a
 * study session. Not a tab, so it owns its top bar.
 */
@Composable
internal fun NoteEditorFullScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = koinViewModel(),
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
                title = stringResource(if (uiState.isEditing) Res.string.feature_create_title_edit else Res.string.feature_create_title_new),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.Close,
                        contentDescription = stringResource(Res.string.feature_create_close),
                        onClick = onClose,
                    )
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
