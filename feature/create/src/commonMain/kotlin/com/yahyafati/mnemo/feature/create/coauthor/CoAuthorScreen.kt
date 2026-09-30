package com.yahyafati.mnemo.feature.create.coauthor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.DuplicateGroup
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.markdown.Markdown
import com.yahyafati.mnemo.core.ui.ai.AiDisclosureDialog
import com.yahyafati.mnemo.core.ui.ai.AiReport
import com.yahyafati.mnemo.core.ui.ai.AiReportKind
import com.yahyafati.mnemo.core.ui.ai.AiSetupPrompt
import com.yahyafati.mnemo.core.ui.ai.ReportAiButton
import com.yahyafati.mnemo.core.ui.ai.aiFailureText
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarBox
import com.yahyafati.mnemo.feature.create.DeckOption
import com.yahyafati.mnemo.feature.create.component.OptionDropdown
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_added
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_add
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_add_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_added
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_applied
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_apply
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_clear
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_deck
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_delete
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_deleted
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_deleted_short
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_discard
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_discarded
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_disclosure
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_duplicate_groups
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_duplicates
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_duplicates_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_intro
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_keep
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_kept
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_lapses
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_no_duplicates
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_no_suggestions
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_no_weak
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_not_applicable
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_placeholder
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_proposal
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_rewriting
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_send
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_setup
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_stop
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_suggest
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_suggesting
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_suggestions
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_suggestions_on
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_thinking
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_weak
import com.yahyafati.mnemo.feature.create.resources.feature_create_coauthor_weak_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_no_decks
import com.yahyafati.mnemo.feature.create.resources.feature_create_smart_via
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant

/** AI Co-Author: a conversation about one deck, with actions that propose changes to it. Stateless. */
@Composable
internal fun CoAuthorScreen(
    uiState: CoAuthorUiState,
    onAction: (CoAuthorAction) -> Unit,
    onSetUpAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MnemoTheme.spacing
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(
            when (notice) {
                is CoAuthorNotice.Added -> getPluralString(Res.plurals.feature_create_added, notice.cards, notice.cards)
                CoAuthorNotice.Deleted -> getString(Res.string.feature_create_coauthor_deleted)
                CoAuthorNotice.Applied -> getString(Res.string.feature_create_coauthor_applied)
            },
        )
        onAction(CoAuthorAction.NoticeShown)
    }
    val listState = rememberLazyListState()
    // Follow the conversation as it grows, including a streaming reply.
    val lastSize = uiState.messages.lastOrNull()?.let { (it as? CoAuthorMessage.Reply)?.text?.length ?: 0 }
    LaunchedEffect(uiState.messages.size, lastSize) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size)
    }

    Box(modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ScrollbarBox(listState, Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = spacing.screenMargin, vertical = spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    item(key = "header") { Header(uiState, onAction, onSetUpAi) }
                    items(uiState.messages, key = { it.id }) { message ->
                        when (message) {
                            is CoAuthorMessage.User -> UserBubble(message.text)
                            is CoAuthorMessage.Reply -> ReplyBubble(message, uiState.route?.modelId)
                            is CoAuthorMessage.Suggestions -> SuggestionsBlock(message, uiState.route?.modelId, onAction)
                            is CoAuthorMessage.Duplicates -> DuplicatesBlock(message, onAction)
                            is CoAuthorMessage.WeakCards -> WeakCardsBlock(message, uiState.route?.modelId, onAction)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            Composer(uiState, onAction, Modifier.widthIn(max = 720.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    uiState.disclosure?.let { route ->
        AiDisclosureDialog(
            providerName = route.provider.name,
            host = AiEndpoint.host(route.provider.baseUrl) ?: route.provider.baseUrl,
            whatIsSent = stringResource(Res.string.feature_create_coauthor_disclosure),
            onAccept = { onAction(CoAuthorAction.AcceptDisclosure) },
            onDismiss = { onAction(CoAuthorAction.DismissDisclosure) },
        )
    }
}

@Composable
private fun Header(uiState: CoAuthorUiState, onAction: (CoAuthorAction) -> Unit, onSetUpAi: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.CoAuthor, contentDescription = null, tint = colors.primary)
            Text(
                text = stringResource(Res.string.feature_create_coauthor_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(start = MnemoTheme.spacing.sm)
                    .semantics { heading() },
            )
        }
        Text(stringResource(Res.string.feature_create_coauthor_intro), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        OptionDropdown(
            value = uiState.decks.firstOrNull { it.id == uiState.deckId }?.path ?: stringResource(Res.string.feature_create_no_decks),
            label = stringResource(Res.string.feature_create_coauthor_deck),
            leadingIcon = MnemoIcons.Decks,
            options = uiState.decks.map { deck -> deck.path to { onAction(CoAuthorAction.SelectDeck(deck.id)) } },
        )
        val route = uiState.route
        if (route == null && !uiState.isLoading) {
            AiSetupPrompt(onSetUp = onSetUpAi, message = stringResource(Res.string.feature_create_coauthor_setup))
        } else if (route != null) {
            Text(
                text = stringResource(Res.string.feature_create_smart_via, route.provider.name, route.modelId),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurfaceVariant,
            )
        }
        if (uiState.messages.isNotEmpty()) {
            TextButton(onClick = { onAction(CoAuthorAction.ClearConversation) }, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(Res.string.feature_create_coauthor_clear))
            }
        }
    }
}

/** Quick actions above the message box, then the box and Send (or Stop while a request runs). */
@Composable
private fun Composer(uiState: CoAuthorUiState, onAction: (CoAuthorAction) -> Unit, modifier: Modifier = Modifier) {
    val spacing = MnemoTheme.spacing
    Column(modifier.padding(horizontal = spacing.screenMargin, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AssistChip(
                onClick = { onAction(CoAuthorAction.SuggestCards) },
                enabled = uiState.canAsk,
                label = { Text(stringResource(Res.string.feature_create_coauthor_suggest)) },
                leadingIcon = { Icon(MnemoIcons.Sparkle, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { onAction(CoAuthorAction.FindDuplicates) },
                enabled = uiState.canFindDuplicates,
                label = { Text(stringResource(Res.string.feature_create_coauthor_duplicates)) },
                leadingIcon = { Icon(MnemoIcons.Duplicate, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { onAction(CoAuthorAction.ImproveWeakCards) },
                enabled = uiState.canAsk,
                label = { Text(stringResource(Res.string.feature_create_coauthor_weak)) },
                leadingIcon = { Icon(MnemoIcons.Leech, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = uiState.input,
                onValueChange = { onAction(CoAuthorAction.InputChanged(it)) },
                placeholder = { Text(stringResource(Res.string.feature_create_coauthor_placeholder)) },
                enabled = uiState.route != null,
                maxLines = 4,
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onAction(CoAuthorAction.Send) }),
                modifier = Modifier.weight(1f),
            )
            if (uiState.busy) {
                MnemoIconButton(
                    icon = MnemoIcons.Stop,
                    contentDescription = stringResource(Res.string.feature_create_coauthor_stop),
                    onClick = { onAction(CoAuthorAction.Stop) },
                )
            } else {
                FilledIconButton(
                    onClick = { onAction(CoAuthorAction.Send) },
                    enabled = uiState.canSend,
                    modifier = Modifier.padding(start = spacing.xs),
                ) {
                    Icon(MnemoIcons.Send, stringResource(Res.string.feature_create_coauthor_send))
                }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun ReplyBubble(message: CoAuthorMessage.Reply, modelId: String?) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .padding(12.dp)
                .semantics { if (!message.done) liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs),
        ) {
            if (message.text.isEmpty() && !message.done) Working(stringResource(Res.string.feature_create_coauthor_thinking))
            if (message.text.isNotEmpty()) MarkdownText(markdown = message.text, style = MaterialTheme.typography.bodyLarge)
            message.failure?.let { Failure(it) }
            if (message.done && message.text.isNotEmpty()) {
                ReportAiButton(AiReport(AiReportKind.CoAuthorReply, message.text, modelId), Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun SuggestionsBlock(message: CoAuthorMessage.Suggestions, modelId: String?, onAction: (CoAuthorAction) -> Unit) {
    Block(
        title = if (message.focus != null) {
            stringResource(Res.string.feature_create_coauthor_suggestions_on, message.focus)
        } else {
            stringResource(Res.string.feature_create_coauthor_suggestions)
        },
    ) {
        message.cards.forEach { suggested ->
            SuggestionCard(suggested, modelId, onAdd = { onAction(CoAuthorAction.AddSuggestion(message.id, suggested.card.id)) }) {
                onAction(CoAuthorAction.DiscardSuggestion(message.id, suggested.card.id))
            }
        }
        when {
            !message.done -> Working(stringResource(Res.string.feature_create_coauthor_suggesting))
            message.cards.isEmpty() && message.failure == null -> Text(stringResource(Res.string.feature_create_coauthor_no_suggestions))
        }
        message.failure?.let { Failure(it) }
        val pending = message.pending.size
        if (message.done && pending > 1) {
            MnemoButton(
                text = pluralStringResource(Res.plurals.feature_create_coauthor_add_all, pending, pending),
                onClick = { onAction(CoAuthorAction.AddAllSuggestions(message.id)) },
                leadingIcon = MnemoIcons.Add,
            )
        }
    }
}

@Composable
private fun SuggestionCard(suggested: SuggestedCard, modelId: String?, onAdd: () -> Unit, onDiscard: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
            CardFace(sides = suggested.card.sides, revealed = true, fontScale = 0.85f)
            when (suggested.status) {
                SuggestionStatus.Pending -> Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    MnemoButton(stringResource(Res.string.feature_create_coauthor_add), onAdd, leadingIcon = MnemoIcons.Add, style = MnemoButtonStyle.Secondary)
                    MnemoButton(stringResource(Res.string.feature_create_coauthor_discard), onDiscard, style = MnemoButtonStyle.Text)
                    ReportAiButton(AiReport.ofFields(AiReportKind.CoAuthorSuggestion, suggested.card.fields, modelId), compact = true)
                }
                SuggestionStatus.Added -> Status(stringResource(Res.string.feature_create_coauthor_added), colors.secondary)
                SuggestionStatus.Discarded -> Status(stringResource(Res.string.feature_create_coauthor_discarded), colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DuplicatesBlock(message: CoAuthorMessage.Duplicates, onAction: (CoAuthorAction) -> Unit) {
    Block(
        title = if (message.groups.isEmpty()) {
            stringResource(Res.string.feature_create_coauthor_no_duplicates)
        } else {
            pluralStringResource(Res.plurals.feature_create_coauthor_duplicate_groups, message.groups.size, message.groups.size)
        },
    ) {
        if (message.groups.isNotEmpty()) {
            Text(stringResource(Res.string.feature_create_coauthor_duplicates_hint), style = MaterialTheme.typography.bodyMedium)
        }
        message.groups.forEach { group ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                    group.notes.forEachIndexed { index, note ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                        DuplicateNote(note, deleted = note.id in message.deleted) {
                            onAction(CoAuthorAction.DeleteNote(message.id, note.id))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateNote(note: Note, deleted: Boolean, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val front = remember(note) { Markdown.plainText(note.field(0)) }
    val back = remember(note) { Markdown.plainText(note.field(1)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(front, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis, color = if (deleted) colors.onSurfaceVariant else colors.onSurface, textDecoration = if (deleted) TextDecoration.LineThrough else null)
            if (back.isNotBlank()) {
                Text(back, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (deleted) {
            Status(stringResource(Res.string.feature_create_coauthor_deleted_short), colors.onSurfaceVariant)
        } else {
            MnemoIconButton(
                icon = MnemoIcons.Delete,
                contentDescription = stringResource(Res.string.feature_create_coauthor_delete, front.take(40)),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun WeakCardsBlock(message: CoAuthorMessage.WeakCards, modelId: String?, onAction: (CoAuthorAction) -> Unit) {
    Block(
        title = if (message.items.isEmpty()) {
            stringResource(Res.string.feature_create_coauthor_no_weak, CoAuthorViewModel.MIN_LAPSES)
        } else {
            stringResource(Res.string.feature_create_coauthor_weak_title)
        },
    ) {
        message.items.forEach { item -> WeakCard(item, modelId, onAction, message.id) }
    }
}

@Composable
private fun WeakCard(item: WeakCardItem, modelId: String?, onAction: (CoAuthorAction) -> Unit, messageId: String) {
    val colors = MaterialTheme.colorScheme
    val card = item.card
    Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Status(
                pluralStringResource(Res.plurals.feature_create_coauthor_lapses, card.card.lapses, card.card.lapses, card.card.reps),
                colors.error,
            )
            CardFace(sides = card.sides.copy(choices = null), revealed = true, fontScale = 0.85f)
            when (val state = item.state) {
                WeakCardState.Waiting -> Unit
                WeakCardState.Loading -> Working(stringResource(Res.string.feature_create_coauthor_rewriting))
                is WeakCardState.Proposed -> {
                    HorizontalDivider(color = colors.surfaceContainerHighest)
                    Status(stringResource(Res.string.feature_create_coauthor_proposal), colors.primary)
                    val sides = remember(state.fields) {
                        CardSides.of(card.kind, state.fields, card.card.templateOrd).copy(choices = null)
                    }
                    CardFace(sides = sides, revealed = true, fontScale = 0.85f)
                    if (state.applicable) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm), verticalArrangement = Arrangement.Center) {
                            MnemoButton(
                                stringResource(Res.string.feature_create_coauthor_apply),
                                { onAction(CoAuthorAction.ApplyRewrite(messageId, card.card.id)) },
                                leadingIcon = MnemoIcons.Check,
                                style = MnemoButtonStyle.Secondary,
                            )
                            MnemoButton(
                                stringResource(Res.string.feature_create_coauthor_keep),
                                { onAction(CoAuthorAction.DismissRewrite(messageId, card.card.id)) },
                                style = MnemoButtonStyle.Text,
                            )
                            ReportAiButton(AiReport.ofFields(AiReportKind.CoAuthorRewrite, state.fields, modelId), compact = true)
                        }
                    } else {
                        Text(stringResource(Res.string.feature_create_coauthor_not_applicable), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                    }
                }
                WeakCardState.Applied -> Status(stringResource(Res.string.feature_create_coauthor_applied), colors.secondary)
                WeakCardState.Dismissed -> Status(stringResource(Res.string.feature_create_coauthor_kept), colors.onSurfaceVariant)
                is WeakCardState.Failed -> Failure(state.failure)
            }
        }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun Working(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = MnemoTheme.spacing.sm))
    }
}

@Composable
private fun Status(text: String, color: Color) {
    Text(text.uppercase(), style = MnemoTheme.typography.metricSm, color = color)
}

@Composable
private fun Failure(failure: AiFailure) {
    Text(
        stringResource(Res.string.feature_create_coauthor_failed, aiFailureText(failure)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun CoAuthorScreenPreview() {
    val now = Instant.EPOCH
    fun note(id: String, front: String) = Note(id, "d", NoteType.Basic.id, listOf(front, "ATP"), createdAt = now, updatedAt = now)
    MnemoTheme {
        CoAuthorScreen(
            uiState = CoAuthorUiState(
                isLoading = false,
                decks = listOf(DeckOption("d", "Biology::Cells")),
                deckId = "d",
                messages = listOf(
                    CoAuthorMessage.User("1", "What topics am I missing?"),
                    CoAuthorMessage.Reply("2", "You have nothing on **enzymes** or the **cell cycle**.", done = true),
                    CoAuthorMessage.Suggestions(
                        "3", "enzymes",
                        listOf(SuggestedCard(GeneratedCard("g", NoteKind.Basic, "What do enzymes lower?", "Activation energy"))),
                        done = true,
                    ),
                    CoAuthorMessage.Duplicates(
                        "4", listOf(DuplicateGroup(listOf(note("a", "What do mitochondria make?"), note("b", "What do **mitochondria** make?")), 1.0)),
                    ),
                ),
            ),
            onAction = {},
            onSetUpAi = {},
        )
    }
}
