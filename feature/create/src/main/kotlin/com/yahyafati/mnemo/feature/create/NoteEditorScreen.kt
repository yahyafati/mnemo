package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.deck.DeckEditorDialog
import com.yahyafati.mnemo.feature.create.component.DeckDropdown
import com.yahyafati.mnemo.feature.create.component.editorFieldColors

/** The editor and its live preview. Stateless; shared by the Create tab and the full-screen editor. */
@Composable
internal fun NoteEditorScreen(
    uiState: NoteEditorUiState,
    onAction: (NoteEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MnemoTheme.spacing
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(uiState.addEvent) {
        if (uiState.addEvent > 0) {
            snackbar.showSnackbar(
                resources.getQuantityString(R.plurals.feature_create_added, uiState.lastAddedCards, uiState.lastAddedCards),
            )
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenMargin, vertical = spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            EditorCard(uiState, onAction, Modifier.widthIn(max = 680.dp))
            PreviewSection(uiState, Modifier.widthIn(max = 680.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (uiState.showDeckDialog) {
        DeckEditorDialog(
            onConfirm = { onAction(NoteEditorAction.CreateDeck(it.path, it.category, it.description)) },
            onDismiss = { onAction(NoteEditorAction.DismissDeckDialog) },
        )
    }
}

@Composable
private fun EditorCard(uiState: NoteEditorUiState, onAction: (NoteEditorAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = colors.primary, contentColor = colors.onPrimary) {
                    Icon(MnemoIcons.Edit, null, Modifier.padding(8.dp).size(20.dp))
                }
                Column(Modifier.padding(start = spacing.sm)) {
                    Text(
                        text = stringResource(R.string.feature_create_subtitle).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                    Text(
                        text = stringResource(if (uiState.isEditing) R.string.feature_create_title_edit else R.string.feature_create_title_new),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }

            DeckDropdown(
                decks = uiState.decks,
                selectedId = uiState.deckId,
                onSelect = { onAction(NoteEditorAction.SelectDeck(it)) },
                onNewDeck = { onAction(NoteEditorAction.ShowDeckDialog) },
            )
            if (!uiState.isEditing) KindPicker(uiState.kind) { onAction(NoteEditorAction.SelectKind(it)) }

            val isCloze = uiState.kind == NoteKind.Cloze
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                EditorField(
                    value = uiState.front,
                    onValueChange = { onAction(NoteEditorAction.FrontChanged(it)) },
                    label = stringResource(if (isCloze) R.string.feature_create_text else R.string.feature_create_front),
                    textStyle = MnemoTheme.typography.studyPromptCompact,
                )
                if (isCloze) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MnemoButton(
                            text = stringResource(R.string.feature_create_cloze),
                            onClick = { onAction(NoteEditorAction.InsertCloze) },
                            style = MnemoButtonStyle.Secondary,
                            leadingIcon = MnemoIcons.Cloze,
                        )
                        Text(
                            text = stringResource(R.string.feature_create_cloze_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = spacing.sm),
                        )
                    }
                }
            }
            EditorField(
                value = uiState.back,
                onValueChange = { onAction(NoteEditorAction.BackChanged(it)) },
                label = stringResource(if (isCloze) R.string.feature_create_extra else R.string.feature_create_back),
                supportingText = stringResource(R.string.feature_create_markdown_hint),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            TagEditor(uiState, onAction)

            MnemoButton(
                text = if (uiState.isEditing) {
                    stringResource(R.string.feature_create_save)
                } else {
                    val count = uiState.cardCount.coerceAtLeast(1)
                    pluralStringResource(R.plurals.feature_create_add, count, count)
                },
                onClick = { onAction(NoteEditorAction.Save) },
                enabled = uiState.canSave,
                leadingIcon = if (uiState.isEditing) MnemoIcons.Check else MnemoIcons.Add,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )
            uiState.problem?.takeIf { !uiState.isLoading && (it == EditorProblem.NoDeck || uiState.front.text.isNotEmpty()) }?.let {
                Text(
                    text = stringResource(
                        when (it) {
                            EditorProblem.NoDeck -> R.string.feature_create_problem_no_deck
                            EditorProblem.EmptyFront -> R.string.feature_create_problem_front
                            EditorProblem.EmptyBack -> R.string.feature_create_problem_back
                            EditorProblem.NoCloze -> R.string.feature_create_problem_cloze
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun KindPicker(kind: NoteKind, onSelect: (NoteKind) -> Unit) {
    val options = listOf(
        NoteKind.Basic to R.string.feature_create_type_basic,
        NoteKind.Reversed to R.string.feature_create_type_reversed,
        NoteKind.Cloze to R.string.feature_create_type_cloze,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = kind == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(stringResource(label), maxLines = 1) },
            )
        }
    }
}

@Composable
private fun EditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    textStyle: TextStyle,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        textStyle = textStyle,
        minLines = 2,
        shape = MaterialTheme.shapes.small,
        colors = editorFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TagEditor(uiState: NoteEditorUiState, onAction: (NoteEditorAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        if (uiState.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                uiState.tags.forEach { tag ->
                    val description = stringResource(R.string.feature_create_remove_tag, tag)
                    InputChip(
                        selected = false,
                        onClick = { onAction(NoteEditorAction.RemoveTag(tag)) },
                        label = { Text("#$tag", style = MnemoTheme.typography.metricSm) },
                        trailingIcon = { Icon(MnemoIcons.Close, description, Modifier.size(14.dp)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = uiState.tagInput,
            onValueChange = { onAction(NoteEditorAction.TagInputChanged(it)) },
            placeholder = { Text(stringResource(R.string.feature_create_add_tag)) },
            leadingIcon = { Icon(MnemoIcons.Tag, stringResource(R.string.feature_create_tags)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = editorFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(NoteEditorAction.CommitTag) }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewSection(uiState: NoteEditorUiState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    var showBack by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = colors.surfaceContainerHighest)
            Text(
                text = stringResource(R.string.feature_create_preview).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.outline,
            )
            HorizontalDivider(Modifier.weight(1f), color = colors.surfaceContainerHighest)
        }
        SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.CenterHorizontally)) {
            listOf(false to R.string.feature_create_preview_front, true to R.string.feature_create_preview_back)
                .forEachIndexed { index, (back, label) ->
                    SegmentedButton(
                        selected = showBack == back,
                        onClick = { showBack = back },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(stringResource(label)) },
                    )
                }
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLowest,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            CardFace(
                sides = uiState.previewSides,
                revealed = showBack,
                modifier = Modifier
                    .padding(spacing.lg)
                    .heightIn(min = 96.dp),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun NoteEditorScreenPreview() {
    MnemoTheme {
        NoteEditorScreen(
            uiState = NoteEditorUiState(
                isLoading = false,
                decks = listOf(DeckOption("d", "Neuroscience")),
                deckId = "d",
                kind = NoteKind.Cloze,
                front = TextFieldValue("The {{c1::amygdala}} drives fear conditioning."),
                back = TextFieldValue("Basolateral complex"),
                tags = listOf("limbic-system"),
            ),
            onAction = {},
        )
    }
}
