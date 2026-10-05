package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.focusable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoChip
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.FigureSide
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.RegionCorner
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_back
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_blank
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_down
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_larger
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_left
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_right
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_smaller
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_action_up
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_box
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_box_state
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_failed
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_front
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_hint
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_hint_keys
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_page
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_save
import com.yahyafati.mnemo.feature.create.resources.feature_create_figure_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_close
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crops a figure out of a page for a queued card (docs/pdf/ROADMAP.md, P7): the page, large, with a rectangle over it to
 * move by its body and resize by its corners (arrow keys move it and Shift + arrow keys resize it, for a keyboard), a
 * choice of the card's Front or Back, and Add figure. The rectangle is kept as a [PageRegion], fractions of the page, so
 * what is drawn on this picture is what is cut from the page at full size. Esc or the button closes it.
 */
@Composable
internal fun PdfFigureCropper(edit: FigureEdit, pageFiles: PdfPageFiles, onAction: (SmartExtractAction) -> Unit) {
    Dialog(onDismissRequest = { onAction(SmartExtractAction.CloseFigure) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            FigureCropperContent(edit, pageFiles, onAction)
        }
    }
}

/** The crop screen without its window, so it can be shown (and photographed) on its own. */
@Composable
internal fun FigureCropperContent(edit: FigureEdit, pageFiles: PdfPageFiles, onAction: (SmartExtractAction) -> Unit, modifier: Modifier = Modifier) {
    val loader = LocalMediaImageLoader.current
    val picture by produceState<ImageBitmap?>(null, edit.page, pageFiles, loader) {
        value = null
        value = withContext(Dispatchers.IO) { pageFiles.page(edit.page)?.let { loader.loadFile(it) } }
    }
    var region by remember(edit.cardId, edit.page) { mutableStateOf(edit.region) }
    var side by remember(edit.cardId) { mutableStateOf(edit.side) }
    val loaded = picture
    val spacing = MnemoTheme.spacing
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            MnemoIconButton(
                icon = MnemoIcons.Close,
                contentDescription = stringResource(Res.string.feature_create_pdf_view_close),
                onClick = { onAction(SmartExtractAction.CloseFigure) },
                shortcut = "Esc",
            )
            Text(
                text = stringResource(Res.string.feature_create_figure_title, edit.page),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = spacing.sm),
            )
            MnemoButton(
                text = stringResource(Res.string.feature_create_figure_save),
                onClick = { onAction(SmartExtractAction.SaveFigure(region, side)) },
                leadingIcon = MnemoIcons.Image,
                enabled = loaded != null && !edit.saving,
            )
        }
        if (edit.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (edit.sides.size > 1) {
            FlowRow(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                edit.sides.forEach { option ->
                    MnemoChip(
                        label = stringResource(if (option == FigureSide.Front) Res.string.feature_create_figure_front else Res.string.feature_create_figure_back),
                        selected = side == option,
                        onClick = { side = option },
                    )
                }
            }
        }
        val problem = edit.problem
        Text(
            text = if (problem != null) {
                if (problem == SourceProblem.BlankPage) {
                    stringResource(Res.string.feature_create_figure_blank)
                } else {
                    stringResource(Res.string.feature_create_figure_failed, sourceProblemText(problem))
                }
            } else {
                stringResource(if (LocalPlatformCapabilities.current.keyboardAndMouse) Res.string.feature_create_figure_hint_keys else Res.string.feature_create_figure_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs),
        )
        if (loaded != null) {
            CropArea(
                bitmap = loaded,
                region = region,
                onRegion = { region = it },
                pageDescription = stringResource(Res.string.feature_create_figure_page, edit.page),
                boxDescription = stringResource(Res.string.feature_create_figure_box),
                boxState = stringResource(
                    Res.string.feature_create_figure_box_state,
                    region.left.percent(),
                    region.top.percent(),
                    region.right.percent(),
                    region.bottom.percent(),
                ),
                actions = CropActions(
                    left = stringResource(Res.string.feature_create_figure_action_left),
                    right = stringResource(Res.string.feature_create_figure_action_right),
                    up = stringResource(Res.string.feature_create_figure_action_up),
                    down = stringResource(Res.string.feature_create_figure_action_down),
                    larger = stringResource(Res.string.feature_create_figure_action_larger),
                    smaller = stringResource(Res.string.feature_create_figure_action_smaller),
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(
                text = stringResource(Res.string.feature_create_pdf_view_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(spacing.lg),
            )
        }
    }
}

/** The page picture with the crop rectangle over it. Everything the user does goes through [onRegion]; the state is the caller's. */
@Composable
private fun CropArea(
    bitmap: ImageBitmap,
    region: PageRegion,
    onRegion: (PageRegion) -> Unit,
    pageDescription: String,
    boxDescription: String,
    boxState: String,
    actions: CropActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentRegion by rememberUpdatedState(region)
    val change by rememberUpdatedState(onRegion)
    val density = LocalDensity.current
    val slop = with(density) { CORNER_REACH.toPx() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalPlatformCapabilities.current.keyboardAndMouse
    if (keyboard) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BoxWithConstraints(modifier.fillMaxSize().padding(MnemoTheme.spacing.md)) {
        val layout = CropLayout.fit(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), bitmap.width, bitmap.height)
        Image(
            bitmap = bitmap,
            contentDescription = pageDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val box = layout.rectOf(region)
            val page = layout.bounds
            val scrim = Color.Black.copy(alpha = 0.5f)
            // Everything outside the rectangle is dimmed, so what will be cut out stays bright.
            drawRect(scrim, page.topLeft, Size(page.width, box.top - page.top))
            drawRect(scrim, Offset(page.left, box.bottom), Size(page.width, page.bottom - box.bottom))
            drawRect(scrim, Offset(page.left, box.top), Size(box.left - page.left, box.height))
            drawRect(scrim, Offset(box.right, box.top), Size(page.right - box.right, box.height))
            drawRect(colors.primary, box.topLeft, box.size, style = Stroke(width = 2.dp.toPx()))
            RegionCorner.entries.forEach { corner ->
                val center = layout.cornerOf(box, corner)
                drawCircle(Color.White, radius = 9.dp.toPx(), center = center)
                drawCircle(colors.primary, radius = 6.dp.toPx(), center = center)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = boxDescription
                    stateDescription = boxState
                    // A screen reader can't drag: the same moves as the arrow keys, in bigger steps.
                    customActions = listOf(
                        CustomAccessibilityAction(actions.left) { change(currentRegion.moved(-ACTION_STEP, 0f)); true },
                        CustomAccessibilityAction(actions.right) { change(currentRegion.moved(ACTION_STEP, 0f)); true },
                        CustomAccessibilityAction(actions.up) { change(currentRegion.moved(0f, -ACTION_STEP)); true },
                        CustomAccessibilityAction(actions.down) { change(currentRegion.moved(0f, ACTION_STEP)); true },
                        CustomAccessibilityAction(actions.larger) { change(currentRegion.resized(ACTION_STEP, ACTION_STEP)); true },
                        CustomAccessibilityAction(actions.smaller) { change(currentRegion.resized(-ACTION_STEP, -ACTION_STEP)); true },
                    )
                }
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val dx = when (event.key) {
                        Key.DirectionLeft -> -KEY_STEP
                        Key.DirectionRight -> KEY_STEP
                        else -> 0f
                    }
                    val dy = when (event.key) {
                        Key.DirectionUp -> -KEY_STEP
                        Key.DirectionDown -> KEY_STEP
                        else -> 0f
                    }
                    if (dx == 0f && dy == 0f) return@onKeyEvent false
                    change(if (event.isShiftPressed) currentRegion.resized(dx, dy) else currentRegion.moved(dx, dy))
                    true
                }
                .pointerInput(layout, slop) {
                    var target: CropTarget = CropTarget.None
                    var grab = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start ->
                            if (keyboard) runCatching { focus.requestFocus() }
                            val box = layout.rectOf(currentRegion)
                            target = layout.targetAt(start, box, slop)
                            // The corner keeps its distance from the finger, so it doesn't jump to it.
                            grab = (target as? CropTarget.Corner)?.let { layout.cornerOf(box, it.corner) - start } ?: Offset.Zero
                        },
                        onDrag = { drag, amount ->
                            when (val now = target) {
                                is CropTarget.Corner -> {
                                    val at = layout.fractionOf(drag.position + grab)
                                    change(currentRegion.withCorner(now.corner, at.x, at.y))
                                }
                                CropTarget.Move -> change(currentRegion.moved(amount.x / layout.bounds.width, amount.y / layout.bounds.height))
                                CropTarget.None -> Unit
                            }
                            if (target != CropTarget.None) drag.consume()
                        },
                    )
                },
        ) {}
    }
}

/** The words of the crop box's accessibility actions (a screen reader can't drag). */
internal class CropActions(
    val left: String,
    val right: String,
    val up: String,
    val down: String,
    val larger: String,
    val smaller: String,
)

/** A fraction of the page as a rounded percentage, for the screen reader. */
private fun Float.percent(): String = "${(this * 100).roundToInt()}%"

/** What a drag that started at some point will do to the rectangle. */
internal sealed interface CropTarget {
    data class Corner(val corner: RegionCorner) : CropTarget

    /** Moves the whole rectangle. */
    data object Move : CropTarget

    /** Started outside the rectangle: does nothing. */
    data object None : CropTarget
}

/**
 * Where the page picture sits in the crop area ([bounds], in pixels of the area) and the arithmetic between that and a
 * [PageRegion]: the rectangle on screen, a screen point as a fraction of the page, and what a touch at a point grabs.
 * A data class so the gesture handler, which is keyed by it, isn't restarted (and a drag cut short) each time the rectangle moves.
 */
internal data class CropLayout(val bounds: Rect) {
    fun rectOf(region: PageRegion): Rect = Rect(
        bounds.left + region.left * bounds.width,
        bounds.top + region.top * bounds.height,
        bounds.left + region.right * bounds.width,
        bounds.top + region.bottom * bounds.height,
    )

    /** [position] as fractions of the page, kept on it. */
    fun fractionOf(position: Offset): Offset = Offset(
        ((position.x - bounds.left) / bounds.width).coerceIn(0f, 1f),
        ((position.y - bounds.top) / bounds.height).coerceIn(0f, 1f),
    )

    fun cornerOf(box: Rect, corner: RegionCorner): Offset = when (corner) {
        RegionCorner.TopLeft -> box.topLeft
        RegionCorner.TopRight -> box.topRight
        RegionCorner.BottomLeft -> box.bottomLeft
        RegionCorner.BottomRight -> box.bottomRight
    }

    /**
     * A touch within [reach] px of a corner of [box] takes the nearest one, inside the rectangle it takes the whole box, and
     * elsewhere nothing. The reach is at most a third of the box's smaller side, so a small box can still be moved.
     */
    fun targetAt(position: Offset, box: Rect, reach: Float): CropTarget {
        val limit = min(reach, min(box.width, box.height) / 3f)
        val nearest = RegionCorner.entries.minBy { hypot(cornerOf(box, it).x - position.x, cornerOf(box, it).y - position.y) }
        val corner = cornerOf(box, nearest)
        return when {
            hypot(corner.x - position.x, corner.y - position.y) <= limit -> CropTarget.Corner(nearest)
            box.contains(position) -> CropTarget.Move
            else -> CropTarget.None
        }
    }

    companion object {
        /** An image of [imageWidth] × [imageHeight] fitted, centred, in an area of [areaWidth] × [areaHeight] px. */
        fun fit(areaWidth: Float, areaHeight: Float, imageWidth: Int, imageHeight: Int): CropLayout {
            val scale = min(areaWidth / imageWidth, areaHeight / imageHeight)
            val width = imageWidth * scale
            val height = imageHeight * scale
            val left = (areaWidth - width) / 2f
            val top = (areaHeight - height) / 2f
            return CropLayout(Rect(left, top, left + width, top + height))
        }
    }
}

/** A page's fraction one arrow key press moves or resizes the rectangle by. */
private const val KEY_STEP = 0.01f

/** The same for one screen-reader action, which can't be repeated as fast as a key. */
private const val ACTION_STEP = 0.05f

private val CORNER_REACH = 28.dp
