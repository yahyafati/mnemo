package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.yahyafati.mnemo.core.ui.files.rememberMediaPicker
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
    LaunchedEffect(uiState.attachFailedEvent) {
        if (uiState.attachFailedEvent > 0) snackbar.showSnackbar(resources.getString(R.string.feature_create_attach_failed))
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

            val kind = uiState.kind
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                EditorField(
                    value = uiState.front,
                    onValueChange = { onAction(NoteEditorAction.FrontChanged(it)) },
                    label = stringResource(
                        when (kind) {
                            NoteKind.Cloze -> R.string.feature_create_text
                            NoteKind.MultipleChoice -> R.string.feature_create_question
                            else -> R.string.feature_create_front
                        },
                    ),
                    textStyle = MnemoTheme.typography.studyPromptCompact,
                    onFocused = { onAction(NoteEditorAction.FieldFocused(EditorField.Front)) },
                )
                if (kind == NoteKind.Cloze) {
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
                label = stringResource(
                    when (kind) {
                        NoteKind.Cloze -> R.string.feature_create_extra
                        NoteKind.TypeIn -> R.string.feature_create_typed_answer
                        NoteKind.MultipleChoice -> R.string.feature_create_correct_answer
                        else -> R.string.feature_create_back
                    },
                ),
                supportingText = stringResource(R.string.feature_create_markdown_hint).takeIf { kind != NoteKind.MultipleChoice },
                textStyle = MaterialTheme.typography.bodyLarge,
                onFocused = { onAction(NoteEditorAction.FieldFocused(EditorField.Back)) },
            )
            if (kind == NoteKind.MultipleChoice) {
                EditorField(
                    value = uiState.wrong,
                    onValueChange = { onAction(NoteEditorAction.WrongChanged(it)) },
                    label = stringResource(R.string.feature_create_wrong_answers),
                    supportingText = stringResource(R.string.feature_create_wrong_answers_hint),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    minLines = 3,
                )
            }
            HintEditor(uiState, onAction)
            AttachRow(uiState, onAction)
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
                            EditorProblem.NoWrongAnswers -> R.string.feature_create_problem_wrong
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
        NoteKind.TypeIn to R.string.feature_create_type_type_in,
        NoteKind.MultipleChoice to R.string.feature_create_type_choice,
    )
    // Five types don't fit a segmented row on a phone; chips scroll instead.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
    ) {
        options.forEach { (option, label) ->
            FilterChip(
                selected = kind == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(label), maxLines = 1) },
                leadingIcon = if (kind == option) {
                    { Icon(MnemoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else {
                    null
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
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
    minLines: Int = 2,
    onFocused: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        textStyle = textStyle,
        minLines = minLines,
        shape = MaterialTheme.shapes.small,
        colors = editorFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() },
    )
}

/** The optional hint: a button until there is one, then its field. */
@Composable
private fun HintEditor(uiState: NoteEditorUiState, onAction: (NoteEditorAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!expanded && uiState.hint.isEmpty()) {
        TextButton(onClick = { expanded = true }) {
            Icon(MnemoIcons.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.feature_create_add_hint), modifier = Modifier.padding(start = MnemoTheme.spacing.xs))
        }
        return
    }
    OutlinedTextField(
        value = uiState.hint,
        onValueChange = { onAction(NoteEditorAction.HintChanged(it)) },
        label = { Text(stringResource(R.string.feature_create_hint)) },
        supportingText = { Text(stringResource(R.string.feature_create_hint_supporting)) },
        leadingIcon = { Icon(MnemoIcons.Lightbulb, contentDescription = null) },
        shape = MaterialTheme.shapes.small,
        colors = editorFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onAction(NoteEditorAction.FieldFocused(EditorField.Hint)) },
    )
}

/** Attach an image or a sound to the field focused last. Files are copied into the app. */
@Composable
private fun AttachRow(uiState: NoteEditorUiState, onAction: (NoteEditorAction) -> Unit) {
    val pickImage = rememberMediaPicker("image/*") { onAction(NoteEditorAction.Attach(it, AttachmentKind.Image)) }
    val pickAudio = rememberMediaPicker("audio/*") { onAction(NoteEditorAction.Attach(it, AttachmentKind.Audio)) }
    val target = stringResource(
        when (uiState.target) {
            EditorField.Front -> when (uiState.kind) {
                NoteKind.Cloze -> R.string.feature_create_text
                NoteKind.MultipleChoice -> R.string.feature_create_question
                else -> R.string.feature_create_front
            }
            EditorField.Back -> if (uiState.kind == NoteKind.Cloze) R.string.feature_create_extra else R.string.feature_create_back
            EditorField.Hint -> R.string.feature_create_hint
        },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        MnemoButton(
            text = stringResource(R.string.feature_create_attach_image),
            onClick = { pickImage.launch() },
            style = MnemoButtonStyle.Secondary,
            leadingIcon = MnemoIcons.Image,
            enabled = !uiState.attaching,
        )
        MnemoButton(
            text = stringResource(R.string.feature_create_attach_audio),
            onClick = { pickAudio.launch() },
            style = MnemoButtonStyle.Secondary,
            leadingIcon = MnemoIcons.AudioFile,
            enabled = !uiState.attaching,
        )
        Text(
            text = if (uiState.attaching) stringResource(R.string.feature_create_attaching) else stringResource(R.string.feature_create_attach_target, target),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                color = colors.onSurfaceVariant,
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
                hint = uiState.hint.takeIf { it.isNotBlank() },
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
