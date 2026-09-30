package com.yahyafati.mnemo.feature.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.component.clickCursor
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CardSort
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.CardStatus
import com.yahyafati.mnemo.core.ui.adaptive.readingWidth
import com.yahyafati.mnemo.core.ui.keyboard.LocalFindRequests
import com.yahyafati.mnemo.core.ui.menu.ContextAction
import com.yahyafati.mnemo.core.ui.menu.ContextMenuHost
import com.yahyafati.mnemo.core.ui.format.formatInterval
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarBox
import com.yahyafati.mnemo.feature.browse.resources.Res
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_add
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_add_tag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_all_decks
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_any_tag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_back
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_cancel
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_clear_search
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_clear_selection
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_count
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_delete
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_delete_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_delete_title
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_deleted_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_edit_note
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_empty_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_empty_title
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_flag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_flagged
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_flagged_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_more
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_move
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_move_title
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_moved_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_remove_tag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_search
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_select_all
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_selected
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_sort
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_sort_due
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_sort_newest
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_sort_oldest
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_due
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_due_in
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_flagged
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_learning
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_new
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_review
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_starred
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_status_suspended
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_suspend
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_suspended_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_tag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_tagged_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_title
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_unflag
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_unflagged_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_unsuspend
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_unsuspended_message
import com.yahyafati.mnemo.feature.browse.resources.feature_browse_untagged_message
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import java.time.Duration
import java.time.Instant

@Composable
internal fun BrowseRoute(
    onBack: () -> Unit,
    onEditNote: (noteId: String) -> Unit,
    focusSearchOnStart: Boolean = false,
    viewModel: BrowseViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cards = viewModel.cards.collectAsLazyPagingItems()
    BrowseScreen(
        uiState = uiState,
        cards = cards,
        now = remember { Instant.now() },
        onAction = viewModel::onAction,
        onBack = onBack,
        onEditNote = onEditNote,
        focusSearchOnStart = focusSearchOnStart,
    )
}

// Browse is not a tab, so it owns its Scaffold and top bar; the app shell hides its own bars.
@Composable
internal fun BrowseScreen(
    uiState: BrowseUiState,
    cards: LazyPagingItems<BrowseItem>,
    now: Instant,
    onAction: (BrowseAction) -> Unit,
    onBack: () -> Unit,
    onEditNote: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Put the cursor in the search box when the screen opens (Find). */
    focusSearchOnStart: Boolean = false,
) {
    val snackbar = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    // Find while the browser is already open.
    val find = LocalFindRequests.current
    LaunchedEffect(find) {
        find?.requests?.collect { runCatching { searchFocus.requestFocus() } }
    }
    val messageText = uiState.message?.let { messageText(it) }
    LaunchedEffect(uiState.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onAction(BrowseAction.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { if (uiState.selecting) SelectionBar(uiState, onAction) else BrowseBar(uiState, onAction, onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .readingWidth(),
        ) {
            Filters(uiState, onAction, searchFocus, focusOnStart = focusSearchOnStart)
            Text(
                text = pluralStringResource(Res.plurals.feature_browse_count, uiState.matchCount, uiState.matchCount),
                style = MnemoTheme.typography.metricSm,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.xs),
            )
            if (cards.itemCount == 0 && uiState.matchCount == 0) {
                EmptyState(
                    icon = MnemoIcons.Browse,
                    title = stringResource(Res.string.feature_browse_empty_title),
                    message = stringResource(Res.string.feature_browse_empty_message),
                )
            } else {
                ScrollbarBox(listState, Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = MnemoTheme.spacing.lg),
                    ) {
                        items(count = cards.itemCount, key = cards.itemKey { it.cardId }) { index ->
                            val item = cards[index] ?: return@items
                            CardRow(
                                item = item,
                                now = now,
                                selecting = uiState.selecting,
                                selected = item.cardId in uiState.selection,
                                onClick = {
                                    if (uiState.selecting) onAction(BrowseAction.ToggleSelected(item.cardId)) else onEditNote(item.noteId)
                                },
                                onLongClick = { onAction(BrowseAction.ToggleSelected(item.cardId)) },
                                contextActions = rowContextActions(item, uiState.selection, onAction, onEditNote),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    }
                }
            }
        }
    }
    BrowseDialogs(uiState, onAction)
}

@Composable
private fun BrowseBar(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit, onBack: () -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    MnemoTopBar(
        title = uiState.deckName ?: stringResource(Res.string.feature_browse_title),
        navigationIcon = {
            MnemoIconButton(
                icon = MnemoIcons.ArrowBack,
                contentDescription = stringResource(Res.string.feature_browse_back),
                onClick = onBack,
            )
        },
        actions = {
            Box {
                MnemoIconButton(
                    icon = MnemoIcons.Sort,
                    contentDescription = stringResource(Res.string.feature_browse_sort),
                    onClick = { sortMenu = true },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(
                        CardSort.Newest to Res.string.feature_browse_sort_newest,
                        CardSort.Oldest to Res.string.feature_browse_sort_oldest,
                        CardSort.DueFirst to Res.string.feature_browse_sort_due,
                    ).forEach { (sort, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { if (uiState.query.sort == sort) Icon(MnemoIcons.Check, null) },
                            onClick = {
                                sortMenu = false
                                onAction(BrowseAction.SortSelected(sort))
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SelectionBar(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    MnemoTopBar(
        title = pluralStringResource(Res.plurals.feature_browse_selected, uiState.selection.size, uiState.selection.size),
        navigationIcon = {
            MnemoIconButton(
                icon = MnemoIcons.Close,
                contentDescription = stringResource(Res.string.feature_browse_clear_selection),
                onClick = { onAction(BrowseAction.ClearSelection) },
            )
        },
        actions = {
            MnemoIconButton(
                icon = MnemoIcons.SelectAll,
                contentDescription = stringResource(Res.string.feature_browse_select_all),
                onClick = { onAction(BrowseAction.SelectAll) },
            )
            MnemoIconButton(
                icon = MnemoIcons.Move,
                contentDescription = stringResource(Res.string.feature_browse_move),
                onClick = { onAction(BrowseAction.ShowMove) },
            )
            Box {
                MnemoIconButton(
                    icon = MnemoIcons.MoreVert,
                    contentDescription = stringResource(Res.string.feature_browse_more),
                    onClick = { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf(
                        Triple(Res.string.feature_browse_suspend, MnemoIcons.Suspend, BrowseAction.Suspend(true)),
                        Triple(Res.string.feature_browse_unsuspend, MnemoIcons.Play, BrowseAction.Suspend(false)),
                        Triple(Res.string.feature_browse_flag, MnemoIcons.Flag, BrowseAction.Flag(true)),
                        Triple(Res.string.feature_browse_unflag, MnemoIcons.Flag, BrowseAction.Flag(false)),
                        Triple(Res.string.feature_browse_add_tag, MnemoIcons.Tag, BrowseAction.ShowAddTag),
                        Triple(Res.string.feature_browse_remove_tag, MnemoIcons.Tag, BrowseAction.ShowRemoveTag),
                        Triple(Res.string.feature_browse_delete, MnemoIcons.Delete, BrowseAction.ShowDelete),
                    ).forEach { (label, icon, action) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon, null) },
                            onClick = {
                                menu = false
                                onAction(action)
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Filters(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit, searchFocus: FocusRequester, focusOnStart: Boolean) {
    // Here, not in the screen: the Scaffold composes its content later, and the search box has to exist to take the focus.
    LaunchedEffect(focusOnStart) {
        if (focusOnStart) runCatching { searchFocus.requestFocus() }
    }
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Column(
        Modifier.padding(horizontal = spacing.screenMargin, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        OutlinedTextField(
            value = uiState.query.text,
            onValueChange = { onAction(BrowseAction.TextChanged(it)) },
            placeholder = { Text(stringResource(Res.string.feature_browse_search), style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(MnemoIcons.Search, null, tint = colors.outline) },
            trailingIcon = {
                if (uiState.query.text.isNotEmpty()) {
                    MnemoIconButton(
                        icon = MnemoIcons.Close,
                        contentDescription = stringResource(Res.string.feature_browse_clear_search),
                        onClick = { onAction(BrowseAction.TextChanged("")) },
                    )
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.surfaceContainerLow,
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedBorderColor = colors.surfaceContainerHigh,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocus),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item(key = "deck") { DeckChip(uiState, onAction) }
            item(key = "tag") { TagChip(uiState, onAction) }
            items(STATUSES, key = { it.first.name }) { (status, label) ->
                MnemoChip(
                    label = stringResource(label),
                    selected = uiState.query.status == status,
                    onClick = { onAction(BrowseAction.StatusSelected(status)) },
                )
            }
        }
    }
}

@Composable
private fun DeckChip(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MnemoChip(
            label = uiState.deckName ?: stringResource(Res.string.feature_browse_all_decks),
            selected = uiState.query.deckId != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.feature_browse_all_decks)) }, onClick = {
                open = false
                onAction(BrowseAction.DeckSelected(null))
            })
            uiState.decks.forEach { deck ->
                DropdownMenuItem(text = { Text(deck.path) }, onClick = {
                    open = false
                    onAction(BrowseAction.DeckSelected(deck.id))
                })
            }
        }
    }
}

@Composable
private fun TagChip(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit) {
    if (uiState.tags.isEmpty() && uiState.query.tag == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        MnemoChip(
            label = uiState.query.tag?.let { "#$it" } ?: stringResource(Res.string.feature_browse_any_tag),
            selected = uiState.query.tag != null,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.feature_browse_any_tag)) }, onClick = {
                open = false
                onAction(BrowseAction.TagSelected(null))
            })
            uiState.tags.forEach { tag ->
                DropdownMenuItem(text = { Text(tag) }, onClick = {
                    open = false
                    onAction(BrowseAction.TagSelected(tag))
                })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    item: BrowseItem,
    now: Instant,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contextActions: List<ContextAction>,
) {
    val colors = MaterialTheme.colorScheme
    ContextMenuHost(contextActions) {
        Surface(
            color = if (selected) colors.secondaryContainer.copy(alpha = 0.4f) else colors.background,
            modifier = Modifier
                .fillMaxWidth()
                .clickCursor()
                .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md),
            ) {
                if (selecting) {
                    Icon(
                        if (selected) MnemoIcons.Checked else MnemoIcons.Unchecked,
                        contentDescription = null,
                        tint = if (selected) colors.primary else colors.outline,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.front.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.back.isNotBlank()) {
                        Text(item.back, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.deckName, style = MnemoTheme.typography.metricSm, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(statusLabel(item, now), style = MnemoTheme.typography.metricSm, color = if (item.suspended) colors.tertiary else colors.secondary)
                        if (item.flagged) Icon(MnemoIcons.FlagFilled, stringResource(Res.string.feature_browse_flagged), tint = colors.error, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * What a right click on a card offers: edit its note, or act on the cards selected with it. A card
 * that isn't selected becomes the selection first, so the menu is about the card that was clicked.
 */
@Composable
private fun rowContextActions(
    item: BrowseItem,
    selection: Set<String>,
    onAction: (BrowseAction) -> Unit,
    onEditNote: (noteId: String) -> Unit,
): List<ContextAction> {
    val edit = stringResource(Res.string.feature_browse_edit_note)
    val suspend = stringResource(if (item.suspended) Res.string.feature_browse_unsuspend else Res.string.feature_browse_suspend)
    val flag = stringResource(if (item.flagged) Res.string.feature_browse_unflag else Res.string.feature_browse_flag)
    val delete = stringResource(Res.string.feature_browse_delete)
    fun onSelection(action: BrowseAction) {
        if (item.cardId !in selection) onAction(BrowseAction.SelectForMenu(item.cardId))
        onAction(action)
    }
    return listOf(
        ContextAction(edit) { onEditNote(item.noteId) },
        ContextAction(suspend) { onSelection(BrowseAction.Suspend(!item.suspended)) },
        ContextAction(flag) { onSelection(BrowseAction.Flag(!item.flagged)) },
        ContextAction(delete) { onSelection(BrowseAction.ShowDelete) },
    )
}

@Composable
private fun statusLabel(item: BrowseItem, now: Instant): String = when {
    item.suspended -> stringResource(Res.string.feature_browse_status_suspended)
    item.state == CardState.New -> stringResource(Res.string.feature_browse_status_new)
    item.state == CardState.Learning || item.state == CardState.Relearning -> stringResource(Res.string.feature_browse_status_learning)
    !item.due.isAfter(now) -> stringResource(Res.string.feature_browse_status_due)
    else -> stringResource(Res.string.feature_browse_status_due_in, formatInterval(Duration.between(now, item.due)))
}

@Composable
private fun BrowseDialogs(uiState: BrowseUiState, onAction: (BrowseAction) -> Unit) {
    val dismiss = { onAction(BrowseAction.DismissDialog) }
    when (val dialog = uiState.dialog) {
        null -> Unit
        BrowseDialog.Move -> ChoiceDialog(
            title = pluralStringResource(Res.plurals.feature_browse_move_title, uiState.selection.size, uiState.selection.size),
            options = uiState.decks.map { it.id to it.path },
            onChoose = { onAction(BrowseAction.Move(it)) },
            onDismiss = dismiss,
        )
        BrowseDialog.RemoveTag -> ChoiceDialog(
            title = stringResource(Res.string.feature_browse_remove_tag),
            options = uiState.tags.map { it to it },
            onChoose = { onAction(BrowseAction.RemoveTag(it)) },
            onDismiss = dismiss,
        )
        BrowseDialog.AddTag -> {
            var tag by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(Res.string.feature_browse_add_tag)) },
                text = {
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        singleLine = true,
                        label = { Text(stringResource(Res.string.feature_browse_tag)) },
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onAction(BrowseAction.AddTag(tag)) }, enabled = tag.isNotBlank()) {
                        Text(stringResource(Res.string.feature_browse_add))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.feature_browse_cancel)) } },
            )
        }
        is BrowseDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = dismiss,
            icon = { Icon(MnemoIcons.Delete, null) },
            title = { Text(pluralStringResource(Res.plurals.feature_browse_delete_title, dialog.cardCount, dialog.cardCount)) },
            text = { Text(stringResource(Res.string.feature_browse_delete_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(BrowseAction.ConfirmDelete) }) {
                    Text(stringResource(Res.string.feature_browse_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(Res.string.feature_browse_cancel)) } },
        )
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, onChoose: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(options, key = { it.first }) { (id, label) ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(role = Role.Button, onClick = { onChoose(id) })
                            .padding(vertical = MnemoTheme.spacing.sm),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_browse_cancel)) } },
    )
}

@Composable
private fun messageText(message: BrowseMessage): String = when (message) {
    is BrowseMessage.Suspended -> pluralStringResource(
        if (message.suspended) Res.plurals.feature_browse_suspended_message else Res.plurals.feature_browse_unsuspended_message,
        message.count, message.count,
    )
    is BrowseMessage.Flagged -> pluralStringResource(
        if (message.flagged) Res.plurals.feature_browse_flagged_message else Res.plurals.feature_browse_unflagged_message,
        message.count, message.count,
    )
    is BrowseMessage.Moved -> pluralStringResource(Res.plurals.feature_browse_moved_message, message.count, message.count, message.deck)
    is BrowseMessage.Tagged -> pluralStringResource(
        if (message.added) Res.plurals.feature_browse_tagged_message else Res.plurals.feature_browse_untagged_message,
        message.count, message.count, message.tag,
    )
    is BrowseMessage.Deleted -> pluralStringResource(Res.plurals.feature_browse_deleted_message, message.count, message.count)
}

private val STATUSES = listOf(
    CardStatus.Due to Res.string.feature_browse_status_due,
    CardStatus.New to Res.string.feature_browse_status_new,
    CardStatus.Learning to Res.string.feature_browse_status_learning,
    CardStatus.Review to Res.string.feature_browse_status_review,
    CardStatus.Suspended to Res.string.feature_browse_status_suspended,
    CardStatus.Flagged to Res.string.feature_browse_status_flagged,
    CardStatus.Starred to Res.string.feature_browse_status_starred,
)

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun BrowseScreenPreview() {
    val now = Instant.parse("2026-10-22T09:00:00Z")
    val items = listOf(
        BrowseItem("1", "n1", "What do mitochondria make?", "ATP", "Biology", CardState.Review, now.plus(Duration.ofDays(3)), false, false, listOf("cells")),
        BrowseItem("2", "n2", "猫", "cat", "Japanese", CardState.New, now, false, true, emptyList()),
        BrowseItem("3", "n3", "Mass-energy equivalence", "E = mc^2", "Physics", CardState.Review, now, true, false, emptyList()),
    )
    MnemoTheme {
        BrowseScreen(
            uiState = BrowseUiState(matchCount = 3, tags = listOf("cells"), selection = setOf("2")),
            cards = MutableStateFlow(PagingData.from(items)).collectAsLazyPagingItems(),
            now = now,
            onAction = {},
            onBack = {},
            onEditNote = {},
        )
    }
}
