package com.yahyafati.mnemo.feature.decks.component

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.chart.StackedBar
import com.yahyafati.mnemo.feature.decks.DeckItem
import com.yahyafati.mnemo.feature.decks.R
import java.text.NumberFormat
import java.time.Instant
import kotlin.math.roundToInt

/** Callbacks shared by a deck card and its subdeck rows. */
internal class DeckCallbacks(
    val onStudy: (String) -> Unit,
    val onAddCards: (String) -> Unit,
    val onToggleStar: (String) -> Unit,
    val onToggleExpanded: (String) -> Unit,
    val onEdit: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onBrowse: (String) -> Unit,
    /** Export as an Anki package: deck id and name (for the file name). */
    val onExport: (String, String) -> Unit,
)

/** A deck in the library list, as in the mockup: labels, name, star, counts and a Review button. */
@Composable
internal fun DeckCard(
    deck: DeckItem,
    now: Instant,
    callbacks: DeckCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        deck.category?.let { Tag(it) }
                        deck.lastReviewedAt?.let {
                            Text(
                                text = stringResource(R.string.feature_decks_last_review, relativeTime(it, now)),
                                style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
                                color = colors.outline,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                StarButton(deck, callbacks)
                DeckMenu(deck, callbacks)
            }
            deck.recall?.let { RetentionHealth(it) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountChip(deck)
                    Text(
                        text = pluralStringResource(R.plurals.feature_decks_total_cards, deck.totalCount, deck.totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                DeckAction(deck, callbacks)
            }
            if (deck.children.isNotEmpty()) Subdecks(deck, now, callbacks)
        }
    }
}

/** The mockup's "Retention health" bar: how much of the deck the user would recall right now. */
@Composable
private fun RetentionHealth(recall: Double) {
    val colors = MaterialTheme.colorScheme
    val percent = NumberFormat.getPercentInstance().format(recall)
    // Healthy at or above a typical 85% target; the bar turns amber below it.
    val color = if (recall >= 0.85) colors.secondary else colors.tertiary
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(
                text = stringResource(R.string.feature_decks_retention_health),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.feature_decks_retention_recall, percent),
                style = MnemoTheme.typography.metricSm.copy(fontWeight = FontWeight.SemiBold),
                color = color,
            )
        }
        StackedBar(listOf((recall * 1000).roundToInt() to color, (1000 - (recall * 1000).roundToInt()) to Color.Transparent), height = 6.dp)
    }
}

@Composable
private fun Subdecks(deck: DeckItem, now: Instant, callbacks: DeckCallbacks) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.surfaceContainer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { callbacks.onToggleExpanded(deck.id) }
                .padding(vertical = MnemoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(R.plurals.feature_decks_subdecks, deck.children.size, deck.children.size),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (deck.expanded) MnemoIcons.ExpandLess else MnemoIcons.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
        if (deck.expanded) {
            deck.descendants().forEach { (child, depth) ->
                SubdeckRow(child, depth, now, callbacks)
            }
        }
    }
}

@Composable
private fun SubdeckRow(deck: DeckItem, depth: Int, now: Instant, callbacks: DeckCallbacks) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp * (depth - 1), top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                CountChip(deck)
                deck.lastReviewedAt?.let {
                    Text(
                        text = relativeTime(it, now),
                        style = MnemoTheme.typography.metricSm.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
        DeckMenu(deck, callbacks)
        DeckAction(deck, callbacks)
    }
}

@Composable
private fun StarButton(deck: DeckItem, callbacks: DeckCallbacks) {
    IconButton(onClick = { callbacks.onToggleStar(deck.id) }) {
        Icon(
            imageVector = if (deck.starred) MnemoIcons.Star else MnemoIcons.StarOutline,
            contentDescription = stringResource(
                if (deck.starred) R.string.feature_decks_unstar else R.string.feature_decks_star,
            ),
            tint = if (deck.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun DeckMenu(deck: DeckItem, callbacks: DeckCallbacks) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(MnemoIcons.MoreVert, stringResource(R.string.feature_decks_options), tint = MaterialTheme.colorScheme.outline)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_decks_add_cards)) },
                leadingIcon = { Icon(MnemoIcons.Add, null) },
                onClick = {
                    open = false
                    callbacks.onAddCards(deck.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_decks_browse)) },
                leadingIcon = { Icon(MnemoIcons.Browse, null) },
                onClick = {
                    open = false
                    callbacks.onBrowse(deck.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_decks_export)) },
                leadingIcon = { Icon(MnemoIcons.FileDownload, null) },
                onClick = {
                    open = false
                    callbacks.onExport(deck.id, deck.name)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_decks_edit)) },
                leadingIcon = { Icon(MnemoIcons.Edit, null) },
                onClick = {
                    open = false
                    callbacks.onEdit(deck.id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_decks_delete)) },
                leadingIcon = { Icon(MnemoIcons.Delete, null) },
                onClick = {
                    open = false
                    callbacks.onDelete(deck.id)
                },
            )
        }
    }
}

/** Review when there is something to study; otherwise a shortcut to add cards. */
@Composable
private fun DeckAction(deck: DeckItem, callbacks: DeckCallbacks) {
    val colors = MaterialTheme.colorScheme
    val study = deck.hasCardsToStudy
    Surface(
        onClick = { if (study) callbacks.onStudy(deck.id) else callbacks.onAddCards(deck.id) },
        shape = MaterialTheme.shapes.small,
        color = if (study) colors.surfaceContainer else colors.surfaceContainerLowest,
        contentColor = if (study) colors.onSurface else colors.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(if (study) R.string.feature_decks_review else R.string.feature_decks_add_cards),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(if (study) MnemoIcons.ArrowForward else MnemoIcons.Add, null, Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CountChip(deck: DeckItem) {
    val colors = MaterialTheme.colorScheme
    val (text, background, content) = when {
        deck.dueCount > 0 -> Triple(
            pluralStringResource(R.plurals.feature_decks_due_count, deck.dueCount, deck.dueCount),
            colors.errorContainer,
            colors.onErrorContainer,
        )
        deck.newCount > 0 -> Triple(
            pluralStringResource(R.plurals.feature_decks_new_count, deck.newCount, deck.newCount),
            colors.primary.copy(alpha = 0.1f),
            colors.primary,
        )
        else -> Triple(stringResource(R.string.feature_decks_done), colors.secondary.copy(alpha = 0.12f), colors.secondary)
    }
    Text(
        text = text,
        style = MnemoTheme.typography.metricSm.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        modifier = Modifier
            .background(background, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun relativeTime(time: Instant, now: Instant): String =
    if (now.toEpochMilli() - time.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.feature_decks_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(
            time.toEpochMilli(),
            now.toEpochMilli(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
