package com.yahyafati.mnemo.feature.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.clickCursor
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.PageImageBatches
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.feature.create.resources.Res
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_clear
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_keys
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_none
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_page
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_page_blank
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_select_all
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_summary
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_ticked
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_grid_view
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_confirm_requests
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_close
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_keys
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_image
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_next
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_previous
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_title
import com.yahyafati.mnemo.feature.create.resources.feature_create_pdf_view_unavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The page grid of Cards from page images (docs/pdf/ROADMAP.md, P6): a thumbnail of every page of the open PDF with a checkbox,
 * standing where the text box is in the other modes. Ticking only sends [SmartExtractAction.TogglePdfPage]: the Pages field
 * is the single source of what is ticked, so the field and the grid never disagree. The grid scrolls inside a fixed height,
 * since it sits in the screen's own scrolling column; thumbnails are drawn as they come into view.
 */
@Composable
internal fun PdfPageGrid(
    pdf: PdfSummary,
    pageFiles: PdfPageFiles,
    reading: Boolean,
    onAction: (SmartExtractAction) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val spacing = MnemoTheme.spacing
    val ticked = pdf.selectedPages.size
    val requests = PageImageBatches.of(pdf.selectedPages.toList()).size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (ticked == 0) {
                    stringResource(Res.string.feature_create_pdf_grid_none)
                } else {
                    stringResource(
                        Res.string.feature_create_pdf_grid_summary,
                        pluralStringResource(Res.plurals.feature_create_pdf_grid_ticked, ticked, ticked),
                        pluralStringResource(Res.plurals.feature_create_pdf_confirm_requests, requests, requests),
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAction(SmartExtractAction.SelectAllPdfPages(true)) }, enabled = !reading) {
                Text(stringResource(Res.string.feature_create_pdf_grid_select_all))
            }
            TextButton(onClick = { onAction(SmartExtractAction.SelectAllPdfPages(false)) }, enabled = !reading && ticked > 0) {
                Text(stringResource(Res.string.feature_create_pdf_grid_clear))
            }
        }
        if (LocalPlatformCapabilities.current.keyboardAndMouse) {
            Text(
                text = stringResource(Res.string.feature_create_pdf_grid_keys),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .height(GRID_HEIGHT)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(spacing.sm),
        ) {
            items(count = pdf.pageCount, key = { it + 1 }, contentType = { "page" }) { index ->
                val page = index + 1
                PageThumbnail(
                    page = page,
                    checked = page in pdf.selectedPages,
                    pageFiles = pageFiles,
                    enabled = !reading,
                    onToggle = { onAction(SmartExtractAction.TogglePdfPage(page)) },
                    onView = { onAction(SmartExtractAction.OpenPdfPage(page)) },
                )
            }
        }
    }
}

/** One page of the grid: its picture, its number, a checkbox, and a button to see it large. The whole cell ticks it. */
@Composable
private fun PageThumbnail(
    page: Int,
    checked: Boolean,
    pageFiles: PdfPageFiles,
    enabled: Boolean,
    onToggle: () -> Unit,
    onView: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val loader = LocalMediaImageLoader.current
    val focusManager = LocalFocusManager.current
    val picture by produceState<Thumbnail>(Thumbnail.Loading, page, pageFiles, loader) {
        value = withContext(Dispatchers.IO) { pageFiles.thumbnail(page)?.let { loader.loadFile(it) } }?.let(Thumbnail::Loaded) ?: Thumbnail.Blank
    }
    val label = stringResource(if (picture == Thumbnail.Blank) Res.string.feature_create_pdf_grid_page_blank else Res.string.feature_create_pdf_grid_page, page)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerLowest,
        modifier = Modifier
            .aspectRatio(THUMBNAIL_RATIO)
            .border(if (checked) 2.dp else 1.dp, if (checked) colors.primary else colors.outlineVariant, MaterialTheme.shapes.small)
            // The cell is the one thing in the grid that takes the focus (its "view large" button is skipped), so the arrow keys
            // go from page to page and these two keys are always meant for the page.
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Spacebar -> onToggle()
                    Key.Enter, Key.NumPadEnter -> onView()
                    // The desktop doesn't move the focus on arrow keys by itself. Sideways goes page by page, so the end of a row
                    // continues on the next one; up and down keep the column.
                    Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Next)
                    Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Previous)
                    Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                    Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .clickCursor()
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val state = picture) {
                Thumbnail.Loading -> Unit
                is Thumbnail.Loaded -> Image(state.bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                Thumbnail.Blank -> Icon(MnemoIcons.Image, null, tint = colors.outline, modifier = Modifier.align(Alignment.Center).size(24.dp))
            }
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            Text(
                text = page.toString(),
                style = MnemoTheme.typography.metricSm,
                color = colors.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(colors.surfaceContainerLowest.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 4.dp),
            )
            MnemoIconButton(
                icon = MnemoIcons.Search,
                contentDescription = stringResource(Res.string.feature_create_pdf_grid_view, page),
                onClick = onView,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp).focusProperties { canFocus = false },
            )
        }
    }
}

private sealed interface Thumbnail {
    data object Loading : Thumbnail

    data class Loaded(val bitmap: ImageBitmap) : Thumbnail

    /** Nothing to show: a page with nothing on it, or one the renderer can't draw. It can still be ticked. */
    data object Blank : Thumbnail
}

/**
 * A page of the open PDF, large, in a window of its own: pinch (or scroll the wheel) to zoom, drag to move, Esc or the
 * button to close, and the neighbouring pages one button away. Shows the page at the quality its image is sent at.
 */
@Composable
internal fun PdfPageViewer(
    pdf: PdfSummary,
    page: Int,
    pageFiles: PdfPageFiles,
    onAction: (SmartExtractAction) -> Unit,
) {
    Dialog(onDismissRequest = { onAction(SmartExtractAction.ClosePdfPage) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            PdfPageViewerContent(pdf, page, pageFiles, onAction)
        }
    }
}

/** The large view without its window, so it can be shown (and tested) on its own. */
@Composable
internal fun PdfPageViewerContent(
    pdf: PdfSummary,
    page: Int,
    pageFiles: PdfPageFiles,
    onAction: (SmartExtractAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loader = LocalMediaImageLoader.current
    val picture by produceState<ImageBitmap?>(null, page, pageFiles, loader) {
        value = null
        value = withContext(Dispatchers.IO) { pageFiles.page(page)?.let { loader.loadFile(it) } }
    }
    val loaded = picture
    val zoom = remember(page) { ZoomState() }
    val focus = remember { FocusRequester() }
    val keyboard = LocalPlatformCapabilities.current.keyboardAndMouse
    // The keys work wherever the focus is in this window; it starts on the picture so they work at once.
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val pan = event.key.pan()
                when {
                    event.isShiftPressed && pan != null && zoom.scale > 1f -> zoom.pan(pan)
                    event.key == Key.DirectionLeft && page > 1 -> onAction(SmartExtractAction.OpenPdfPage(page - 1))
                    event.key == Key.DirectionRight && page < pdf.pageCount -> onAction(SmartExtractAction.OpenPdfPage(page + 1))
                    event.key in ZOOM_IN_KEYS -> zoom.zoom(KEY_ZOOM)
                    event.key in ZOOM_OUT_KEYS -> zoom.zoom(1f / KEY_ZOOM)
                    event.key == Key.Zero || event.key == Key.NumPad0 -> zoom.reset()
                    else -> return@onKeyEvent false
                }
                true
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MnemoTheme.spacing.sm, vertical = MnemoTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MnemoIconButton(
                icon = MnemoIcons.Close,
                contentDescription = stringResource(Res.string.feature_create_pdf_view_close),
                onClick = { onAction(SmartExtractAction.ClosePdfPage) },
                shortcut = "Esc",
            )
            Text(
                text = stringResource(Res.string.feature_create_pdf_view_title, page, pdf.pageCount),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = MnemoTheme.spacing.sm),
            )
            MnemoIconButton(
                icon = MnemoIcons.ArrowBack,
                contentDescription = stringResource(Res.string.feature_create_pdf_view_previous),
                onClick = { onAction(SmartExtractAction.OpenPdfPage(page - 1)) },
                enabled = page > 1,
            )
            MnemoIconButton(
                icon = MnemoIcons.ArrowForward,
                contentDescription = stringResource(Res.string.feature_create_pdf_view_next),
                onClick = { onAction(SmartExtractAction.OpenPdfPage(page + 1)) },
                enabled = page < pdf.pageCount,
            )
        }
        if (keyboard) {
            Text(
                text = stringResource(Res.string.feature_create_pdf_view_keys),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MnemoTheme.spacing.md),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth().focusRequester(focus).focusable(), contentAlignment = Alignment.Center) {
            when {
                loaded != null -> ZoomableImage(loaded, stringResource(Res.string.feature_create_pdf_view_image, page), zoom)
                else -> Text(
                    text = stringResource(Res.string.feature_create_pdf_view_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How far the large view is zoomed and moved. Hoisted so the keys, the wheel and a pinch all drive the same state; it resets with the page. */
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set

    fun zoom(factor: Float) {
        scale = (scale * factor).coerceIn(1f, MAX_ZOOM)
        if (scale == 1f) offset = Offset.Zero
    }

    fun pan(by: Offset) {
        if (scale > 1f) offset += by
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }
}

/** The way Shift + an arrow key moves a zoomed page, or null for any other key. */
private fun Key.pan(): Offset? = when (this) {
    Key.DirectionLeft -> Offset(KEY_PAN, 0f)
    Key.DirectionRight -> Offset(-KEY_PAN, 0f)
    Key.DirectionUp -> Offset(0f, KEY_PAN)
    Key.DirectionDown -> Offset(0f, -KEY_PAN)
    else -> null
}

/** An image the user can zoom (pinch, the wheel or + and -) and move (drag, or Shift + arrows). Never smaller than the window. */
@Composable
private fun ZoomableImage(bitmap: ImageBitmap, description: String, zoom: ZoomState) {
    Image(
        bitmap = bitmap,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(zoom) {
                detectTransformGestures { _, pan, factor, _ ->
                    zoom.zoom(factor)
                    zoom.pan(pan)
                }
            }
            .pointerInput(zoom) {
                // The mouse wheel zooms on the desktop, where a pinch is rare.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.first().scrollDelta.y
                            if (delta != 0f) zoom.zoom(if (delta < 0) WHEEL_STEP else 1f / WHEEL_STEP)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            },
    )
}

private val GRID_HEIGHT = 340.dp
private const val THUMBNAIL_RATIO = 0.75f
private const val MAX_ZOOM = 5f
private const val WHEEL_STEP = 1.15f
private const val KEY_ZOOM = 1.25f
private const val KEY_PAN = 80f
private val ZOOM_IN_KEYS = setOf(Key.Plus, Key.Equals, Key.NumPadAdd)
private val ZOOM_OUT_KEYS = setOf(Key.Minus, Key.NumPadSubtract)
