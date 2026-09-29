package com.yahyafati.mnemo.feature.create

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.GeneratedCardProblem
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.ui.ai.AiDisclosureDialog
import com.yahyafati.mnemo.core.ui.ai.AiReport
import com.yahyafati.mnemo.core.ui.ai.AiReportKind
import com.yahyafati.mnemo.core.ui.ai.AiSetupPrompt
import com.yahyafati.mnemo.core.ui.ai.ReportAiButton
import com.yahyafati.mnemo.core.ui.ai.aiFailureText
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.permission.PermissionRationaleDialog
import com.yahyafati.mnemo.core.ui.deck.DeckEditorDialog
import com.yahyafati.mnemo.feature.create.component.DeckDropdown
import com.yahyafati.mnemo.feature.create.component.OptionDropdown
import com.yahyafati.mnemo.feature.create.component.editorFieldColors
import java.time.Instant

/**
 * Smart Extract (`design/ai-card-creator.html`): the source and options card, then the review
 * queue, which fills while cards stream in. Stateless apart from the PDF picker and the
 * microphone permission request.
 */
@Composable
internal fun SmartExtractScreen(
    uiState: SmartExtractUiState,
    onAction: (SmartExtractAction) -> Unit,
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = uiState.message?.let { messageText(it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onAction(SmartExtractAction.MessageShown)
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> Unit
            uiState.route == null -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                contentAlignment = Alignment.TopCenter,
            ) {
                AiSetupPrompt(
                    onSetUp = onSetUpAi,
                    message = stringResource(R.string.feature_create_smart_setup),
                    modifier = Modifier.widthIn(max = 680.dp),
                )
            }
            else -> {
                val acceptable = uiState.acceptable.size
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(
                        start = MnemoTheme.spacing.screenMargin,
                        end = MnemoTheme.spacing.screenMargin,
                        top = MnemoTheme.spacing.md,
                        bottom = if (acceptable > 0) 96.dp else MnemoTheme.spacing.lg,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md),
                ) {
                    item(key = "workshop") { Workshop(uiState, uiState.route, onAction, onSetUpAi, Modifier.widthIn(max = 680.dp)) }
                    item(key = "queue-header") { QueueHeader(uiState, onAction, Modifier.widthIn(max = 680.dp)) }
                    itemsIndexed(uiState.queue, key = { _, item -> item.card.id }) { index, item ->
                        ReviewCard(
                            item = item,
                            number = index + 1,
                            editing = uiState.editingId == item.card.id,
                            modelId = uiState.route.modelId,
                            onAction = onAction,
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .animateItem(),
                        )
                    }
                }
                if (acceptable > 0) {
                    AcceptAllBar(
                        count = acceptable,
                        deckPath = uiState.deckPath,
                        onAcceptAll = { onAction(SmartExtractAction.AcceptAll) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.sm)
                            .widthIn(max = 680.dp),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (uiState.acceptable.isNotEmpty()) 80.dp else 0.dp))
    }

    uiState.disclosure?.let { route ->
        AiDisclosureDialog(
            providerName = route.provider.name,
            host = AiEndpoint.host(route.provider.baseUrl) ?: route.provider.baseUrl,
            whatIsSent = stringResource(R.string.feature_create_disclosure_content),
            onAccept = { onAction(SmartExtractAction.AcceptDisclosure) },
            onDismiss = { onAction(SmartExtractAction.DismissDisclosure) },
        )
    }
    if (uiState.showDeckDialog) {
        DeckEditorDialog(
            onConfirm = { onAction(SmartExtractAction.CreateDeck(it.path, it.category, it.description)) },
            onDismiss = { onAction(SmartExtractAction.DismissDeckDialog) },
        )
    }
}

@Composable
private fun messageText(message: ExtractMessage): String = when (message) {
    is ExtractMessage.Accepted -> pluralStringResource(R.plurals.feature_create_accepted, message.cards, message.cards, message.deckPath)
    ExtractMessage.NothingNew -> stringResource(R.string.feature_create_nothing_new)
    is ExtractMessage.RegenerateFailed -> stringResource(R.string.feature_create_regenerate_failed, aiFailureText(message.failure))
}

/** The source, the options and the Generate button: the mockup's "AI generator workshop" card. */
@Composable
private fun Workshop(
    uiState: SmartExtractUiState,
    route: AiRoute,
    onAction: (SmartExtractAction) -> Unit,
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    Icon(MnemoIcons.Sparkle, null, Modifier.padding(8.dp).size(20.dp))
                }
                Column(Modifier.padding(start = spacing.sm)) {
                    Text(
                        text = stringResource(R.string.feature_create_smart_label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                    Text(stringResource(R.string.feature_create_smart_title), style = MaterialTheme.typography.headlineSmall)
                }
            }

            SourcePicker(uiState.sourceKind) { onAction(SmartExtractAction.SelectSource(it)) }
            if (uiState.sourceKind != SourceKind.Paste || uiState.reading || uiState.sourceProblem != null) SourcePanel(uiState, onAction)
            SourceTextField(uiState, onAction)

            DeckDropdown(
                decks = uiState.decks,
                selectedId = uiState.deckId,
                onSelect = { onAction(SmartExtractAction.SelectDeck(it)) },
                onNewDeck = { onAction(SmartExtractAction.ShowDeckDialog) },
            )
            DensitySlider(uiState) { onAction(SmartExtractAction.SetDensity(it)) }
            ArchetypePicker(uiState.options.archetypes) { onAction(SmartExtractAction.ToggleArchetype(it)) }
            LanguagePicker(uiState.options.language) { onAction(SmartExtractAction.SetLanguage(it)) }

            GenerateControls(uiState, onAction)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MnemoIcons.Route, null, tint = colors.outline, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(R.string.feature_create_smart_via, route.provider.name, route.modelId),
                    style = MnemoTheme.typography.metricSm,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.xs),
                )
                TextButton(onClick = onSetUpAi) { Text(stringResource(R.string.feature_create_smart_manage)) }
            }
        }
    }
}

@Composable
private fun SourcePicker(selected: SourceKind, onSelect: (SourceKind) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        SourceKind.entries.forEach { kind ->
            MnemoChip(
                label = stringResource(
                    when (kind) {
                        SourceKind.Paste -> R.string.feature_create_source_paste
                        SourceKind.Pdf -> R.string.feature_create_source_pdf
                        SourceKind.Link -> R.string.feature_create_source_link
                        SourceKind.Dictation -> R.string.feature_create_source_dictation
                    },
                ),
                selected = selected == kind,
                onClick = { onSelect(kind) },
            )
        }
    }
}

/** What each source needs besides the text box: a file picker, a link field, or the microphone. */
@Composable
private fun SourcePanel(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        when (uiState.sourceKind) {
            SourceKind.Paste -> Unit
            SourceKind.Pdf -> {
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { onAction(SmartExtractAction.PdfPicked(it.toString())) }
                }
                MnemoButton(
                    text = stringResource(R.string.feature_create_pdf_pick),
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    style = MnemoButtonStyle.Secondary,
                    leadingIcon = MnemoIcons.Pdf,
                    enabled = !uiState.reading,
                )
                Hint(stringResource(R.string.feature_create_pdf_hint))
            }
            SourceKind.Link -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = uiState.link,
                        onValueChange = { onAction(SmartExtractAction.LinkChanged(it)) },
                        label = { Text(stringResource(R.string.feature_create_link_label)) },
                        leadingIcon = { Icon(MnemoIcons.Link, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = editorFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onAction(SmartExtractAction.FetchLink) }),
                        modifier = Modifier.weight(1f),
                    )
                    MnemoButton(
                        text = stringResource(R.string.feature_create_link_read),
                        onClick = { onAction(SmartExtractAction.FetchLink) },
                        style = MnemoButtonStyle.Secondary,
                        enabled = uiState.link.isNotBlank() && !uiState.reading,
                    )
                }
                Hint(stringResource(R.string.feature_create_link_hint))
            }
            SourceKind.Dictation -> DictationControls(uiState, onAction)
        }
        if (uiState.reading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.feature_create_reading), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = MnemoTheme.spacing.sm))
            }
        }
        uiState.sourceProblem?.let {
            Text(sourceProblemText(it), style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

@Composable
private fun DictationControls(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAction(if (granted) SmartExtractAction.StartDictation else SmartExtractAction.DictationPermissionDenied)
    }
    var explainMicrophone by rememberSaveable { mutableStateOf(false) }
    val listening = uiState.dictation as? DictationState.Listening
    MnemoButton(
        text = stringResource(if (listening != null) R.string.feature_create_dictation_stop else R.string.feature_create_dictation_start),
        onClick = {
            when {
                listening != null -> onAction(SmartExtractAction.StopDictation)
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                    onAction(SmartExtractAction.StartDictation)
                else -> explainMicrophone = true
            }
        },
        style = if (listening != null) MnemoButtonStyle.Primary else MnemoButtonStyle.Secondary,
        leadingIcon = if (listening != null) MnemoIcons.Stop else MnemoIcons.Mic,
    )
    if (explainMicrophone) {
        PermissionRationaleDialog(
            icon = MnemoIcons.Mic,
            title = stringResource(R.string.feature_create_mic_title),
            message = stringResource(R.string.feature_create_mic_message),
            onContinue = {
                explainMicrophone = false
                permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onNotNow = { explainMicrophone = false },
        )
    }
    when (val dictation = uiState.dictation) {
        is DictationState.Listening -> Text(
            text = dictation.partial.ifEmpty { stringResource(R.string.feature_create_dictation_listening) },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
        is DictationState.Failed -> Text(
            text = stringResource(
                when (dictation.problem) {
                    DictationProblem.Unavailable -> R.string.feature_create_dictation_unavailable
                    DictationProblem.NoPermission -> R.string.feature_create_dictation_permission
                    DictationProblem.LanguageUnavailable -> R.string.feature_create_dictation_language
                    DictationProblem.RecognizerError -> R.string.feature_create_dictation_error
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        DictationState.Off -> Hint(stringResource(R.string.feature_create_dictation_hint))
    }
}

@Composable
private fun SourceTextField(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        uiState.title?.let {
            Text(
                text = stringResource(R.string.feature_create_source_from, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = uiState.text,
            onValueChange = { onAction(SmartExtractAction.TextChanged(it)) },
            label = { Text(stringResource(R.string.feature_create_source_text)) },
            placeholder = { Text(stringResource(R.string.feature_create_source_placeholder)) },
            supportingText = { Text(pluralStringResource(R.plurals.feature_create_source_words, uiState.wordCount, uiState.wordCount)) },
            trailingIcon = if (uiState.text.isNotEmpty()) {
                {
                    IconButton(onClick = { onAction(SmartExtractAction.ClearText) }) {
                        Icon(MnemoIcons.Close, stringResource(R.string.feature_create_source_clear))
                    }
                }
            } else {
                null
            },
            minLines = 5,
            maxLines = 12,
            shape = MaterialTheme.shapes.small,
            colors = editorFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (uiState.truncated) Hint(stringResource(R.string.feature_create_source_truncated))
    }
}

@Composable
private fun DensitySlider(uiState: SmartExtractUiState, onSelect: (ExtractDensity) -> Unit) {
    val density = uiState.options.density
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(R.string.feature_create_density), Modifier.weight(1f))
            Text(densityLabel(density), style = MnemoTheme.typography.metricSm, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = density.ordinal.toFloat(),
            onValueChange = { onSelect(ExtractDensity.entries[it.toInt().coerceIn(0, ExtractDensity.entries.lastIndex)]) },
            valueRange = 0f..ExtractDensity.entries.lastIndex.toFloat(),
            steps = ExtractDensity.entries.size - 2,
        )
        Row {
            Hint(densityLabel(ExtractDensity.Concise), Modifier.weight(1f))
            Hint(densityLabel(ExtractDensity.Comprehensive))
        }
    }
}

@Composable
private fun densityLabel(density: ExtractDensity) = stringResource(
    when (density) {
        ExtractDensity.Concise -> R.string.feature_create_density_concise
        ExtractDensity.Balanced -> R.string.feature_create_density_balanced
        ExtractDensity.Comprehensive -> R.string.feature_create_density_comprehensive
    },
)

@Composable
private fun ArchetypePicker(selected: Set<CardArchetype>, onToggle: (CardArchetype) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Label(stringResource(R.string.feature_create_archetypes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            CardArchetype.entries.forEach { archetype ->
                MnemoChip(
                    label = stringResource(
                        when (archetype) {
                            CardArchetype.Definition -> R.string.feature_create_archetype_definition
                            CardArchetype.Cloze -> R.string.feature_create_archetype_cloze
                            CardArchetype.MultipleChoice -> R.string.feature_create_archetype_choice
                            CardArchetype.CaseStudy -> R.string.feature_create_archetype_case
                        },
                    ),
                    selected = archetype in selected,
                    onClick = { onToggle(archetype) },
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(language: String?, onSelect: (String?) -> Unit) {
    val sameAsSource = stringResource(R.string.feature_create_language_source)
    OptionDropdown(
        value = language ?: sameAsSource,
        label = stringResource(R.string.feature_create_language),
        leadingIcon = MnemoIcons.Translate,
        options = listOf(sameAsSource to { onSelect(null) }) + ExtractOptions.Languages.map { name -> name to { onSelect(name) } },
    )
}

@Composable
private fun GenerateControls(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (val generation = uiState.generation) {
        is GenerationState.Running -> Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (generation.parts > 1) {
                        stringResource(R.string.feature_create_generating_part, generation.part + 1, generation.parts)
                    } else {
                        stringResource(R.string.feature_create_generating)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                MnemoButton(
                    text = stringResource(R.string.feature_create_stop),
                    onClick = { onAction(SmartExtractAction.Stop) },
                    style = MnemoButtonStyle.Secondary,
                    leadingIcon = MnemoIcons.Stop,
                )
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            if (generation is GenerationState.Failed) {
                Surface(shape = MaterialTheme.shapes.small, color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                    Row(Modifier.padding(start = MnemoTheme.spacing.md, end = MnemoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        Icon(MnemoIcons.Error, null, Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.feature_create_failed, aiFailureText(generation.failure)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(MnemoTheme.spacing.sm),
                        )
                        TextButton(onClick = { onAction(SmartExtractAction.Retry) }) { Text(stringResource(R.string.feature_create_retry)) }
                    }
                }
            }
            val estimate = uiState.estimatedCards
            MnemoButton(
                text = if (estimate > 0) {
                    pluralStringResource(R.plurals.feature_create_generate, estimate, estimate)
                } else {
                    stringResource(R.string.feature_create_generate_empty)
                },
                onClick = { onAction(SmartExtractAction.Generate) },
                enabled = uiState.canGenerate,
                leadingIcon = MnemoIcons.Sparkle,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            )
        }
    }
}

@Composable
private fun QueueHeader(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = colors.surfaceContainerHighest)
            Text(
                text = stringResource(R.string.feature_create_queue).uppercase() + if (uiState.queue.isNotEmpty()) " (${uiState.queue.size})" else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f), color = colors.surfaceContainerHighest)
        }
        if (uiState.queue.isEmpty()) {
            Hint(stringResource(R.string.feature_create_queue_empty), Modifier.align(Alignment.CenterHorizontally))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val skipped = uiState.duplicatesSkipped + uiState.invalidSkipped
                Hint(
                    text = if (skipped > 0) pluralStringResource(R.plurals.feature_create_queue_skipped, skipped, skipped) else "",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(SmartExtractAction.DiscardAll) }) { Text(stringResource(R.string.feature_create_discard_all)) }
            }
        }
    }
}

/** One proposed card: shown as it will be studied, or as two fields while editing. */
@Composable
private fun ReviewCard(
    item: QueueItem,
    number: Int,
    editing: Boolean,
    modelId: String?,
    onAction: (SmartExtractAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    val card = item.card
    val isCloze = card.kind == NoteKind.Cloze
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.feature_create_card_number, number).uppercase(), style = MnemoTheme.typography.metricSm, color = colors.onSurfaceVariant)
                Text(
                    text = stringResource(if (isCloze) R.string.feature_create_type_cloze else R.string.feature_create_type_basic),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                    modifier = Modifier.padding(start = spacing.sm),
                )
                Box(Modifier.weight(1f))
                IconButton(onClick = { onAction(SmartExtractAction.Regenerate(card.id)) }, enabled = !item.regenerating) {
                    Icon(MnemoIcons.Regenerate, stringResource(R.string.feature_create_card_regenerate))
                }
                IconButton(onClick = { onAction(if (editing) SmartExtractAction.DoneEditing else SmartExtractAction.Edit(card.id)) }, enabled = !item.regenerating) {
                    Icon(
                        if (editing) MnemoIcons.Check else MnemoIcons.Edit,
                        stringResource(if (editing) R.string.feature_create_card_done else R.string.feature_create_card_edit),
                    )
                }
                ReportAiButton(AiReport.ofFields(AiReportKind.SmartExtractCard, card.fields, modelId), compact = true)
                IconButton(onClick = { onAction(SmartExtractAction.Discard(card.id)) }) {
                    Icon(MnemoIcons.Delete, stringResource(R.string.feature_create_card_discard))
                }
            }
            if (item.regenerating) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (editing) {
                OutlinedTextField(
                    value = card.front,
                    onValueChange = { onAction(SmartExtractAction.EditFront(card.id, it)) },
                    label = { Text(stringResource(if (isCloze) R.string.feature_create_text else R.string.feature_create_front)) },
                    textStyle = MnemoTheme.typography.studyPromptCompact,
                    shape = MaterialTheme.shapes.small,
                    colors = editorFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = card.back,
                    onValueChange = { onAction(SmartExtractAction.EditBack(card.id, it)) },
                    label = { Text(stringResource(if (isCloze) R.string.feature_create_extra else R.string.feature_create_back)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    shape = MaterialTheme.shapes.small,
                    colors = editorFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CardFace(sides = card.sides, revealed = true)
            }

            if (card.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    card.tags.forEach { Text("#$it", style = MnemoTheme.typography.metricSm, color = colors.primary) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.problem?.let { problemText(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier.weight(1f),
                )
                MnemoButton(
                    text = stringResource(R.string.feature_create_card_accept),
                    onClick = { onAction(SmartExtractAction.Accept(card.id)) },
                    style = MnemoButtonStyle.Secondary,
                    leadingIcon = MnemoIcons.Check,
                    enabled = item.problem == null && !item.regenerating,
                )
            }
        }
    }
}

@Composable
private fun problemText(problem: GeneratedCardProblem): String = stringResource(
    when (problem) {
        GeneratedCardProblem.EmptyFront -> R.string.feature_create_problem_front
        GeneratedCardProblem.EmptyBack -> R.string.feature_create_problem_back
        GeneratedCardProblem.NoCloze -> R.string.feature_create_problem_cloze
        GeneratedCardProblem.BrokenCloze -> R.string.feature_create_problem_broken_cloze
        GeneratedCardProblem.TooLong -> R.string.feature_create_problem_too_long
        GeneratedCardProblem.NoWrongAnswers -> R.string.feature_create_problem_wrong
    },
)

@Composable
private fun sourceProblemText(problem: SourceProblem): String = stringResource(
    when (problem) {
        SourceProblem.NoText -> R.string.feature_create_source_no_text
        SourceProblem.Encrypted -> R.string.feature_create_source_encrypted
        SourceProblem.Unsupported -> R.string.feature_create_source_unsupported
        SourceProblem.TooLarge -> R.string.feature_create_source_too_large
        SourceProblem.InvalidUrl -> R.string.feature_create_source_invalid_url
        SourceProblem.Unreachable -> R.string.feature_create_source_unreachable
        SourceProblem.HttpError -> R.string.feature_create_source_http
        SourceProblem.FileUnavailable -> R.string.feature_create_source_unavailable
    },
)

/** "Accept all (12)" into the destination deck: the mockup's quick insertion bar. */
@Composable
private fun AcceptAllBar(count: Int, deckPath: String?, onAcceptAll: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = MaterialTheme.shapes.medium,
        color = colors.inverseSurface,
        contentColor = colors.inverseOnSurface,
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(start = MnemoTheme.spacing.md, end = MnemoTheme.spacing.sm, top = MnemoTheme.spacing.sm, bottom = MnemoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.Bolt, null, Modifier.size(18.dp), tint = colors.inversePrimary)
            Text(
                text = deckPath?.let { stringResource(R.string.feature_create_accept_into, it) } ?: stringResource(R.string.feature_create_accept_no_deck),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MnemoTheme.spacing.sm),
            )
            MnemoButton(text = pluralStringResource(R.plurals.feature_create_accept_all, count, count), onClick = onAcceptAll)
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
private fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun SmartExtractScreenPreview() {
    val provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    val text = "The amygdala plays a primary role in processing emotional memories, particularly fear responses. " +
        "The basolateral complex receives sensory inputs from cortical structures."
    MnemoTheme {
        SmartExtractScreen(
            uiState = SmartExtractUiState(
                isLoading = false,
                route = AiRoute(AiTask.Extract, provider, "llama-3.3-70b", AiCapabilities(), usesDefault = true),
                decks = listOf(DeckOption("d", "Cognitive Neuroscience")),
                deckId = "d",
                text = text,
                generation = GenerationState.Running(0, 1),
                queue = listOf(
                    QueueItem(GeneratedCard("1", NoteKind.Basic, "Which structure is primary in fear-conditioned memory?", "The amygdala (basolateral complex).", listOf("limbic-system")), text),
                    QueueItem(GeneratedCard("2", NoteKind.Cloze, "The {{c1::basolateral complex}} receives cortical sensory input.", ""), text),
                ),
            ),
            onAction = {},
            onSetUpAi = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartExtractSetupPreview() {
    MnemoTheme { SmartExtractScreen(SmartExtractUiState(isLoading = false), onAction = {}, onSetUpAi = {}) }
}
