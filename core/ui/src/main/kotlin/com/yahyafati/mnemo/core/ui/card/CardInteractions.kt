package com.yahyafati.mnemo.core.ui.card

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.TypedAnswer
import com.yahyafati.mnemo.core.ui.R
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText

/** What the learner did on the card showing now: typed text, the option picked, whether the hint is out. */
@Immutable
data class CardResponse(
    val typed: String = "",
    val chosen: Int? = null,
    val hintShown: Boolean = false,
)

/** Callbacks that make a [CardFace] interactive while studying. Without them it is a read-only preview. */
@Immutable
class CardInteraction(
    val onTypedChange: (String) -> Unit,
    /** Check the typed answer: reveals the card. */
    val onSubmitTyped: () -> Unit,
    val onChoose: (Int) -> Unit,
    val onShowHint: () -> Unit,
)

/** The hint under the prompt: a "Show hint" button until asked for, then the hint itself. */
@Composable
internal fun HintArea(hint: String, shown: Boolean, onShow: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (!shown && onShow != null) {
        TextButton(onClick = onShow, modifier = modifier) {
            Icon(MnemoIcons.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.core_ui_card_show_hint), modifier = Modifier.padding(start = MnemoTheme.spacing.xs))
        }
        return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                MnemoIcons.Lightbulb,
                contentDescription = stringResource(R.string.core_ui_card_hint),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            MarkdownText(
                markdown = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = MnemoTheme.spacing.sm),
            )
        }
    }
}

/**
 * Multiple-choice options. Before the answer shows they are buttons (when [onChoose] is set);
 * after, the correct one is marked, and so is a wrong pick.
 */
@Composable
internal fun ChoiceList(
    choices: List<String>,
    correct: Int,
    chosen: Int?,
    revealed: Boolean,
    onChoose: ((Int) -> Unit)?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        choices.forEachIndexed { index, choice ->
            val isCorrect = revealed && index == correct
            val isWrongPick = revealed && index == chosen && index != correct
            val container = when {
                isCorrect -> colors.secondaryContainer
                isWrongPick -> colors.errorContainer
                else -> colors.surfaceContainerLow
            }
            val content = when {
                isCorrect -> colors.onSecondaryContainer
                isWrongPick -> colors.onErrorContainer
                revealed -> colors.onSurfaceVariant
                else -> colors.onSurface
            }
            val letter = ('A' + index).toString()
            val description = when {
                isCorrect -> stringResource(R.string.core_ui_card_choice_correct, "$letter. $choice")
                isWrongPick -> stringResource(R.string.core_ui_card_choice_wrong, "$letter. $choice")
                else -> "$letter. $choice"
            }
            val enabled = onChoose != null && !revealed
            val shape = MaterialTheme.shapes.medium
            val border = BorderStroke(1.dp, if (isCorrect || isWrongPick) container else colors.outlineVariant)
            val row: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(letter, style = MnemoTheme.typography.metricLg, color = content, modifier = Modifier.width(24.dp))
                    MarkdownText(markdown = choice, style = style, color = content, modifier = Modifier.weight(1f))
                    when {
                        isCorrect -> Icon(MnemoIcons.Check, null, tint = content, modifier = Modifier.size(20.dp))
                        isWrongPick -> Icon(MnemoIcons.Close, null, tint = content, modifier = Modifier.size(20.dp))
                    }
                }
            }
            val semantics = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = description
                    if (enabled) role = Role.Button
                }
            if (enabled) {
                Surface(onClick = { onChoose?.invoke(index) }, shape = shape, color = container, border = border, modifier = semantics) { row() }
            } else {
                Surface(shape = shape, color = container, border = border, modifier = semantics) { row() }
            }
        }
    }
}

/** The type-in box. It takes focus when it appears, and the keyboard's Done checks the answer. */
@Composable
internal fun TypedAnswerField(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.core_ui_card_type_answer)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus),
        )
        TextButton(onClick = onSubmit, modifier = Modifier.padding(start = MnemoTheme.spacing.xs)) {
            Text(stringResource(R.string.core_ui_card_check))
        }
    }
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
}

/** What was typed against the answer: a verdict, then the typed text with its mistakes marked. */
@Composable
internal fun TypedAnswerResult(typed: String, back: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val result = remember(typed, back) { TypedAnswer.check(typed, back) }
    val diff = remember(result, colors) {
        buildAnnotatedString {
            if (typed.isBlank()) {
                append("")
                return@buildAnnotatedString
            }
            result.diff.forEach { segment ->
                when (segment.kind) {
                    TypedAnswer.Kind.Same -> append(segment.text)
                    TypedAnswer.Kind.Extra -> withStyle(
                        SpanStyle(color = colors.error, background = colors.errorContainer, textDecoration = TextDecoration.LineThrough),
                    ) { append(segment.text) }
                    TypedAnswer.Kind.Missing -> withStyle(
                        SpanStyle(color = colors.secondary, background = colors.secondaryContainer, textDecoration = TextDecoration.Underline),
                    ) { append(segment.text) }
                }
            }
        }
    }
    val verdict = stringResource(if (result.correct) R.string.core_ui_card_typed_correct else R.string.core_ui_card_typed_wrong)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (result.correct) colors.secondaryContainer.copy(alpha = 0.5f) else colors.errorContainer.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Icon(
                    if (result.correct) MnemoIcons.Check else MnemoIcons.Close,
                    contentDescription = null,
                    tint = if (result.correct) colors.secondary else colors.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = MnemoTheme.spacing.xs),
                )
            }
            Text(
                text = stringResource(R.string.core_ui_card_you_typed).uppercase(),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurfaceVariant,
            )
            if (typed.isBlank()) {
                Text(stringResource(R.string.core_ui_card_typed_nothing), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            } else {
                Text(text = diff, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
        }
    }
}
