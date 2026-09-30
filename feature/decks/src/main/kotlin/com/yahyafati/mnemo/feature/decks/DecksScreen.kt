package com.yahyafati.mnemo.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.StatTile
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.RetentionOverview
import com.yahyafati.mnemo.core.model.TodaySummary
import com.yahyafati.mnemo.core.ui.deck.DeckEditorDialog
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.files.rememberFileSaver
import com.yahyafati.mnemo.core.ui.permission.AppPermission
import com.yahyafati.mnemo.core.ui.permission.PermissionRationaleDialog
import com.yahyafati.mnemo.core.ui.permission.rememberPermissionRequest
import com.yahyafati.mnemo.feature.decks.component.DailyMixCard
import com.yahyafati.mnemo.feature.decks.component.DeckCallbacks
import com.yahyafati.mnemo.feature.decks.component.DeckCard
import com.yahyafati.mnemo.feature.decks.component.TransferBanner
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun DecksScreen(
    onStudyDeck: (deckId: String) -> Unit,
    onStartDailyMix: () -> Unit,
    onAddCards: (deckId: String) -> Unit,
    onBrowse: (deckId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DecksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // .apkg and .colpkg have no registered MIME type, so the picker shows every file.
    val importPicker = rememberFilePicker { viewModel.onAction(DecksAction.Import(it)) }
    // The progress notification needs permission on some platforms; the import runs either way.
    val notifications = rememberPermissionRequest(AppPermission.Notifications) { importPicker.launch() }
    var explainNotifications by rememberSaveable { mutableStateOf(false) }
    var exportDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val exportPicker = rememberFileSaver(APKG_MIME) { uri ->
        exportDeckId?.let { viewModel.onAction(DecksAction.Export(it, uri)) }
        exportDeckId = null
    }
    DecksScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onStudyDeck = onStudyDeck,
        onStartDailyMix = onStartDailyMix,
        onAddCards = onAddCards,
        onBrowse = onBrowse,
        onImport = { if (notifications.canRequest) explainNotifications = true else importPicker.launch() },
        onExportDeck = { deckId, name ->
            exportDeckId = deckId
            exportPicker.launch("$name.apkg")
        },
        modifier = modifier,
    )
    if (explainNotifications) {
        PermissionRationaleDialog(
            icon = MnemoIcons.Notifications,
            title = stringResource(R.string.feature_decks_notifications_title),
            message = stringResource(R.string.feature_decks_notifications_message),
            onContinue = {
                explainNotifications = false
                notifications.launch()
            },
            // Declining only means no progress notification: the import goes ahead.
            onNotNow = {
                explainNotifications = false
                importPicker.launch()
            },
        )
    }
}

private const val APKG_MIME = "application/octet-stream"

/** Narrowest a deck card gets before the grid drops a column. */
private val DECK_MIN_WIDTH = 340.dp

private val FULL_WIDTH: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

@Composable
internal fun DecksScreen(
    uiState: DecksUiState,
    onAction: (DecksAction) -> Unit,
    onStudyDeck: (deckId: String) -> Unit,
    onStartDailyMix: () -> Unit,
    onAddCards: (deckId: String) -> Unit,
    onBrowse: (deckId: String?) -> Unit,
    onImport: () -> Unit,
    onExportDeck: (deckId: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MnemoTheme.spacing
    if (uiState.isLoading) {
        Box(modifier.fillMaxSize())
        return
    }
    val callbacks = DeckCallbacks(
        onStudy = onStudyDeck,
        onAddCards = onAddCards,
        onToggleStar = { onAction(DecksAction.ToggleStar(it)) },
        onToggleExpanded = { onAction(DecksAction.ToggleExpanded(it)) },
        onEdit = { onAction(DecksAction.EditDeck(it)) },
        onDelete = { onAction(DecksAction.DeleteDeck(it)) },
        onBrowse = onBrowse,
        onExport = onExportDeck,
    )
    // One column on phones; a grid of deck cards on tablets and unfolded foldables, as in the
    // mockup's desktop layout. Everything but the decks spans the full width.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = DECK_MIN_WIDTH),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "header", span = FULL_WIDTH) { Header(uiState) }
        item(key = "transfers", span = FULL_WIDTH) {
            TransferBanner(
                importState = uiState.importState,
                exportState = uiState.exportState,
                onDismiss = { onAction(DecksAction.DismissTransfer) },
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        if (uiState.hasDecks) {
            item(key = "mix", span = FULL_WIDTH) { DailyMixCard(uiState.today, onStart = onStartDailyMix, Modifier.padding(top = spacing.xs)) }
            item(key = "stats", span = FULL_WIDTH) { StatsStrip(uiState.today, uiState.retention, Modifier.padding(top = spacing.sm)) }
            item(key = "search", span = FULL_WIDTH) { SearchAndFilters(uiState, onAction, Modifier.padding(top = spacing.md)) }
            item(key = "library", span = FULL_WIDTH) {
                LibraryBar(
                    onNewDeck = { onAction(DecksAction.NewDeck) },
                    onImport = onImport,
                    onBrowse = { onBrowse(null) },
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
            gridItems(uiState.decks, key = { it.id }) { deck ->
                DeckCard(deck = deck, now = uiState.now, callbacks = callbacks)
            }
            if (uiState.decks.isEmpty()) {
                item(key = "no-match", span = FULL_WIDTH) {
                    EmptyState(
                        icon = MnemoIcons.Search,
                        title = stringResource(R.string.feature_decks_no_match_title),
                        message = stringResource(R.string.feature_decks_no_match_message),
                        action = {
                            MnemoButton(
                                text = stringResource(R.string.feature_decks_clear_filters),
                                onClick = {
                                    onAction(DecksAction.QueryChanged(""))
                                    onAction(DecksAction.FilterSelected(DeckFilter.All))
                                },
                            )
                        },
                    )
                }
            }
        } else {
            item(key = "empty", span = FULL_WIDTH) {
                EmptyState(
                    icon = MnemoIcons.Decks,
                    title = stringResource(R.string.feature_decks_empty_title),
                    message = stringResource(R.string.feature_decks_empty_message),
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            MnemoButton(
                                text = stringResource(R.string.feature_decks_new_deck),
                                onClick = { onAction(DecksAction.NewDeck) },
                                leadingIcon = MnemoIcons.Add,
                            )
                            MnemoButton(
                                text = stringResource(R.string.feature_decks_import),
                                onClick = onImport,
                                style = MnemoButtonStyle.Text,
                                leadingIcon = MnemoIcons.FileUpload,
                            )
                        }
                    },
                )
            }
        }
    }

    DecksDialogs(uiState.dialog, onAction)
}

@Composable
private fun Header(uiState: DecksUiState) {
    val colors = MaterialTheme.colorScheme
    val today = uiState.today
    Column(Modifier.padding(top = MnemoTheme.spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = uiState.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (today.totalToStudy > 0) {
                Row(
                    modifier = Modifier
                        .background(colors.surfaceContainer, MaterialTheme.shapes.large)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(MnemoIcons.Schedule, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                    Text(
                        text = stringResource(R.string.feature_decks_estimate, today.estimatedMinutes),
                        style = MnemoTheme.typography.metricSm,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = stringResource(
                when (uiState.greeting) {
                    Greeting.Morning -> R.string.feature_decks_greeting_morning
                    Greeting.Afternoon -> R.string.feature_decks_greeting_afternoon
                    Greeting.Evening -> R.string.feature_decks_greeting_evening
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = MnemoTheme.spacing.sm),
        )
        Text(
            text = when {
                !uiState.hasDecks -> stringResource(R.string.feature_decks_headline_first)
                today.totalToStudy == 0 -> stringResource(R.string.feature_decks_headline_done)
                else -> pluralStringResource(R.plurals.feature_decks_headline_cards, today.totalToStudy, today.totalToStudy)
            },
            style = MaterialTheme.typography.displayMedium,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun StatsStrip(today: TodaySummary, retention: RetentionOverview, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        StatTile(
            label = stringResource(R.string.feature_decks_stat_streak),
            value = today.streakDays.toString(),
            unit = pluralStringResource(R.plurals.feature_decks_stat_days, today.streakDays),
            icon = MnemoIcons.Streak,
            iconTint = colors.tertiary,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.feature_decks_stat_retained),
            value = retention.retention?.let { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(it) }
                ?: stringResource(R.string.feature_decks_stat_none),
            icon = MnemoIcons.TrendingUp,
            iconTint = colors.secondary,
            valueColor = if (retention.retention == null) colors.onSurface else colors.secondary,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.feature_decks_stat_mastered),
            value = NumberFormat.getIntegerInstance().format(retention.matureCards),
            icon = MnemoIcons.CheckCircle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchAndFilters(uiState: DecksUiState, onAction: (DecksAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = { onAction(DecksAction.QueryChanged(it)) },
            placeholder = { Text(stringResource(R.string.feature_decks_search), style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(MnemoIcons.Search, null, tint = colors.outline) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedBorderColor = colors.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val counts = uiState.filterCounts
        val filters = buildList {
            add(DeckFilter.All to stringResource(R.string.feature_decks_filter_all, counts.all))
            add(DeckFilter.Due to stringResource(R.string.feature_decks_filter_due, counts.due))
            add(DeckFilter.Starred to stringResource(R.string.feature_decks_filter_starred, counts.starred))
            uiState.categories.forEach { add(DeckFilter.Category(it) to it) }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filters, key = { it.first.toString() }) { (filter, label) ->
                MnemoChip(
                    label = label,
                    selected = uiState.filter == filter,
                    onClick = { onAction(DecksAction.FilterSelected(filter)) },
                )
            }
        }
    }
}

@Composable
private fun LibraryBar(onNewDeck: () -> Unit, onImport: () -> Unit, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(horizontal = MnemoTheme.spacing.md, vertical = MnemoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MnemoIcons.LibraryAdd, null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.feature_decks_library),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .weight(1f)
                .padding(start = MnemoTheme.spacing.sm),
        )
        IconButton(onClick = onBrowse) {
            Icon(MnemoIcons.Browse, stringResource(R.string.feature_decks_browse_all), tint = colors.onSurfaceVariant)
        }
        IconButton(onClick = onImport) {
            Icon(MnemoIcons.FileUpload, stringResource(R.string.feature_decks_import), tint = colors.onSurfaceVariant)
        }
        MnemoButton(
            text = stringResource(R.string.feature_decks_new_deck),
            onClick = onNewDeck,
            leadingIcon = MnemoIcons.Add,
        )
    }
}

@Composable
private fun DecksDialogs(dialog: DecksDialog?, onAction: (DecksAction) -> Unit) {
    when (dialog) {
        null -> Unit
        DecksDialog.NewDeck -> DeckEditorDialog(
            onConfirm = { onAction(DecksAction.SaveDeck(it)) },
            onDismiss = { onAction(DecksAction.DismissDialog) },
        )
        is DecksDialog.EditDeck -> DeckEditorDialog(
            onConfirm = { onAction(DecksAction.SaveDeck(it)) },
            onDismiss = { onAction(DecksAction.DismissDialog) },
            initial = dialog.draft,
            isNew = false,
        )
        is DecksDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { onAction(DecksAction.DismissDialog) },
            icon = { Icon(MnemoIcons.Delete, null) },
            title = { Text(stringResource(R.string.feature_decks_delete_title, dialog.name)) },
            text = {
                Text(pluralStringResource(R.plurals.feature_decks_delete_message, dialog.cardCount, dialog.cardCount))
            },
            confirmButton = {
                TextButton(onClick = { onAction(DecksAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.feature_decks_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(DecksAction.DismissDialog) }) {
                    Text(stringResource(R.string.feature_decks_cancel))
                }
            },
        )
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun DecksScreenPreview() {
    val child = DeckItem("c", "Kanji N3", null, false, 4, 0, 120, null, emptyList(), false)
    MnemoTheme {
        DecksScreen(
            uiState = DecksUiState(
                isLoading = false,
                now = Instant.parse("2026-10-22T09:00:00Z"),
                date = LocalDate.of(2026, 10, 22),
                today = TodaySummary(14, 8, 6, 12, 14, 32, 1420),
                hasDecks = true,
                decks = listOf(
                    DeckItem(
                        "a", "Cognitive Neuroscience", "Exam Prep", true, 18, 5, 320,
                        Instant.parse("2026-10-22T07:00:00Z"), emptyList(), false, recall = 0.94,
                    ),
                    DeckItem("b", "Japanese", "Language", false, 4, 0, 450, null, listOf(child), true),
                ),
                filterCounts = FilterCounts(2, 2, 1),
                categories = listOf("Exam Prep", "Language"),
            ),
            onAction = {},
            onStudyDeck = {},
            onStartDailyMix = {},
            onAddCards = {},
            onBrowse = {},
            onImport = {},
            onExportDeck = { _, _ -> },
        )
    }
}
