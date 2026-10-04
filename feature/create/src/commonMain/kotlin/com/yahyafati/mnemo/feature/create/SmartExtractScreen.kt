package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.Role
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
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.clickCursor
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.GeneratedCardProblem
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.PageRangeError
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.ui.ai.AiDisclosureDialog
import com.yahyafati.mnemo.core.ui.ai.AiReport
import com.yahyafati.mnemo.core.ui.ai.AiReportKind
import com.yahyafati.mnemo.core.ui.ai.AiSetupPrompt
import com.yahyafati.mnemo.core.ui.ai.ReportAiButton
import com.yahyafati.mnemo.core.ui.ai.aiFailureText
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.deck.DeckEditorDialog
import com.yahyafati.mnemo.core.ui.files.fileDropTarget
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.permission.AppPermission
import com.yahyafati.mnemo.core.ui.permission.PermissionRationaleDialog
import com.yahyafati.mnemo.core.ui.permission.rememberPermissionRequest
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarFor
import com.yahyafati.mnemo.feature.create.component.DeckDropdown
import com.yahyafati.mnemo.feature.create.component.OptionDropdown
import com.yahyafati.mnemo.feature.create.component.editorFieldColors
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_accept_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_accept_into
import com.yahyafati.mnemo.feature.create.resources.feature_create_accept_no_deck
import com.yahyafati.mnemo.feature.create.resources.feature_create_accepted
import com.yahyafati.mnemo.feature.create.resources.feature_create_archetype_case
import com.yahyafati.mnemo.feature.create.resources.feature_create_archetype_choice
import com.yahyafati.mnemo.feature.create.resources.feature_create_archetype_cloze
import com.yahyafati.mnemo.feature.create.resources.feature_create_archetype_definition
import com.yahyafati.mnemo.feature.create.resources.feature_create_archetypes
import com.yahyafati.mnemo.feature.create.resources.feature_create_back
import com.yahyafati.mnemo.feature.create.resources.feature_create_nonempty_cancel
import com.yahyafati.mnemo.feature.create.resources.feature_create_nonempty_confirm
import com.yahyafati.mnemo.feature.create.resources.feature_create_nonempty_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_nonempty_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_discard_advance
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_discard_confirm
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_discard_keep
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_discard_start
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_discard_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_done
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_finish
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_hint_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_hint_idle
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_hint_review
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_hint_running
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_label
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_next
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_progress
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_skip
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_stop
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_back_matter
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_cut_short
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_front_matter
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_accept
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_discard
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_done
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_edit
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_number
import com.yahyafati.mnemo.feature.create.resources.feature_create_card_regenerate
import com.yahyafati.mnemo.feature.create.resources.feature_create_density
import com.yahyafati.mnemo.feature.create.resources.feature_create_density_balanced
import com.yahyafati.mnemo.feature.create.resources.feature_create_density_comprehensive
import com.yahyafati.mnemo.feature.create.resources.feature_create_density_concise
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_error
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_language
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_listening
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_permission
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_start
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_stop
import com.yahyafati.mnemo.feature.create.resources.feature_create_dictation_unavailable
import com.yahyafati.mnemo.feature.create.resources.feature_create_discard_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_disclosure_content
import com.yahyafati.mnemo.feature.create.resources.feature_create_drop_file
import com.yahyafati.mnemo.feature.create.resources.feature_create_drop_file_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_another
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_cancel
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_chapter_change
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_chapter_choose
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_chapter_current
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_chapter_none
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_chapters_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_epub_pick
import com.yahyafati.mnemo.feature.create.resources.feature_create_extra
import com.yahyafati.mnemo.feature.create.resources.feature_create_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_front
import com.yahyafati.mnemo.feature.create.resources.feature_create_generate
import com.yahyafati.mnemo.feature.create.resources.feature_create_generate_empty
import com.yahyafati.mnemo.feature.create.resources.feature_create_generating
import com.yahyafati.mnemo.feature.create.resources.feature_create_generating_part
import com.yahyafati.mnemo.feature.create.resources.feature_create_language
import com.yahyafati.mnemo.feature.create.resources.feature_create_language_source
import com.yahyafati.mnemo.feature.create.resources.feature_create_link_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_link_label
import com.yahyafati.mnemo.feature.create.resources.feature_create_link_read
import com.yahyafati.mnemo.feature.create.resources.feature_create_mic_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_mic_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_nothing_new
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_chapter_detail
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_chapter_detail_printed
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_chapters
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_chapters_pages
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_chapters_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_hint_long
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_label
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_malformed
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_out_of_range
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_printed
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_read
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_reversed
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pages_too_many
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_pick
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_replace_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_summary
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_back
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_broken_cloze
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_cloze
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_front
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_too_long
import com.yahyafati.mnemo.feature.create.resources.feature_create_problem_wrong
import com.yahyafati.mnemo.feature.create.resources.feature_create_queue
import com.yahyafati.mnemo.feature.create.resources.feature_create_queue_empty
import com.yahyafati.mnemo.feature.create.resources.feature_create_queue_skipped
import com.yahyafati.mnemo.feature.create.resources.feature_create_reading
import com.yahyafati.mnemo.feature.create.resources.feature_create_regenerate_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_retry
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_choose
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_done
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_lead
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_none
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_replace_confirm
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_replace_keep
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_replace_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_replace_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_summary
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_sections_title_untitled
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_label
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_manage
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_setup
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_via
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_clear
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_count
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_dictation
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_disambiguation
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_drm
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_encrypted
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_epub
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_from
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_http
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_invalid_url
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_link
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_no_text
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_not_an_article
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_paste
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_pdf
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_placeholder
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_requests
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_text
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_too_large
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_truncated
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_unavailable
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_unreachable
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_unsupported
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_words
import com.yahyafati.mnemo.feature.create.resources.feature_create_stop
import com.yahyafati.mnemo.feature.create.resources.feature_create_text
import com.yahyafati.mnemo.feature.create.resources.feature_create_type_basic
import com.yahyafati.mnemo.feature.create.resources.feature_create_type_cloze
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
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
    /** Opens the book import; null leaves its entry out. */
    onImportBook: (() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = uiState.message?.let { messageText(it) }
    LaunchedEffect(uiState.message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onAction(SmartExtractAction.MessageShown)
        }
    }

    // A PDF or a text file dragged from the file manager (desktop) becomes the source.
    var dropping by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxSize()
            .fileDropTarget(
                accepts = { DroppedFile.of(it) != null },
                onHover = { dropping = it },
            ) { paths -> onAction(SmartExtractAction.FileDropped(paths.first())) },
    ) {
        when {
            uiState.isLoading -> Unit
            uiState.route == null -> Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
                    AiSetupPrompt(
                        onSetUp = onSetUpAi,
                        message = stringResource(Res.string.feature_create_smart_setup),
                    )
                    // Making the decks of a book needs no provider.
                    if (onImportBook != null) BookImportEntry(onImportBook)
                }
            }
            else -> {
                val acceptable = uiState.acceptable.size
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
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
                    if (onImportBook != null) item(key = "book") { BookImportEntry(onImportBook, Modifier.widthIn(max = 680.dp)) }
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
                ScrollbarFor(listState)
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
        if (dropping) DropHint(Modifier.fillMaxSize())
    }

    uiState.disclosure?.let { route ->
        AiDisclosureDialog(
            providerName = route.provider.name,
            host = AiEndpoint.host(route.provider.baseUrl) ?: route.provider.baseUrl,
            whatIsSent = stringResource(Res.string.feature_create_disclosure_content),
            onAccept = { onAction(SmartExtractAction.AcceptDisclosure) },
            onDismiss = { onAction(SmartExtractAction.DismissDisclosure) },
        )
    }
    uiState.batchConfirmation?.let { confirmation ->
        BatchDiscardDialog(
            confirmation = confirmation,
            cards = uiState.queue.size,
            onConfirm = { onAction(SmartExtractAction.ConfirmBatchDiscard) },
            onDismiss = { onAction(SmartExtractAction.CancelBatchDiscard) },
        )
    }
    uiState.nonEmptyDeck?.let { deck ->
        AlertDialog(
            onDismissRequest = { onAction(SmartExtractAction.CancelNonEmptyDeck) },
            title = { Text(stringResource(Res.string.feature_create_nonempty_title)) },
            text = { Text(pluralStringResource(Res.plurals.feature_create_nonempty_message, deck.cards, deck.path, deck.cards)) },
            confirmButton = {
                TextButton(onClick = { onAction(SmartExtractAction.ConfirmNonEmptyDeck) }) {
                    Text(stringResource(Res.string.feature_create_nonempty_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SmartExtractAction.CancelNonEmptyDeck) }) {
                    Text(stringResource(Res.string.feature_create_nonempty_cancel))
                }
            },
        )
    }
    uiState.book?.takeIf { uiState.showChapters }?.let { book ->
        ChapterChooserDialog(
            book = book,
            selectedId = uiState.chapterId,
            onSelect = { onAction(SmartExtractAction.SelectChapter(it)) },
            onDismiss = { onAction(SmartExtractAction.DismissChapters) },
        )
    }
    uiState.sections?.takeIf { uiState.showSections }?.let { sections ->
        SectionChooserDialog(
            title = uiState.title,
            sections = sections,
            onToggle = { onAction(SmartExtractAction.ToggleSection(it)) },
            onSelectAll = { onAction(SmartExtractAction.SelectAllSections(it)) },
            onDismiss = { onAction(SmartExtractAction.DismissSections) },
        )
    }
    if (uiState.sectionsConfirmation) {
        AlertDialog(
            onDismissRequest = { onAction(SmartExtractAction.CancelSectionReplace) },
            title = { Text(stringResource(Res.string.feature_create_sections_replace_title)) },
            text = { Text(stringResource(Res.string.feature_create_sections_replace_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(SmartExtractAction.ConfirmSectionReplace) }) {
                    Text(stringResource(Res.string.feature_create_sections_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SmartExtractAction.CancelSectionReplace) }) {
                    Text(stringResource(Res.string.feature_create_sections_replace_keep))
                }
            },
        )
    }
    uiState.pdf?.takeIf { it.showChapters }?.let { pdf ->
        PdfChaptersDialog(
            pdf = pdf,
            reading = uiState.reading,
            onToggle = { onAction(SmartExtractAction.TogglePdfChapter(it)) },
            onRead = { onAction(SmartExtractAction.ApplyPdfChapters) },
            onDismiss = { onAction(SmartExtractAction.DismissPdfChapters) },
        )
    }
    if (uiState.pdf?.replaceConfirmation == true) {
        AlertDialog(
            onDismissRequest = { onAction(SmartExtractAction.CancelPdfReplace) },
            title = { Text(stringResource(Res.string.feature_create_sections_replace_title)) },
            text = { Text(stringResource(Res.string.feature_create_pdf_replace_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(SmartExtractAction.ConfirmPdfReplace) }) {
                    Text(stringResource(Res.string.feature_create_sections_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SmartExtractAction.CancelPdfReplace) }) {
                    Text(stringResource(Res.string.feature_create_sections_replace_keep))
                }
            },
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
    is ExtractMessage.Accepted -> pluralStringResource(Res.plurals.feature_create_accepted, message.cards, message.cards, message.deckPath)
    ExtractMessage.NothingNew -> stringResource(Res.string.feature_create_nothing_new)
    is ExtractMessage.BatchFinished -> pluralStringResource(Res.plurals.feature_create_batch_done, message.chapters, message.chapters)
    is ExtractMessage.RegenerateFailed -> stringResource(Res.string.feature_create_regenerate_failed, aiFailureText(message.failure))
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
                        text = stringResource(Res.string.feature_create_smart_label).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                    Text(stringResource(Res.string.feature_create_smart_title), style = MaterialTheme.typography.headlineSmall)
                }
            }

            uiState.batch?.let { BatchBanner(it, uiState.generation, onAction) }
            SourcePicker(uiState.sourceKind, uiState.dictationAvailable) { onAction(SmartExtractAction.SelectSource(it)) }
            if (LocalPlatformCapabilities.current.keyboardAndMouse) Hint(stringResource(Res.string.feature_create_drop_file_hint))
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

            // A book run generates by itself and moves on through the banner; a Generate button here would only invite a second request.
            if (uiState.batch == null || uiState.generation !is GenerationState.Done) GenerateControls(uiState, onAction)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MnemoIcons.Route, null, tint = colors.outline, modifier = Modifier.size(14.dp))
                Text(
                    text = stringResource(Res.string.feature_create_smart_via, route.provider.name, route.modelId),
                    style = MnemoTheme.typography.metricSm,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = spacing.xs),
                )
                TextButton(onClick = onSetUpAi) { Text(stringResource(Res.string.feature_create_smart_manage)) }
            }
        }
    }
}

/**
 * Where a book run is: "Chapter 3 of 8", its title, what to do now, and the two ways to leave this chapter. The
 * run goes on only when the user presses Next (or Skip), so a failed chapter waits here for Retry or Skip.
 */
@Composable
private fun BatchBanner(batch: BookBatch, generation: GenerationState, onAction: (SmartExtractAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Surface(shape = MaterialTheme.shapes.small, color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(Res.string.feature_create_batch_label).uppercase() + " · " +
                    stringResource(Res.string.feature_create_batch_progress, batch.position + 1, batch.total),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(batch.current.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(
                    when (generation) {
                        is GenerationState.Running -> Res.string.feature_create_batch_hint_running
                        is GenerationState.Done -> Res.string.feature_create_batch_hint_review
                        is GenerationState.Failed -> Res.string.feature_create_batch_hint_failed
                        GenerationState.Idle -> Res.string.feature_create_batch_hint_idle
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MnemoButton(
                    text = stringResource(
                        when {
                            batch.isLast -> Res.string.feature_create_batch_finish
                            generation is GenerationState.Done -> Res.string.feature_create_batch_next
                            else -> Res.string.feature_create_batch_skip
                        },
                    ),
                    onClick = { onAction(SmartExtractAction.BatchNext) },
                    style = MnemoButtonStyle.Secondary,
                )
                MnemoButton(
                    text = stringResource(Res.string.feature_create_batch_stop),
                    onClick = { onAction(SmartExtractAction.BatchStop) },
                    style = MnemoButtonStyle.Text,
                )
            }
        }
    }
}

/** Leaving a chapter, or starting a run, throws away the cards still in the queue: ask first. */
@Composable
private fun BatchDiscardDialog(confirmation: BatchConfirmation, cards: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feature_create_batch_discard_title)) },
        text = {
            Text(
                pluralStringResource(
                    when (confirmation) {
                        BatchConfirmation.Start -> Res.plurals.feature_create_batch_discard_start
                        BatchConfirmation.Advance -> Res.plurals.feature_create_batch_discard_advance
                    },
                    cards,
                    cards,
                ),
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.feature_create_batch_discard_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_create_batch_discard_keep)) } },
    )
}

@Composable
private fun SourcePicker(selected: SourceKind, dictationAvailable: Boolean, onSelect: (SourceKind) -> Unit) {
    // Dictation needs the platform to have it (a capability) and a recognizer to be there right now.
    val dictation = dictationAvailable && LocalPlatformCapabilities.current.dictation
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        SourceKind.entries.filter { dictation || it != SourceKind.Dictation }.forEach { kind ->
            MnemoChip(
                label = stringResource(
                    when (kind) {
                        SourceKind.Paste -> Res.string.feature_create_source_paste
                        SourceKind.Pdf -> Res.string.feature_create_source_pdf
                        SourceKind.Epub -> Res.string.feature_create_source_epub
                        SourceKind.Link -> Res.string.feature_create_source_link
                        SourceKind.Dictation -> Res.string.feature_create_source_dictation
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
                val picker = rememberFilePicker(listOf("application/pdf")) { onAction(SmartExtractAction.PdfPicked(it)) }
                MnemoButton(
                    text = stringResource(Res.string.feature_create_pdf_pick),
                    onClick = { picker.launch() },
                    style = MnemoButtonStyle.Secondary,
                    leadingIcon = MnemoIcons.Pdf,
                    enabled = !uiState.reading,
                )
                Hint(stringResource(Res.string.feature_create_pdf_hint))
                uiState.pdf?.let { PdfPages(it, reading = uiState.reading, onAction = onAction) }
            }
            SourceKind.Epub -> EpubControls(uiState, onAction)
            SourceKind.Link -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    OutlinedTextField(
                        value = uiState.link,
                        onValueChange = { onAction(SmartExtractAction.LinkChanged(it)) },
                        label = { Text(stringResource(Res.string.feature_create_link_label)) },
                        leadingIcon = { Icon(MnemoIcons.Link, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = editorFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onAction(SmartExtractAction.FetchLink) }),
                        modifier = Modifier.weight(1f),
                    )
                    MnemoButton(
                        text = stringResource(Res.string.feature_create_link_read),
                        onClick = { onAction(SmartExtractAction.FetchLink) },
                        style = MnemoButtonStyle.Secondary,
                        enabled = uiState.link.isNotBlank() && !uiState.reading,
                    )
                }
                Hint(stringResource(Res.string.feature_create_link_hint))
                uiState.sections?.let { sections ->
                    Hint(
                        pluralStringResource(
                            Res.plurals.feature_create_sections_summary,
                            sections.options.size,
                            sections.selected.size,
                            sections.options.size,
                            sections.selectedWords,
                        ),
                    )
                    MnemoButton(
                        text = stringResource(Res.string.feature_create_sections_choose),
                        onClick = { onAction(SmartExtractAction.ShowSections) },
                        style = MnemoButtonStyle.Secondary,
                        leadingIcon = MnemoIcons.Link,
                    )
                }
            }
            SourceKind.Dictation -> DictationControls(uiState, onAction)
        }
        if (uiState.reading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.feature_create_reading), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = MnemoTheme.spacing.sm))
            }
        }
        uiState.sourceProblem?.let {
            // A book that can't be read says so in the book's words; the others are about PDFs and pages.
            val text = if (uiState.sourceKind == SourceKind.Epub) bookProblemText(it) else sourceProblemText(it)
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

/** The open PDF: its page count and the Pages field that chooses which pages the box holds. */
@Composable
private fun PdfPages(pdf: PdfSummary, reading: Boolean, onAction: (SmartExtractAction) -> Unit) {
    val pageCount = pluralStringResource(Res.plurals.feature_create_pdf_pages, pdf.pageCount, pdf.pageCount)
    val count = pdf.printedPages?.let { stringResource(Res.string.feature_create_pdf_pages_printed, pageCount, it) } ?: pageCount
    Text(
        text = pdf.title?.let { stringResource(Res.string.feature_create_pdf_summary, it, count) } ?: count,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val long = pdf.pageCount > PdfInfo.MAX_PAGES
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        OutlinedTextField(
            value = pdf.pages,
            onValueChange = { onAction(SmartExtractAction.PdfPagesChanged(it)) },
            label = { Text(stringResource(Res.string.feature_create_pdf_pages_label)) },
            placeholder = { Text(stringResource(Res.string.feature_create_pdf_pages_all)) },
            supportingText = {
                Text(
                    pdf.error?.let { pdfPagesErrorText(it, pdf.pageCount) }
                        ?: if (long) {
                            stringResource(Res.string.feature_create_pdf_pages_hint_long, PdfInfo.MAX_PAGES)
                        } else {
                            stringResource(Res.string.feature_create_pdf_pages_hint)
                        },
                )
            },
            isError = pdf.error != null,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = editorFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAction(SmartExtractAction.ApplyPdfPages) }),
            modifier = Modifier.weight(1f),
        )
        MnemoButton(
            text = stringResource(Res.string.feature_create_pdf_pages_read),
            onClick = { onAction(SmartExtractAction.ApplyPdfPages) },
            style = MnemoButtonStyle.Secondary,
            enabled = !reading && pdf.error == null,
            modifier = Modifier.padding(top = MnemoTheme.spacing.xs),
        )
    }
    if (pdf.chapters.isNotEmpty()) {
        MnemoButton(
            text = stringResource(Res.string.feature_create_pdf_chapters),
            onClick = { onAction(SmartExtractAction.ShowPdfChapters) },
            style = MnemoButtonStyle.Secondary,
            enabled = !reading,
        )
    }
}

/** The open PDF's bookmarks: tick the chapters whose pages go in the Pages field, then read them. */
@Composable
private fun PdfChaptersDialog(pdf: PdfSummary, reading: Boolean, onToggle: (Int) -> Unit, onRead: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feature_create_pdf_chapters_title)) },
        text = {
            Column {
                Text(
                    text = pdf.error?.let { pdfPagesErrorText(it, pdf.pageCount) }
                        ?: stringResource(Res.string.feature_create_pdf_chapters_pages, pdf.pages.ifBlank { stringResource(Res.string.feature_create_pdf_pages_all) }),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pdf.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = MnemoTheme.spacing.sm),
                )
                PdfChapterList(pdf.chapters, pdf.selectedChapters, onToggle)
            }
        },
        confirmButton = {
            TextButton(onClick = onRead, enabled = !reading && pdf.error == null) {
                Text(stringResource(Res.string.feature_create_pdf_pages_read))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_create_sections_done)) } },
    )
}

/** A textbook can have hundreds of bookmarks, so the list is lazy. A sub-chapter is indented under its parent. */
@Composable
internal fun PdfChapterList(chapters: List<PdfChapterOption>, selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        items(chapters, key = { it.id }, contentType = { "chapter" }) { chapter ->
            PdfChapterRow(chapter, checked = chapter.id in selected, onToggle = { onToggle(chapter.id) })
        }
    }
}

/** One bookmark: the whole row toggles it, and reads as "title, page, pages" to a screen reader. */
@Composable
private fun PdfChapterRow(chapter: PdfChapterOption, checked: Boolean, onToggle: () -> Unit) {
    val length = pluralStringResource(Res.plurals.feature_create_pdf_pages, chapter.pages, chapter.pages)
    val detail = if (chapter.printedPage != null && chapter.printedPage != chapter.page.toString()) {
        stringResource(Res.string.feature_create_pdf_chapter_detail_printed, chapter.printedPage, chapter.page, length)
    } else {
        stringResource(Res.string.feature_create_pdf_chapter_detail, chapter.page, length)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .clickCursor()
            .padding(start = MnemoTheme.spacing.md * (chapter.level - 1).coerceIn(0, 4), top = MnemoTheme.spacing.sm, bottom = MnemoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = MnemoTheme.spacing.md)) {
            Text(chapter.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun pdfPagesErrorText(error: PdfPagesError, pageCount: Int): String = when (error) {
    is PdfPagesError.Invalid -> when (val e = error.error) {
        is PageRangeError.OutOfRange -> stringResource(Res.string.feature_create_pdf_pages_out_of_range, e.page, pageCount)
        is PageRangeError.Reversed -> stringResource(Res.string.feature_create_pdf_pages_reversed, e.start, e.end)
        PageRangeError.Empty, is PageRangeError.Malformed -> stringResource(Res.string.feature_create_pdf_pages_malformed)
    }
    is PdfPagesError.TooMany -> stringResource(Res.string.feature_create_pdf_pages_too_many, error.limit, error.selected)
}

/** The Epub source: pick a book, then one of its chapters, whose text goes in the box below. */
@Composable
private fun EpubControls(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    val picker = rememberFilePicker(listOf(EPUB_MIME_TYPE)) { onAction(SmartExtractAction.EpubPicked(it)) }
    val book = uiState.book
    if (book == null) {
        MnemoButton(
            text = stringResource(Res.string.feature_create_epub_pick),
            onClick = { picker.launch() },
            style = MnemoButtonStyle.Secondary,
            leadingIcon = MnemoIcons.Book,
            enabled = !uiState.reading,
        )
        Hint(stringResource(Res.string.feature_create_epub_hint))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val chapter = book.chapters.firstOrNull { it.id == uiState.chapterId }
        Hint(
            if (chapter != null) {
                stringResource(Res.string.feature_create_epub_chapter_current, chapter.title)
            } else {
                stringResource(Res.string.feature_create_epub_chapter_none)
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            MnemoButton(
                text = stringResource(if (chapter != null) Res.string.feature_create_epub_chapter_change else Res.string.feature_create_epub_chapter_choose),
                onClick = { onAction(SmartExtractAction.ShowChapters) },
                style = MnemoButtonStyle.Secondary,
                leadingIcon = MnemoIcons.Book,
            )
            MnemoButton(
                text = stringResource(Res.string.feature_create_epub_another),
                onClick = { picker.launch() },
                style = MnemoButtonStyle.Text,
                enabled = !uiState.reading,
            )
        }
    }
}

/**
 * The chapters of the book, one to choose. Front and back matter are listed, marked, like the import does.
 * A book can have hundreds, so the list is lazy.
 */
@Composable
private fun ChapterChooserDialog(
    book: BookSummary,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feature_create_epub_chapters_title, book.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = { ChapterOptionList(book, selectedId, onSelect) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_create_epub_cancel)) } },
    )
}

@Composable
internal fun ChapterOptionList(book: BookSummary, selectedId: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        items(book.chapters, key = { it.id }, contentType = { "chapter" }) { chapter ->
            ChapterOptionRow(chapter, selected = chapter.id == selectedId, onClick = { onSelect(chapter.id) })
        }
    }
}

/** One chapter: the whole row selects it, and reads as "title, details" to a screen reader. */
@Composable
private fun ChapterOptionRow(chapter: ChapterOption, selected: Boolean, onClick: () -> Unit) {
    val details = buildList {
        add(pluralStringResource(Res.plurals.feature_create_source_words, chapter.words, chapter.words))
        when (chapter.kind) {
            ChapterKind.Content -> Unit
            ChapterKind.FrontMatter -> add(stringResource(Res.string.feature_create_book_front_matter))
            ChapterKind.BackMatter -> add(stringResource(Res.string.feature_create_book_back_matter))
        }
        if (chapter.truncated) add(stringResource(Res.string.feature_create_book_cut_short))
    }.joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clickCursor()
            .padding(vertical = MnemoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = MnemoTheme.spacing.md)) {
            Text(chapter.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The sections of the page the link was read from: tick the ones whose text goes in the box. */
@Composable
private fun SectionChooserDialog(
    title: String?,
    sections: SectionsSummary,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (title != null) stringResource(Res.string.feature_create_sections_title, title) else stringResource(Res.string.feature_create_sections_title_untitled),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    TextButton(onClick = { onSelectAll(true) }) { Text(stringResource(Res.string.feature_create_sections_all)) }
                    TextButton(onClick = { onSelectAll(false) }) { Text(stringResource(Res.string.feature_create_sections_none)) }
                }
                SectionOptionList(sections, onToggle)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_create_sections_done)) } },
    )
}

/** An article can have dozens of sections, so the list is lazy. A subsection is indented under its parent. */
@Composable
internal fun SectionOptionList(sections: SectionsSummary, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        items(sections.options, key = { it.id }, contentType = { "section" }) { section ->
            SectionOptionRow(section, checked = section.id in sections.selected, onToggle = { onToggle(section.id) })
        }
    }
}

/** One section: the whole row toggles it, and reads as "title, words" to a screen reader. */
@Composable
private fun SectionOptionRow(section: SectionOption, checked: Boolean, onToggle: () -> Unit) {
    val title = section.title ?: stringResource(Res.string.feature_create_sections_lead)
    val words = pluralStringResource(Res.plurals.feature_create_source_words, section.words, section.words)
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .clickCursor()
            .padding(start = MnemoTheme.spacing.md * (section.level - 2).coerceIn(0, 4), top = MnemoTheme.spacing.sm, bottom = MnemoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = MnemoTheme.spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(words, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DictationControls(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    val microphone = rememberPermissionRequest(AppPermission.Microphone) { granted ->
        onAction(if (granted) SmartExtractAction.StartDictation else SmartExtractAction.DictationPermissionDenied)
    }
    var explainMicrophone by rememberSaveable { mutableStateOf(false) }
    val listening = uiState.dictation as? DictationState.Listening
    MnemoButton(
        text = stringResource(if (listening != null) Res.string.feature_create_dictation_stop else Res.string.feature_create_dictation_start),
        onClick = {
            when {
                listening != null -> onAction(SmartExtractAction.StopDictation)
                microphone.isGranted -> onAction(SmartExtractAction.StartDictation)
                else -> explainMicrophone = true
            }
        },
        style = if (listening != null) MnemoButtonStyle.Primary else MnemoButtonStyle.Secondary,
        leadingIcon = if (listening != null) MnemoIcons.Stop else MnemoIcons.Mic,
    )
    if (explainMicrophone) {
        PermissionRationaleDialog(
            icon = MnemoIcons.Mic,
            title = stringResource(Res.string.feature_create_mic_title),
            message = stringResource(Res.string.feature_create_mic_message),
            onContinue = {
                explainMicrophone = false
                microphone.launch()
            },
            onNotNow = { explainMicrophone = false },
        )
    }
    when (val dictation = uiState.dictation) {
        is DictationState.Listening -> Text(
            text = dictation.partial.ifEmpty { stringResource(Res.string.feature_create_dictation_listening) },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
        is DictationState.Failed -> Text(
            text = stringResource(
                when (dictation.problem) {
                    DictationProblem.Unavailable -> Res.string.feature_create_dictation_unavailable
                    DictationProblem.NoPermission -> Res.string.feature_create_dictation_permission
                    DictationProblem.LanguageUnavailable -> Res.string.feature_create_dictation_language
                    DictationProblem.RecognizerError -> Res.string.feature_create_dictation_error
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        DictationState.Off -> Hint(stringResource(Res.string.feature_create_dictation_hint))
    }
}

@Composable
private fun SourceTextField(uiState: SmartExtractUiState, onAction: (SmartExtractAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        uiState.title?.let {
            Text(
                text = stringResource(Res.string.feature_create_source_from, it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedTextField(
            value = uiState.text,
            onValueChange = { onAction(SmartExtractAction.TextChanged(it)) },
            label = { Text(stringResource(Res.string.feature_create_source_text)) },
            placeholder = { Text(stringResource(Res.string.feature_create_source_placeholder)) },
            supportingText = {
                val words = pluralStringResource(Res.plurals.feature_create_source_words, uiState.wordCount, uiState.wordCount)
                // A text that takes several requests is worth saying so, since each one is sent to the provider.
                Text(
                    if (uiState.requests > 1) {
                        stringResource(
                            Res.string.feature_create_source_count,
                            words,
                            pluralStringResource(Res.plurals.feature_create_source_requests, uiState.requests, uiState.requests),
                        )
                    } else {
                        words
                    },
                )
            },
            trailingIcon = if (uiState.text.isNotEmpty()) {
                {
                    MnemoIconButton(
                        icon = MnemoIcons.Close,
                        contentDescription = stringResource(Res.string.feature_create_source_clear),
                        onClick = { onAction(SmartExtractAction.ClearText) },
                    )
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
        if (uiState.truncated) Hint(stringResource(Res.string.feature_create_source_truncated))
        if (uiState.disambiguation) Hint(stringResource(Res.string.feature_create_source_disambiguation))
    }
}

@Composable
private fun DensitySlider(uiState: SmartExtractUiState, onSelect: (ExtractDensity) -> Unit) {
    val density = uiState.options.density
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(stringResource(Res.string.feature_create_density), Modifier.weight(1f))
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
        ExtractDensity.Concise -> Res.string.feature_create_density_concise
        ExtractDensity.Balanced -> Res.string.feature_create_density_balanced
        ExtractDensity.Comprehensive -> Res.string.feature_create_density_comprehensive
    },
)

@Composable
private fun ArchetypePicker(selected: Set<CardArchetype>, onToggle: (CardArchetype) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Label(stringResource(Res.string.feature_create_archetypes))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            CardArchetype.entries.forEach { archetype ->
                MnemoChip(
                    label = stringResource(
                        when (archetype) {
                            CardArchetype.Definition -> Res.string.feature_create_archetype_definition
                            CardArchetype.Cloze -> Res.string.feature_create_archetype_cloze
                            CardArchetype.MultipleChoice -> Res.string.feature_create_archetype_choice
                            CardArchetype.CaseStudy -> Res.string.feature_create_archetype_case
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
    val sameAsSource = stringResource(Res.string.feature_create_language_source)
    OptionDropdown(
        value = language ?: sameAsSource,
        label = stringResource(Res.string.feature_create_language),
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
                        stringResource(Res.string.feature_create_generating_part, generation.part + 1, generation.parts)
                    } else {
                        stringResource(Res.string.feature_create_generating)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                MnemoButton(
                    text = stringResource(Res.string.feature_create_stop),
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
                            text = stringResource(Res.string.feature_create_failed, aiFailureText(generation.failure)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .padding(MnemoTheme.spacing.sm),
                        )
                        TextButton(onClick = { onAction(SmartExtractAction.Retry) }) { Text(stringResource(Res.string.feature_create_retry)) }
                    }
                }
            }
            val estimate = uiState.estimatedCards
            MnemoButton(
                text = if (estimate > 0) {
                    pluralStringResource(Res.plurals.feature_create_generate, estimate, estimate)
                } else {
                    stringResource(Res.string.feature_create_generate_empty)
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
                text = stringResource(Res.string.feature_create_queue).uppercase() + if (uiState.queue.isNotEmpty()) " (${uiState.queue.size})" else "",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.weight(1f), color = colors.surfaceContainerHighest)
        }
        if (uiState.queue.isEmpty()) {
            Hint(stringResource(Res.string.feature_create_queue_empty), Modifier.align(Alignment.CenterHorizontally))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val skipped = uiState.duplicatesSkipped + uiState.invalidSkipped
                Hint(
                    text = if (skipped > 0) pluralStringResource(Res.plurals.feature_create_queue_skipped, skipped, skipped) else "",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAction(SmartExtractAction.DiscardAll) }) { Text(stringResource(Res.string.feature_create_discard_all)) }
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
                Text(stringResource(Res.string.feature_create_card_number, number.toString().padStart(2, '0')).uppercase(), style = MnemoTheme.typography.metricSm, color = colors.onSurfaceVariant)
                Text(
                    text = stringResource(if (isCloze) Res.string.feature_create_type_cloze else Res.string.feature_create_type_basic),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.secondary,
                    modifier = Modifier.padding(start = spacing.sm),
                )
                Box(Modifier.weight(1f))
                MnemoIconButton(
                    icon = MnemoIcons.Regenerate,
                    contentDescription = stringResource(Res.string.feature_create_card_regenerate),
                    onClick = { onAction(SmartExtractAction.Regenerate(card.id)) },
                    enabled = !item.regenerating,
                )
                MnemoIconButton(
                    icon = if (editing) MnemoIcons.Check else MnemoIcons.Edit,
                    contentDescription = stringResource(if (editing) Res.string.feature_create_card_done else Res.string.feature_create_card_edit),
                    onClick = { onAction(if (editing) SmartExtractAction.DoneEditing else SmartExtractAction.Edit(card.id)) },
                    enabled = !item.regenerating,
                )
                ReportAiButton(AiReport.ofFields(AiReportKind.SmartExtractCard, card.fields, modelId), compact = true)
                MnemoIconButton(
                    icon = MnemoIcons.Delete,
                    contentDescription = stringResource(Res.string.feature_create_card_discard),
                    onClick = { onAction(SmartExtractAction.Discard(card.id)) },
                )
            }
            if (item.regenerating) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (editing) {
                OutlinedTextField(
                    value = card.front,
                    onValueChange = { onAction(SmartExtractAction.EditFront(card.id, it)) },
                    label = { Text(stringResource(if (isCloze) Res.string.feature_create_text else Res.string.feature_create_front)) },
                    textStyle = MnemoTheme.typography.studyPromptCompact,
                    shape = MaterialTheme.shapes.small,
                    colors = editorFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = card.back,
                    onValueChange = { onAction(SmartExtractAction.EditBack(card.id, it)) },
                    label = { Text(stringResource(if (isCloze) Res.string.feature_create_extra else Res.string.feature_create_back)) },
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
                    text = stringResource(Res.string.feature_create_card_accept),
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
        GeneratedCardProblem.EmptyFront -> Res.string.feature_create_problem_front
        GeneratedCardProblem.EmptyBack -> Res.string.feature_create_problem_back
        GeneratedCardProblem.NoCloze -> Res.string.feature_create_problem_cloze
        GeneratedCardProblem.BrokenCloze -> Res.string.feature_create_problem_broken_cloze
        GeneratedCardProblem.TooLong -> Res.string.feature_create_problem_too_long
        GeneratedCardProblem.NoWrongAnswers -> Res.string.feature_create_problem_wrong
    },
)

@Composable
private fun sourceProblemText(problem: SourceProblem): String = stringResource(
    when (problem) {
        SourceProblem.NoText -> Res.string.feature_create_source_no_text
        SourceProblem.Encrypted -> Res.string.feature_create_source_encrypted
        SourceProblem.Unsupported -> Res.string.feature_create_source_unsupported
        SourceProblem.TooLarge -> Res.string.feature_create_source_too_large
        SourceProblem.InvalidUrl -> Res.string.feature_create_source_invalid_url
        SourceProblem.Unreachable -> Res.string.feature_create_source_unreachable
        SourceProblem.HttpError -> Res.string.feature_create_source_http
        SourceProblem.FileUnavailable -> Res.string.feature_create_source_unavailable
        SourceProblem.NotAnArticle -> Res.string.feature_create_source_not_an_article
        SourceProblem.Drm -> Res.string.feature_create_source_drm
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
                text = deckPath?.let { stringResource(Res.string.feature_create_accept_into, it) } ?: stringResource(Res.string.feature_create_accept_no_deck),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MnemoTheme.spacing.sm),
            )
            MnemoButton(text = pluralStringResource(Res.plurals.feature_create_accept_all, count, count), onClick = onAcceptAll)
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Over the whole screen while a file is dragged above it: what dropping does. */
@Composable
private fun DropHint(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(colors.primaryContainer.copy(alpha = 0.85f))
            .border(2.dp, colors.primary, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.feature_create_drop_file),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onPrimaryContainer,
        )
    }
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
