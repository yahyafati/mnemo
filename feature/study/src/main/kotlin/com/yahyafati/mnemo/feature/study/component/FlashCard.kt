package com.yahyafati.mnemo.feature.study.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.feature.study.R
import com.yahyafati.mnemo.feature.study.StudyAction

/**
 * The study card from the mockup: a stack underlay, a meta bar (card type, star, more actions),
 * and the card face. Tapping anywhere on the card flips it. Long content scrolls inside the card.
 */
@Composable
internal fun FlashCard(
    card: StudyCard,
    revealed: Boolean,
    onAction: (StudyAction) -> Unit,
    onEditNote: (noteId: String) -> Unit,
    swipeFraction: () -> Float,
    modifier: Modifier = Modifier,
    /** Opens study-time AI; null hides the button (no provider, or the answer isn't showing). */
    onAssist: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        // Simulated stack of cards underneath.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer { translationY = 8.dp.toPx() }
                .background(colors.surfaceContainerHigh, MaterialTheme.shapes.large),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer { translationY = 4.dp.toPx() }
                .background(colors.surfaceContainer, MaterialTheme.shapes.large),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.feature_study_flip),
                ) { onAction(StudyAction.Flip) },
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLowest,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(MnemoTheme.spacing.lg)) {
                MetaBar(card, onAction, onEditNote, onAssist)
                CardFace(
                    sides = card.sides,
                    revealed = revealed,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = MnemoTheme.spacing.md),
                )
            }
        }
        SwipeHint(Rating.Again, Alignment.TopEnd) { (-swipeFraction() * 3f).coerceIn(0f, 1f) }
        SwipeHint(Rating.Good, Alignment.TopStart) { (swipeFraction() * 3f).coerceIn(0f, 1f) }
    }
}

@Composable
private fun MetaBar(card: StudyCard, onAction: (StudyAction) -> Unit, onEditNote: (String) -> Unit, onAssist: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .background(colors.surfaceContainerLow, MaterialTheme.shapes.large)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).background(colors.primary, CircleShape))
            Text(
                text = stringResource(
                    when (card.kind) {
                        NoteKind.Basic -> R.string.feature_study_kind_basic
                        NoteKind.Reversed -> R.string.feature_study_kind_reversed
                        NoteKind.Cloze -> R.string.feature_study_kind_cloze
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (card.card.flagged) {
            Icon(
                MnemoIcons.FlagFilled,
                contentDescription = null,
                tint = colors.error,
                modifier = Modifier
                    .padding(start = MnemoTheme.spacing.sm)
                    .size(16.dp),
            )
        }
        Box(Modifier.weight(1f))
        if (onAssist != null) {
            IconButton(
                onClick = onAssist,
                colors = IconButtonDefaults.iconButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                modifier = Modifier
                    .padding(end = MnemoTheme.spacing.xs)
                    .size(36.dp),
            ) {
                Icon(MnemoIcons.Sparkle, stringResource(R.string.feature_study_ai_open), Modifier.size(19.dp))
            }
        }
        val starred = card.card.starred
        IconButton(
            onClick = { onAction(StudyAction.ToggleStar) },
            colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surfaceContainerLow),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (starred) MnemoIcons.Star else MnemoIcons.StarOutline,
                contentDescription = stringResource(if (starred) R.string.feature_study_unstar else R.string.feature_study_star),
                tint = if (starred) colors.tertiary else colors.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        CardMenu(card, onAction, onEditNote)
    }
}

@Composable
private fun CardMenu(card: StudyCard, onAction: (StudyAction) -> Unit, onEditNote: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = MnemoTheme.spacing.xs)) {
        IconButton(
            onClick = { open = true },
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(MnemoIcons.MoreVert, stringResource(R.string.feature_study_more), Modifier.size(19.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun item(label: Int, icon: ImageVector, action: () -> Unit) = Triple(label, icon, action)
            listOf(
                item(R.string.feature_study_edit, MnemoIcons.Edit) { onEditNote(card.note.id) },
                item(
                    if (card.card.flagged) R.string.feature_study_unflag else R.string.feature_study_flag,
                    MnemoIcons.Flag,
                ) { onAction(StudyAction.ToggleFlag) },
                item(R.string.feature_study_bury, MnemoIcons.Bury) { onAction(StudyAction.Bury) },
                item(R.string.feature_study_suspend, MnemoIcons.Suspend) { onAction(StudyAction.Suspend) },
            ).forEach { (label, icon, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { Icon(icon, null) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/** A rating label that fades in as the card is dragged toward it. */
@Composable
private fun BoxScope.SwipeHint(rating: Rating, alignment: Alignment, alpha: () -> Float) {
    val color = rating.color()
    Text(
        text = stringResource(rating.labelRes()).uppercase(),
        style = MnemoTheme.typography.metricLg,
        color = color,
        modifier = Modifier
            .align(alignment)
            .padding(MnemoTheme.spacing.lg)
            .graphicsLayer { this.alpha = alpha() }
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
