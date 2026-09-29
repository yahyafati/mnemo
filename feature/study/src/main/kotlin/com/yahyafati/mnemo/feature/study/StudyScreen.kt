package com.yahyafati.mnemo.feature.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.component.EmptyState
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.feature.study.component.FlashCard
import com.yahyafati.mnemo.feature.study.component.IntervalButtons
import com.yahyafati.mnemo.feature.study.component.StudyAssistSheet
import com.yahyafati.mnemo.feature.study.component.SessionSummaryView
import com.yahyafati.mnemo.feature.study.component.SwipeableCard
import java.time.Duration
import java.time.Instant

@Composable
internal fun StudyScreen(
    onEditNote: (noteId: String) -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = hiltViewModel(),
    assistViewModel: StudyAssistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val assist by assistViewModel.uiState.collectAsStateWithLifecycle()
    // Coming back from the editor or another tab: refresh content, or look for new cards.
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenShown()
        onPauseOrDispose { }
    }
    StudyScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onEditNote = onEditNote,
        onDone = onDone,
        doneLabel = doneLabel,
        modifier = modifier,
        aiAvailable = assist.available,
        onAssist = { assistViewModel.onAction(AssistAction.Open(it)) },
    )
    // A rewrite that was applied changes the note: reload the session's copy.
    StudyAssistSheet(assist, assistViewModel::onAction, onApplied = viewModel::onScreenShown)
}

@Composable
internal fun StudyScreen(
    uiState: StudyUiState,
    onAction: (StudyAction) -> Unit,
    onEditNote: (noteId: String) -> Unit,
    onDone: () -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    /** A provider is configured: show the AI button once the answer is showing. */
    aiAvailable: Boolean = false,
    onAssist: (StudyCard) -> Unit = {},
) {
    when (val phase = uiState.phase) {
        StudyPhase.Loading -> Box(modifier.fillMaxSize())

        is StudyPhase.Empty -> Box(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = MnemoIcons.Study,
                title = stringResource(R.string.feature_study_empty_title),
                message = if (phase.laterCount > 0) {
                    pluralStringResource(R.plurals.feature_study_later_message, phase.laterCount, phase.laterCount)
                } else {
                    stringResource(R.string.feature_study_empty_message)
                },
                action = { MnemoButton(doneLabel, onDone) },
            )
        }

        is StudyPhase.Finished -> Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (phase.canUndo) {
                UndoRow(onAction, Modifier.align(Alignment.End))
            }
            SessionSummaryView(
                summary = phase.summary,
                laterCount = phase.laterCount,
                onCheckAgain = { onAction(StudyAction.Continue) },
                onDone = onDone,
                doneLabel = doneLabel,
            )
        }

        is StudyPhase.Reviewing -> Reviewing(
            phase = phase,
            deckName = uiState.deckName,
            onAction = onAction,
            onEditNote = onEditNote,
            onAssist = { onAssist(phase.card) }.takeIf { aiAvailable && phase.revealed },
            modifier = modifier,
        )
    }
}

@Composable
private fun Reviewing(
    phase: StudyPhase.Reviewing,
    deckName: String?,
    onAction: (StudyAction) -> Unit,
    onEditNote: (noteId: String) -> Unit,
    onAssist: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    val progress by animateFloatAsState(phase.progress, label = "studyProgress")
    Column(modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = colors.primary,
            trackColor = colors.surfaceContainerHigh,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        Row(
            modifier = Modifier.padding(start = spacing.screenMargin, end = spacing.sm, top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MnemoIcons.School, null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Text(
                text = deckName ?: phase.card.deckName.ifEmpty { stringResource(R.string.feature_study_daily_mix) },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = spacing.xs),
            )
            Text(
                text = stringResource(R.string.feature_study_position, phase.position, phase.total),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .background(colors.surfaceContainer, MaterialTheme.shapes.large)
                    .padding(horizontal = spacing.sm, vertical = 2.dp),
            )
            IconButton(onClick = { onAction(StudyAction.Undo) }, enabled = phase.canUndo) {
                Icon(MnemoIcons.Undo, stringResource(R.string.feature_study_undo))
            }
        }

        val layout = LocalWindowLayout.current
        val card: @Composable (Modifier) -> Unit = { cardModifier ->
            key(phase.card.card.id) {
                SwipeableCard(
                    enabled = phase.revealed,
                    onSwipeLeft = { onAction(StudyAction.Rate(Rating.Again)) },
                    onSwipeRight = { onAction(StudyAction.Rate(Rating.Good)) },
                    modifier = cardModifier
                        .widthIn(max = 640.dp)
                        .padding(top = spacing.xs, bottom = spacing.md),
                ) { swipeFraction ->
                    FlashCard(
                        card = phase.card,
                        revealed = phase.revealed,
                        response = phase.response,
                        onAction = onAction,
                        onEditNote = onEditNote,
                        swipeFraction = swipeFraction,
                        modifier = Modifier.fillMaxSize(),
                        onAssist = onAssist,
                    )
                }
            }
        }
        when {
            // Landscape phone: the card and the answer buttons side by side.
            layout.wide && layout.short -> Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenMargin),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                card(Modifier.weight(1f).fillMaxHeight())
                AnswerControls(phase, onAction, Modifier.width(320.dp))
            }
            // Half-open foldable: the card above the hinge, the buttons below it.
            layout.tabletop -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                card(Modifier.weight(1f))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AnswerControls(phase, onAction, Modifier.widthIn(max = 640.dp))
                }
            }
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                card(Modifier.weight(1f))
                AnswerControls(phase, onAction, Modifier.widthIn(max = 640.dp))
            }
        }
    }
}

/** Show answer, or the four ratings once it shows, and the gesture hint below. */
@Composable
private fun AnswerControls(phase: StudyPhase.Reviewing, onAction: (StudyAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Column(
        modifier = modifier.padding(bottom = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (phase.revealed) {
            IntervalButtons(phase.intervals, onRate = { onAction(StudyAction.Rate(it)) }, suggested = phase.suggestedRating)
        } else {
            MnemoButton(
                text = stringResource(if (phase.card.sides.typeIn) R.string.feature_study_check_answer else R.string.feature_study_show_answer),
                onClick = { onAction(StudyAction.Flip) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MnemoIcons.TouchApp, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(
                    if (phase.revealed) R.string.feature_study_hint_swipe else R.string.feature_study_hint_before,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun UndoRow(onAction: (StudyAction) -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = { onAction(StudyAction.Undo) }, modifier = modifier) {
        Icon(MnemoIcons.Undo, stringResource(R.string.feature_study_undo))
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun StudyScreenPreview() {
    val now = Instant.parse("2026-10-22T09:00:00Z")
    val note = Note(
        id = "n", deckId = "d", noteTypeId = NoteType.Basic.id,
        fields = listOf(
            "What is the role of **long-term potentiation** in the hippocampus?",
            "The persistent strengthening of synapses after repeated stimulation.",
        ),
        createdAt = now, updatedAt = now,
    )
    val card = Card(id = "c", noteId = "n", deckId = "d", templateOrd = 0, due = now, createdAt = now, updatedAt = now)
    MnemoTheme {
        StudyScreen(
            uiState = StudyUiState(
                deckName = "Cognitive Neuroscience",
                phase = StudyPhase.Reviewing(
                    card = StudyCard(card, note, NoteKind.Basic, "Cognitive Neuroscience"),
                    revealed = true,
                    intervals = mapOf(
                        Rating.Again to Duration.ofMinutes(1),
                        Rating.Hard to Duration.ofMinutes(6),
                        Rating.Good to Duration.ofMinutes(10),
                        Rating.Easy to Duration.ofDays(8),
                    ),
                    position = 7,
                    total = 24,
                    canUndo = true,
                ),
            ),
            onAction = {},
            onEditNote = {},
            onDone = {},
            doneLabel = "Done",
        )
    }
}
