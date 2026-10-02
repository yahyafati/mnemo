package com.yahyafati.mnemo.feature.study.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.SavedAssistAnswer
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.ui.ai.AiDisclosureDialog
import com.yahyafati.mnemo.core.ui.ai.AiReport
import com.yahyafati.mnemo.core.ui.ai.AiReportKind
import com.yahyafati.mnemo.core.ui.ai.ReportAiButton
import com.yahyafati.mnemo.core.ui.ai.aiFailureText
import com.yahyafati.mnemo.core.ui.card.CardFace
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText
import com.yahyafati.mnemo.feature.study.AssistAction
import com.yahyafati.mnemo.feature.study.AssistSheet
import com.yahyafati.mnemo.feature.study.RewriteProblem
import com.yahyafati.mnemo.feature.study.StudyAssistUiState
import com.yahyafati.mnemo.feature.study.resources.Res
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_apply
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_back
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_discard
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_disclosure
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_example
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_example_hint
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_explain
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_explain_hint
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_failed
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_outdated
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_problem_cloze
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_problem_empty
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_proposal
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_regenerate
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_retry
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_rewrite
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_rewrite_hint
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_saved
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_saved_badge
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_saved_hint
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_thinking
import com.yahyafati.mnemo.feature.study.resources.feature_study_ai_via
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The study-time AI sheet: a menu of what AI can do with the card, then the streamed answer or
 * the proposed rewrite. [onApplied] runs once a rewrite is saved, so the session reloads the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyAssistSheet(state: StudyAssistUiState, onAction: (AssistAction) -> Unit, onApplied: () -> Unit) {
    val sheet = state.sheet
    if (sheet != null) {
        LaunchedEffect(sheet.applied) {
            if (sheet.applied) {
                onApplied()
                onAction(AssistAction.Close)
            }
        }
        ModalBottomSheet(onDismissRequest = { onAction(AssistAction.Close) }) {
            AssistSheetContent(state, sheet, onAction)
        }
    }
    state.disclosure?.let { route ->
        AiDisclosureDialog(
            providerName = route.provider.name,
            host = AiEndpoint.host(route.provider.baseUrl) ?: route.provider.baseUrl,
            whatIsSent = stringResource(Res.string.feature_study_ai_disclosure),
            onAccept = { onAction(AssistAction.AcceptDisclosure) },
            onDismiss = { onAction(AssistAction.DismissDisclosure) },
        )
    }
}

@Composable
internal fun AssistSheetContent(state: StudyAssistUiState, sheet: AssistSheet, onAction: (AssistAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = MnemoTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.screenMargin, end = spacing.screenMargin, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.Sparkle, null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = spacing.sm),
            ) {
                Text(
                    text = sheet.assist?.let { stringResource(it.titleRes()) } ?: stringResource(Res.string.feature_study_ai),
                    style = MaterialTheme.typography.headlineSmall,
                )
                // A saved answer names the model that wrote it, which may not be today's route.
                val via = sheet.saved?.let { it.providerName to it.modelId }
                    ?: sheet.assist?.let(state::routeFor)?.let { it.provider.name to it.modelId }
                if (via != null) {
                    Text(
                        text = stringResource(Res.string.feature_study_ai_via, via.first, via.second),
                        style = MnemoTheme.typography.metricSm,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (sheet.assist != null) {
                TextButton(onClick = { onAction(AssistAction.Back) }) { Text(stringResource(Res.string.feature_study_ai_back)) }
            }
        }

        when (val assist = sheet.assist) {
            null -> StudyAssist.entries.filter { state.routeFor(it) != null }.forEach { option ->
                val hint = if (option in sheet.savedAnswers) Res.string.feature_study_ai_saved_hint else option.hintRes()
                MenuItem(option.icon(), stringResource(option.titleRes()), stringResource(hint), saved = option in sheet.savedAnswers) {
                    onAction(AssistAction.Run(option))
                }
            }
            StudyAssist.Explain, StudyAssist.Example -> {
                sheet.saved?.let { SavedNote(it) }
                if (sheet.text.isNotEmpty()) MarkdownText(sheet.text, style = MaterialTheme.typography.bodyLarge)
                if (sheet.text.isNotEmpty() && !sheet.running) {
                    val kind = if (assist == StudyAssist.Explain) AiReportKind.Explain else AiReportKind.Example
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.End)) {
                        ReportAiButton(AiReport(kind, sheet.text, sheet.saved?.modelId ?: state.routeFor(assist)?.modelId))
                        if (state.routeFor(assist) != null) {
                            TextButton(onClick = { onAction(AssistAction.Run(assist, regenerate = true)) }) {
                                Icon(MnemoIcons.Sparkle, contentDescription = null)
                                Text(stringResource(Res.string.feature_study_ai_regenerate), modifier = Modifier.padding(start = spacing.xs))
                            }
                        }
                    }
                }
                if (sheet.running && sheet.text.isEmpty()) Thinking()
                sheet.failure?.let { Failure(aiFailureText(it)) { onAction(AssistAction.Run(assist, regenerate = true)) } }
            }
            StudyAssist.Rewrite -> {
                if (sheet.running && sheet.proposal == null) Thinking()
                sheet.failure?.let { Failure(aiFailureText(it)) { onAction(AssistAction.Run(assist)) } }
                sheet.proposal?.let { fields ->
                    Text(
                        text = stringResource(Res.string.feature_study_ai_proposal).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainerLowest, shadowElevation = 1.dp) {
                        CardFace(
                            sides = CardSides.of(sheet.card.kind, fields, sheet.card.card.templateOrd),
                            revealed = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.lg),
                        )
                    }
                    sheet.proposalProblem?.let { problem ->
                        Text(
                            text = stringResource(
                                when (problem) {
                                    RewriteProblem.Empty -> Res.string.feature_study_ai_problem_empty
                                    RewriteProblem.ClozeChanged -> Res.string.feature_study_ai_problem_cloze
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.error,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        if (!sheet.running) {
                            ReportAiButton(AiReport.ofFields(AiReportKind.Rewrite, fields, state.routeFor(assist)?.modelId), compact = true)
                        }
                        MnemoButton(
                            text = stringResource(Res.string.feature_study_ai_discard),
                            onClick = { onAction(AssistAction.Back) },
                            style = MnemoButtonStyle.Secondary,
                        )
                        MnemoButton(
                            text = stringResource(Res.string.feature_study_ai_apply),
                            onClick = { onAction(AssistAction.ApplyRewrite) },
                            enabled = sheet.proposalProblem == null && !sheet.running && !sheet.applied,
                            leadingIcon = MnemoIcons.Check,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, title: String, hint: String, saved: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(MnemoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.primary)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = MnemoTheme.spacing.md),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (saved) SavedBadge(Modifier.padding(start = MnemoTheme.spacing.sm))
        }
    }
}

/** Marks an answer that is stored on this device and opens without a request. */
@Composable
private fun SavedBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.small, color = colors.secondaryContainer, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = MnemoTheme.spacing.sm, vertical = MnemoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MnemoIcons.CheckCircle, null, tint = colors.onSecondaryContainer, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(Res.string.feature_study_ai_saved_badge),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSecondaryContainer,
                modifier = Modifier.padding(start = MnemoTheme.spacing.xs),
            )
        }
    }
}

/** When a saved answer was written, and whether the card changed since. */
@Composable
private fun SavedNote(answer: SavedAssistAnswer) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SavedBadge()
            Text(
                text = stringResource(Res.string.feature_study_ai_saved, SAVED_DATE.format(answer.savedAt.atZone(ZoneId.systemDefault()))),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = MnemoTheme.spacing.sm),
            )
        }
        if (answer.outdated) {
            Text(stringResource(Res.string.feature_study_ai_outdated), style = MaterialTheme.typography.bodySmall, color = colors.error)
        }
    }
}

private val SAVED_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun Thinking() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(Res.string.feature_study_ai_thinking),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = MnemoTheme.spacing.sm),
        )
    }
}

@Composable
private fun Failure(message: String, onRetry: () -> Unit) {
    Column {
        Text(stringResource(Res.string.feature_study_ai_failed, message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Box(Modifier.align(Alignment.End)) {
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.feature_study_ai_retry)) }
        }
    }
}

private fun StudyAssist.titleRes() = when (this) {
    StudyAssist.Explain -> Res.string.feature_study_ai_explain
    StudyAssist.Example -> Res.string.feature_study_ai_example
    StudyAssist.Rewrite -> Res.string.feature_study_ai_rewrite
}

private fun StudyAssist.hintRes() = when (this) {
    StudyAssist.Explain -> Res.string.feature_study_ai_explain_hint
    StudyAssist.Example -> Res.string.feature_study_ai_example_hint
    StudyAssist.Rewrite -> Res.string.feature_study_ai_rewrite_hint
}

private fun StudyAssist.icon() = when (this) {
    StudyAssist.Explain -> MnemoIcons.Lightbulb
    StudyAssist.Example -> MnemoIcons.School
    StudyAssist.Rewrite -> MnemoIcons.Edit
}

@Preview(showBackground = true)
@Composable
private fun AssistSheetPreview() {
    val now = Instant.EPOCH
    val note = Note("n", "d", NoteType.Basic.id, listOf("What makes ATP?", "Mitochondria"), createdAt = now, updatedAt = now)
    val card = StudyCard(Card("c", "n", "d", 0, due = now, createdAt = now, updatedAt = now), note, NoteKind.Basic, "Biology")
    MnemoTheme {
        AssistSheetContent(
            state = StudyAssistUiState(),
            sheet = AssistSheet(card, StudyAssist.Explain, text = "Mitochondria run **oxidative phosphorylation**, which makes most of a cell's ATP."),
            onAction = {},
        )
    }
}
