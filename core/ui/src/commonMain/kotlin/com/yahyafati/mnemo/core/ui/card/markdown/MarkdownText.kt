package com.yahyafati.mnemo.core.ui.card.markdown

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.markdown.Markdown
import com.yahyafati.mnemo.core.model.markdown.Markdown.Block
import com.yahyafati.mnemo.core.model.markdown.Markdown.Inline
import com.yahyafati.mnemo.core.ui.card.MediaImage
import com.yahyafati.mnemo.core.ui.card.audio.LocalCardAudio
import com.yahyafati.mnemo.core.ui.resources.Res
import com.yahyafati.mnemo.core.ui.resources.core_ui_play_sound
import org.jetbrains.compose.resources.stringResource

/** How `[sound:…]` tags show: a tappable [label] that plays the sound. */
internal class SoundLinks(val label: String, val onPlay: (String) -> Unit)

/** Which cloze deletion is the answer on this card, and whether it is shown yet. */
@Immutable
data class ClozeDisplay(val ordinal: Int, val revealed: Boolean)

/**
 * Renders card Markdown (see [Markdown]). With [cloze], deletion [ClozeDisplay.ordinal] shows as
 * "[hint]" / "[…]" until revealed, then highlighted; other deletions show as plain text.
 *
 * Math is shown as its TeX source in code style, unless a [math] painter draws it (the desktop app).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    cloze: ClozeDisplay? = null,
    math: MathPainter? = null,
) {
    val blocks = remember(markdown) { Markdown.parse(markdown) }
    val colors = MaterialTheme.colorScheme
    val mono = MnemoTheme.fonts.jetBrainsMono
    val spans = remember(colors, mono) { MarkdownSpans.from(colors, mono) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { MarkdownBlock(it, style, color, spans, cloze, math) }
    }
}

@Composable
private fun MarkdownBlock(block: Block, style: TextStyle, color: Color, spans: MarkdownSpans, cloze: ClozeDisplay?, math: MathPainter?) {
    when (block) {
        is Block.Paragraph -> InlineText(block.content, style, color, spans, cloze, math)

        is Block.Heading -> InlineText(
            block.content,
            style.copy(
                fontSize = style.fontSize * HEADING_SCALE.getOrElse(block.level - 1) { 1f },
                fontWeight = FontWeight.SemiBold,
            ),
            color, spans, cloze, math,
        )

        is Block.CodeBlock -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = block.code,
                style = style.copy(fontFamily = MnemoTheme.fonts.jetBrainsMono, fontSize = style.fontSize * 0.78f, lineHeight = 1.45.em),
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
                block.blocks.forEach { MarkdownBlock(it, style, MaterialTheme.colorScheme.onSurfaceVariant, spans, cloze, math) }
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
                    InlineText(item, style, color, spans, cloze, math)
                }
            }
        }

        Block.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
    }
}

/**
 * Inline content. Top-level images (and display math, when a [math] painter draws it) can't sit
 * inside a line of text, so they split the run into text pieces and full-width pieces, in order
 * (Anki-imported cards usually put images on their own line).
 */
@Composable
private fun InlineText(
    content: List<Inline>,
    style: TextStyle,
    color: Color,
    spans: MarkdownSpans,
    cloze: ClozeDisplay?,
    math: MathPainter?,
) {
    val audio = LocalCardAudio.current
    val soundLabel = stringResource(Res.string.core_ui_play_sound)
    val sounds = remember(audio, soundLabel) { SoundLinks(soundLabel) { src -> audio.play(listOf(src)) } }
    val density = LocalDensity.current
    // The formulas' size follows the text: the style's font size, in pixels.
    val inlineMath = remember(math, color, style.fontSize, density) {
        math?.let { MathContext(it, color, with(density) { style.fontSize.toPx() }, density) }
    }
    val pieces = remember(content, inlineMath) { splitAtBlocks(content, splitDisplayMath = inlineMath != null) }
    if (pieces.size == 1 && pieces[0] is TextRun) {
        TextPiece(content, spans, cloze, sounds, inlineMath, style, color)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.forEach { piece ->
            when (piece) {
                is Inline.Image -> MediaImage(src = piece.src, alt = piece.alt)
                is Inline.Math -> DisplayMath(piece.tex, inlineMath!!, style, color, spans)
                is TextRun -> TextPiece(piece.inlines, spans, cloze, sounds, inlineMath, style, color, skipBlank = true)
            }
        }
    }
}

@Composable
private fun TextPiece(
    inlines: List<Inline>,
    spans: MarkdownSpans,
    cloze: ClozeDisplay?,
    sounds: SoundLinks,
    math: MathContext?,
    style: TextStyle,
    color: Color,
    skipBlank: Boolean = false,
) {
    // Building the text fills the formulas' inline content, so the two come as a pair.
    val (text, inlineContent) = remember(inlines, spans, cloze, sounds, math) {
        val formulas = math?.let(::MathInlines)
        buildInline(inlines, spans, cloze, sounds, formulas) to (formulas?.content ?: emptyMap())
    }
    if (skipBlank && text.isBlank()) return
    Text(text = text, style = style, color = color, inlineContent = inlineContent)
}

/** A formula on a line of its own, centered, scrolling sideways if it is wider than the card. */
@Composable
private fun DisplayMath(tex: String, math: MathContext, style: TextStyle, color: Color, spans: MarkdownSpans) {
    val image = remember(tex, math) { math.painter.paint(tex, display = true, sizePx = math.fontSizePx, color = math.color) }
    if (image == null) {
        Text(text = tex, style = style.copy(fontSize = style.fontSize * 0.85f), color = color, modifier = Modifier.background(spans.code.background))
        return
    }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image.bitmap,
            contentDescription = tex,
            modifier = Modifier.size(with(density) { image.widthPx.toDp() }, with(density) { image.heightPx.toDp() }),
        )
    }
}

/** What drawing a text's formulas needs: the painter, the text's color and its size in pixels. */
@Immutable
internal class MathContext(
    val painter: MathPainter,
    val color: Color,
    val fontSizePx: Float,
    val density: Density,
)

/**
 * The inline formulas of one text: each formula the painter could draw becomes an inline content
 * placeholder, in the order the text mentions them.
 */
internal class MathInlines(private val context: MathContext) {
    val content = mutableMapOf<String, InlineTextContent>()
    private val painter get() = context.painter
    private val density get() = context.density

    /** Appends [tex] as an inline picture; false if it can't be drawn, so the caller shows the source. */
    fun append(builder: AnnotatedString.Builder, tex: String): Boolean {
        val image = painter.paint(tex, display = false, sizePx = context.fontSizePx, color = context.color) ?: return false
        val id = "math:${content.size}"
        val ascent = (image.heightPx - image.depthPx).coerceAtLeast(1)
        val placeholder = with(density) {
            Placeholder(image.widthPx.toSp(), ascent.toSp(), PlaceholderVerticalAlign.AboveBaseline)
        }
        content[id] = InlineTextContent(placeholder) {
            // The picture hangs below the placeholder by its depth, so its baseline meets the text's.
            Image(
                bitmap = image.bitmap,
                contentDescription = tex,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(with(density) { image.widthPx.toDp() }, with(density) { image.heightPx.toDp() }),
            )
        }
        builder.appendInlineContent(id, tex)
        return true
    }
}

private data class TextRun(val inlines: List<Inline>)

private fun splitAtBlocks(content: List<Inline>, splitDisplayMath: Boolean): List<Any> {
    fun Inline.isBlock() = this is Inline.Image || (splitDisplayMath && this is Inline.Math && display)
    if (content.none { it.isBlock() }) return listOf(TextRun(content))
    val out = mutableListOf<Any>()
    var run = mutableListOf<Inline>()
    for (inline in content) {
        if (inline.isBlock()) {
            if (run.isNotEmpty()) out += TextRun(trimRun(run))
            out += inline
            run = mutableListOf()
        } else {
            run += inline
        }
    }
    if (run.isNotEmpty()) out += TextRun(trimRun(run))
    return out
}

/** Drops the line breaks that surrounded an image. */
private fun trimRun(run: List<Inline>): List<Inline> = run.mapIndexed { i, inline ->
    if (inline !is Inline.Text) return@mapIndexed inline
    var text = inline.text
    if (i == 0) text = text.trimStart('\n')
    if (i == run.lastIndex) text = text.trimEnd('\n')
    Inline.Text(text)
}.filterNot { it is Inline.Text && it.text.isEmpty() }

/** Span styles for inline Markdown, derived from the color scheme once. */
@Immutable
internal data class MarkdownSpans(
    val code: SpanStyle,
    val link: TextLinkStyles,
    val clozeHidden: SpanStyle,
    val clozeRevealed: SpanStyle,
) {
    companion object {
        fun from(colors: ColorScheme, mono: FontFamily) = MarkdownSpans(
            code = SpanStyle(fontFamily = mono, fontSize = 0.85.em, background = colors.surfaceContainerHigh),
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

internal fun buildInline(
    content: List<Inline>,
    spans: MarkdownSpans,
    cloze: ClozeDisplay?,
    sounds: SoundLinks? = null,
    math: MathInlines? = null,
): AnnotatedString = buildAnnotatedString { appendInlines(content, spans, cloze, sounds, math) }

private fun AnnotatedString.Builder.appendInlines(
    content: List<Inline>,
    spans: MarkdownSpans,
    cloze: ClozeDisplay?,
    sounds: SoundLinks?,
    math: MathInlines?,
) {
    for (inline in content) {
        when (inline) {
            is Inline.Text -> append(inline.text)
            is Inline.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInlines(inline.children, spans, cloze, sounds, math) }
            is Inline.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInlines(inline.children, spans, cloze, sounds, math) }
            is Inline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(inline.children, spans, cloze, sounds, math)
            }
            is Inline.Code -> withStyle(spans.code) { append(" ${inline.code} ") }
            is Inline.Link -> withLink(LinkAnnotation.Url(inline.url, spans.link)) { appendInlines(inline.children, spans, cloze, sounds, math) }
            // Images nested in emphasis or links, where they can't be laid out: their alt text.
            is Inline.Image -> if (inline.alt.isNotBlank()) append(inline.alt)
            // Android draws cards with math in a KaTeX WebView (CardFace); the desktop app draws the
            // formulas itself. Without a painter, or for TeX it can't draw, the source shows.
            is Inline.Math -> if (math == null || !math.append(this, inline.tex)) withStyle(spans.code) { append(inline.tex) }
            // A tappable speaker; the sound also plays on its own if auto-play is on.
            is Inline.Sound -> if (sounds == null) {
                append("🔊")
            } else {
                withLink(LinkAnnotation.Clickable("sound", spans.link) { sounds.onPlay(inline.src) }) { append("🔊 ${sounds.label}") }
            }
            is Inline.Cloze -> when {
                cloze == null || inline.ordinal != cloze.ordinal -> appendInlines(inline.answer, spans, cloze, sounds, math)
                cloze.revealed -> withStyle(spans.clozeRevealed) { appendInlines(inline.answer, spans, cloze, sounds, math) }
                else -> withStyle(spans.clozeHidden) { append("[${inline.hint ?: "…"}]") }
            }
        }
    }
}

private val HEADING_SCALE = listOf(1.4f, 1.25f, 1.1f)
