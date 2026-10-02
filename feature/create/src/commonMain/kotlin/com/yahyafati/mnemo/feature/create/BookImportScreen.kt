package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.component.clickCursor
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.ui.ai.AiSetupPrompt
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarFor
import com.yahyafati.mnemo.feature.create.component.editorFieldColors
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_cancel
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_chapters
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_cost_local
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_cost_remote
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_how
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_requests
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_setup
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_start
import com.yahyafati.mnemo.feature.create.resources.feature_create_batch_confirm_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_another
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_back
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_back_matter
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_by
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_chapters
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_choose
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_create
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_create_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_creating
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_cut_short
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_done
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_done_created
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_done_next
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_done_reused
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_done_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_empty_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_empty_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_entry_message
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_entry_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_exists_note
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_front_matter
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_generate
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_generate_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_has_deck
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_name
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_name_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_no_text
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_reading
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_select_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_select_content
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_select_none
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_summary
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_truncated
import com.yahyafati.mnemo.feature.create.resources.feature_create_book_unsupported
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_drm
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_too_large
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_unavailable
import com.yahyafati.mnemo.feature.create.resources.feature_create_source_words
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal const val EPUB_MIME_TYPE = "application/epub+zip"

/** The book import as its own screen (a route, not a tab), so it owns its top bar. */
@Composable
internal fun BookImportFullScreen(
    onClose: () -> Unit,
    onGenerateCards: () -> Unit,
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookImportViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // A confirmed book run is with Smart Extract now: go there.
    LaunchedEffect(viewModel) {
        viewModel.runStarted.collect { onGenerateCards() }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(Res.string.feature_create_book_title),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.feature_create_book_back),
                        onClick = onClose,
                    )
                },
            )
        },
    ) { padding ->
        BookImportScreen(
            uiState = uiState,
            onAction = viewModel::onAction,
            onClose = onClose,
            onGenerateCards = onGenerateCards,
            onSetUpAi = onSetUpAi,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        )
    }
}

/**
 * Pick a book, choose its chapters, create the decks (docs/epub/ROADMAP.md, B4). Stateless apart from
 * the file picker. No AI is involved here: the decks start empty.
 */
@Composable
internal fun BookImportScreen(
    uiState: BookImportUiState,
    onAction: (BookImportAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Leaves for Smart Extract, which takes the book (see [BookImportAction.GenerateCards]). */
    onGenerateCards: () -> Unit = {},
    /** Opens the AI provider settings, from the book run's confirmation when no provider is set up. */
    onSetUpAi: () -> Unit = {},
) {
    val picker = rememberFilePicker(listOf(EPUB_MIME_TYPE)) { onAction(BookImportAction.FilePicked(it)) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            uiState.reading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md, Alignment.CenterVertically),
            ) {
                CircularProgressIndicator()
                Text(stringResource(Res.string.feature_create_book_reading), style = MaterialTheme.typography.bodyMedium)
            }
            uiState.created != null -> BookCreated(
                result = uiState.created,
                onDone = onClose,
                onAnother = { onAction(BookImportAction.Reset) },
                onGenerate = {
                    onAction(BookImportAction.GenerateCards)
                    onGenerateCards()
                },
            )
            uiState.book != null -> ChapterPicker(uiState, uiState.book, onAction)
            else -> EmptyState(
                icon = MnemoIcons.Book,
                title = stringResource(Res.string.feature_create_book_empty_title),
                message = uiState.problem?.let { bookProblemText(it) } ?: stringResource(Res.string.feature_create_book_empty_message),
                modifier = Modifier.widthIn(max = 680.dp),
                action = { MnemoButton(text = stringResource(Res.string.feature_create_book_choose), onClick = picker::launch, leadingIcon = MnemoIcons.FileUpload) },
            )
        }
    }
    uiState.batchPlan?.let { plan ->
        BatchConfirmDialog(
            plan = plan,
            route = uiState.route,
            onConfirm = { onAction(BookImportAction.ConfirmBatch) },
            onDismiss = { onAction(BookImportAction.DismissBatch) },
            onSetUp = {
                onAction(BookImportAction.DismissBatch)
                onSetUpAi()
            },
        )
    }
}

/**
 * What "Create decks and generate" would send, before it sends anything: the chapters, the words, the number of
 * AI requests and where they go. A model on the user's own machine costs nothing but time, and says so. Without a
 * provider the dialog is the setup prompt instead.
 */
@Composable
private fun BatchConfirmDialog(
    plan: BatchPlan,
    route: AiRoute?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onSetUp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.feature_create_batch_confirm_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                Text(
                    pluralStringResource(Res.plurals.feature_create_batch_confirm_chapters, plan.chapterIds.size, plan.chapterIds.size, plan.words),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (route == null) {
                    AiSetupPrompt(onSetUp = onSetUp, message = stringResource(Res.string.feature_create_batch_confirm_setup))
                } else {
                    Text(
                        pluralStringResource(Res.plurals.feature_create_batch_confirm_requests, plan.requests, plan.requests, route.provider.name, route.modelId),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (route.provider.isLocal) {
                            stringResource(Res.string.feature_create_batch_confirm_cost_local)
                        } else {
                            stringResource(Res.string.feature_create_batch_confirm_cost_remote)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.feature_create_batch_confirm_how),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (route != null) TextButton(onClick = onConfirm) { Text(stringResource(Res.string.feature_create_batch_confirm_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_create_batch_confirm_cancel)) } },
    )
}

@Composable
private fun ChapterPicker(
    uiState: BookImportUiState,
    book: BookSource,
    onAction: (BookImportAction) -> Unit,
) {
    val spacing = MnemoTheme.spacing
    Column(Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = spacing.screenMargin, vertical = spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "header") {
                    BookHeader(uiState, book, onAction, Modifier.widthIn(max = 680.dp))
                }
                items(book.chapters, key = { it.id }, contentType = { "chapter" }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        words = uiState.wordCounts[chapter.id] ?: 0,
                        checked = chapter.id in uiState.checked,
                        hasDeck = chapter.id in uiState.existing,
                        onToggle = { onAction(BookImportAction.ToggleChapter(chapter.id)) },
                        modifier = Modifier.widthIn(max = 680.dp),
                    )
                }
            }
            ScrollbarFor(listState)
        }
        CreateBar(uiState, onAction, Modifier.widthIn(max = 680.dp).padding(horizontal = spacing.screenMargin, vertical = spacing.sm))
    }
}

@Composable
private fun BookHeader(
    uiState: BookImportUiState,
    book: BookSource,
    onAction: (BookImportAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = book.title.ifBlank { uiState.bookName },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        book.author?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(Res.string.feature_create_book_by, it), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        OutlinedTextField(
            value = uiState.bookName,
            onValueChange = { onAction(BookImportAction.BookNameChanged(it)) },
            label = { Text(stringResource(Res.string.feature_create_book_name)) },
            supportingText = { Text(stringResource(Res.string.feature_create_book_name_hint)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            colors = editorFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
        )
        if (book.truncated) {
            Text(stringResource(Res.string.feature_create_book_truncated), style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
        Text(
            text = stringResource(Res.string.feature_create_book_chapters),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.sm),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MnemoChip(stringResource(Res.string.feature_create_book_select_all), selected = false, onClick = { onAction(BookImportAction.SelectAll) })
            MnemoChip(stringResource(Res.string.feature_create_book_select_content), selected = false, onClick = { onAction(BookImportAction.SelectContent) })
            MnemoChip(stringResource(Res.string.feature_create_book_select_none), selected = false, onClick = { onAction(BookImportAction.SelectNone) })
        }
        Text(
            text = pluralStringResource(
                Res.plurals.feature_create_book_summary,
                book.chapters.size,
                uiState.checked.size,
                book.chapters.size,
                uiState.selectedWords,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        val reused = uiState.checked.count { it in uiState.existing }
        if (reused > 0) {
            Text(stringResource(Res.string.feature_create_book_exists_note, reused), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = spacing.xs))
    }
}

/** One chapter: the whole row toggles it, and reads as "title, details" to a screen reader. */
@Composable
private fun ChapterRow(
    chapter: BookChapter,
    words: Int,
    checked: Boolean,
    hasDeck: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    val details = buildList {
        add(pluralStringResource(Res.plurals.feature_create_source_words, words, words))
        when (chapter.kind) {
            ChapterKind.Content -> Unit
            ChapterKind.FrontMatter -> add(stringResource(Res.string.feature_create_book_front_matter))
            ChapterKind.BackMatter -> add(stringResource(Res.string.feature_create_book_back_matter))
        }
        if (chapter.truncated) add(stringResource(Res.string.feature_create_book_cut_short))
        if (hasDeck) add(stringResource(Res.string.feature_create_book_has_deck))
    }.joinToString(" · ")
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .clickCursor()
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = spacing.md)) {
            Text(chapter.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreateBar(uiState: BookImportUiState, onAction: (BookImportAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        if (uiState.createFailed) {
            Text(stringResource(Res.string.feature_create_book_create_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        MnemoButton(
            text = if (uiState.creating) {
                stringResource(Res.string.feature_create_book_creating)
            } else {
                pluralStringResource(Res.plurals.feature_create_book_create, uiState.checked.size, uiState.checked.size)
            },
            onClick = { onAction(BookImportAction.Create) },
            enabled = uiState.canCreate,
            modifier = Modifier.fillMaxWidth(),
        )
        MnemoButton(
            text = stringResource(Res.string.feature_create_book_generate_all),
            onClick = { onAction(BookImportAction.ShowBatch) },
            style = MnemoButtonStyle.Secondary,
            leadingIcon = MnemoIcons.Sparkle,
            enabled = uiState.canGenerate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BookCreated(result: BookImportResult, onDone: () -> Unit, onAnother: () -> Unit, onGenerate: () -> Unit) {
    val spacing = MnemoTheme.spacing
    EmptyState(
        icon = MnemoIcons.CheckCircle,
        title = stringResource(Res.string.feature_create_book_done_title),
        message = buildList {
            if (result.newDecks > 0) add(pluralStringResource(Res.plurals.feature_create_book_done_created, result.newDecks, result.newDecks, result.bookDeck))
            if (result.reusedDecks > 0) add(pluralStringResource(Res.plurals.feature_create_book_done_reused, result.reusedDecks, result.reusedDecks))
            add(stringResource(Res.string.feature_create_book_done_next))
        }.joinToString(" "),
        modifier = Modifier.widthIn(max = 680.dp),
        action = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MnemoButton(text = stringResource(Res.string.feature_create_book_generate), onClick = onGenerate, leadingIcon = MnemoIcons.Sparkle)
                MnemoButton(text = stringResource(Res.string.feature_create_book_done), onClick = onDone, style = MnemoButtonStyle.Secondary)
                MnemoButton(text = stringResource(Res.string.feature_create_book_another), onClick = onAnother, style = MnemoButtonStyle.Text)
            }
        },
    )
}

/** The Create tab's way in: a short card above Smart Extract. Needs no AI provider. */
@Composable
internal fun BookImportEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
    ) {
        Row(Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.Book, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = spacing.md)) {
                Text(stringResource(Res.string.feature_create_book_entry_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.feature_create_book_entry_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
            MnemoButton(text = stringResource(Res.string.feature_create_book_choose), onClick = onClick, style = MnemoButtonStyle.Text)
        }
    }
}

@Composable
internal fun bookProblemText(problem: SourceProblem): String = stringResource(
    when (problem) {
        SourceProblem.Drm -> Res.string.feature_create_source_drm
        SourceProblem.NoText -> Res.string.feature_create_book_no_text
        SourceProblem.TooLarge -> Res.string.feature_create_source_too_large
        SourceProblem.FileUnavailable -> Res.string.feature_create_source_unavailable
        // A book is read from a file: the PDF and link problems can't come up, and a book that isn't one is Unsupported.
        SourceProblem.Unsupported,
        SourceProblem.Encrypted,
        SourceProblem.InvalidUrl,
        SourceProblem.NotAnArticle,
        SourceProblem.Unreachable,
        SourceProblem.HttpError,
        -> Res.string.feature_create_book_unsupported
    },
)

private fun previewBook() = BookSource(
    title = "The Origin of Species",
    author = "Charles Darwin",
    chapters = listOf(
        BookChapter(0, "Title page", "Charles Darwin", ChapterKind.FrontMatter),
        BookChapter(1, "Variation under domestication", "word ".repeat(5200)),
        BookChapter(2, "Struggle for existence", "word ".repeat(4100)),
        BookChapter(3, "Index", "word ".repeat(900), ChapterKind.BackMatter),
    ),
)

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun BookImportScreenPreview() {
    val book = previewBook()
    MnemoTheme {
        BookImportScreen(
            uiState = BookImportUiState(
                book = book,
                wordCounts = book.chapters.associate { it.id to it.wordCount },
                bookName = book.title,
                checked = setOf(1, 2),
                existing = setOf(1),
            ),
            onAction = {},
            onClose = {},
        )
    }
}
