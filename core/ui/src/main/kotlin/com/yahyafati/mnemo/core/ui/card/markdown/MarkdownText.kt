package com.yahyafati.mnemo.core.ui.card.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yahyafati.mnemo.core.designsystem.theme.JetBrainsMono
import com.yahyafati.mnemo.core.ui.card.markdown.Markdown.Block
import com.yahyafati.mnemo.core.ui.card.markdown.Markdown.Inline

/** Which cloze deletion is the answer on this card, and whether it is shown yet. */
@Immutable
data class ClozeDisplay(val ordinal: Int, val revealed: Boolean)

/**
 * Renders card Markdown (see [Markdown]). With [cloze], deletion [ClozeDisplay.ordinal] shows as
 * "[hint]" / "[…]" until revealed, then highlighted; other deletions show as plain text.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    cloze: ClozeDisplay? = null,
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val colors = MaterialTheme.colorScheme
    val spans = remember(colors) { MarkdownSpans.from(colors) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { MarkdownBlock(it, style, color, spans, cloze) }
    }
}

@Composable
private fun MarkdownBlock(block: Block, style: TextStyle, color: Color, spans: MarkdownSpans, cloze: ClozeDisplay?) {
    when (block) {
        is Block.Paragraph -> InlineText(block.content, style, color, spans, cloze)

        is Block.Heading -> InlineText(
            block.content,
            style.copy(
                fontSize = style.fontSize * HEADING_SCALE.getOrElse(block.level - 1) { 1f },
                fontWeight = FontWeight.SemiBold,
            ),
            color, spans, cloze,
        )

        is Block.CodeBlock -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = block.code,
                style = style.copy(fontFamily = JetBrainsMono, fontSize = style.fontSize * 0.78f, lineHeight = 1.45.em),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }

        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
            )
            Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.blocks.forEach { MarkdownBlock(it, style, MaterialTheme.colorScheme.onSurfaceVariant, spans, cloze) }
            }
        }

        is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 20.dp).padding(end = 6.dp),
                    )
                    InlineText(item, style, color, spans, cloze)
                }
            }
        }

        Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

@Composable
private fun InlineText(content: List<Inline>, style: TextStyle, color: Color, spans: MarkdownSpans, cloze: ClozeDisplay?) {
    val text = remember(content, spans, cloze) { buildInline(content, spans, cloze) }
    Text(text = text, style = style, color = color)
}

/** Span styles for inline Markdown, derived from the color scheme once. */
@Immutable
internal data class MarkdownSpans(
    val code: SpanStyle,
    val link: TextLinkStyles,
    val clozeHidden: SpanStyle,
    val clozeRevealed: SpanStyle,
) {
    companion object {
        fun from(colors: ColorScheme) = MarkdownSpans(
            code = SpanStyle(fontFamily = JetBrainsMono, fontSize = 0.85.em, background = colors.surfaceContainerHigh),
            link = TextLinkStyles(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)),
            clozeHidden = SpanStyle(
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                background = colors.primary.copy(alpha = 0.10f),
            ),
            clozeRevealed = SpanStyle(
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
                background = colors.secondaryContainer.copy(alpha = 0.35f),
            ),
        )
    }
}

internal fun buildInline(content: List<Inline>, spans: MarkdownSpans, cloze: ClozeDisplay?): AnnotatedString =
    buildAnnotatedString { appendInlines(content, spans, cloze) }

private fun AnnotatedString.Builder.appendInlines(content: List<Inline>, spans: MarkdownSpans, cloze: ClozeDisplay?) {
    for (inline in content) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(inline.children, spans, cloze) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(inline.children, spans, cloze) }
            is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(inline.children, spans, cloze)
            }
            is Inline.Code -> withStyle(spans.code) { append(" ${inline.code} ") }
            is Inline.Link -> withLink(LinkAnnotation.Url(inline.url, spans.link)) { appendInlines(inline.children, spans, cloze) }
            is Inline.Cloze -> when {
                cloze == null || inline.ordinal != cloze.ordinal -> appendInlines(inline.answer, spans, cloze)
                cloze.revealed -> withStyle(spans.clozeRevealed) { appendInlines(inline.answer, spans, cloze) }
                else -> withStyle(spans.clozeHidden) { append("[${inline.hint ?: "…"}]") }
            }
        }
    }
}

private val HEADING_SCALE = listOf(1.4f, 1.25f, 1.1f)
