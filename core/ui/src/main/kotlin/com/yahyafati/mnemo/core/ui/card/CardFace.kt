package com.yahyafati.mnemo.core.ui.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.markdown.Markdown
import com.yahyafati.mnemo.core.ui.card.web.MathText
import com.yahyafati.mnemo.core.ui.R
import com.yahyafati.mnemo.core.ui.card.markdown.ClozeDisplay
import com.yahyafati.mnemo.core.ui.card.markdown.MarkdownText

/** The user's card font size setting (Settings › Appearance), as a multiplier. Provided by `:app`. */
val LocalCardFontScale = staticCompositionLocalOf { 1f }

/**
 * A card's content: the prompt, and below a divider the answer once [revealed]. Cloze deletions
 * are revealed in place in the prompt; the answer area then shows the Extra field, if any.
 */
@Composable
fun CardFace(
    sides: CardSides,
    revealed: Boolean,
    modifier: Modifier = Modifier,
    fontScale: Float = LocalCardFontScale.current,
) {
    val colors = MaterialTheme.colorScheme
    val frontMath = remember(sides.front) { Markdown.containsMath(sides.front) }
    val backMath = remember(sides.back) { Markdown.containsMath(sides.back) }
    Column(modifier) {
        SideLabel(stringResource(R.string.core_ui_card_prompt), colors.outline)
        val promptStyle = MnemoTheme.typography.studyPromptCompact.scaled(fontScale)
        if (frontMath) {
            MathText(
                markdown = sides.front,
                style = promptStyle,
                serif = true,
                clozeOrdinal = sides.clozeOrdinal,
                revealed = revealed,
                modifier = Modifier.padding(top = MnemoTheme.spacing.xs),
            )
        } else {
            MarkdownText(
                markdown = sides.front,
                style = promptStyle,
                cloze = sides.clozeOrdinal?.let { ClozeDisplay(it, revealed) },
                modifier = Modifier.padding(top = MnemoTheme.spacing.xs),
            )
        }
        AnimatedVisibility(
            visible = revealed && sides.back.isNotBlank(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
                HorizontalDivider(
                    color = colors.surfaceContainerHighest,
                    modifier = Modifier.padding(vertical = MnemoTheme.spacing.md),
                )
                SideLabel(stringResource(R.string.core_ui_card_answer), colors.secondary)
                val answerStyle = MaterialTheme.typography.bodyLarge.scaled(fontScale)
                if (backMath) {
                    MathText(markdown = sides.back, style = answerStyle, serif = false)
                } else {
                    MarkdownText(markdown = sides.back, style = answerStyle)
                }
            }
        }
    }
}

@Composable
private fun SideLabel(text: String, color: Color) {
    Text(text = text.uppercase(), style = MnemoTheme.typography.metricSm, color = color)
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

@Preview(showBackground = true)
@Composable
private fun CardFacePreview() {
    MnemoTheme {
        CardFace(
            sides = CardSides(
                front = "What does **long-term potentiation** strengthen?",
                back = "Synapses, after repeated high-frequency stimulation.\n\n- NMDA receptors\n- `Ca²⁺` influx",
            ),
            revealed = true,
            modifier = Modifier.padding(MnemoTheme.spacing.lg),
        )
    }
}
