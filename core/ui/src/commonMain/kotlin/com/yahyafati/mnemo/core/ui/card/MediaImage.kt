package com.yahyafati.mnemo.core.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.MediaRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yahyafati.mnemo.core.ui.resources.Res
import com.yahyafati.mnemo.core.ui.resources.core_ui_image_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Reads a stored image (`media:<hash>`) for display: finds its file, decodes it, keeps recent ones
 * in memory. Android decodes with `BitmapFactory`; the desktop app with Skia (desktop D5).
 */
interface MediaImageLoader {
    /** The image for media [hash], downsampled to screen size; null if it's missing or can't be decoded. Blocking. */
    fun load(hash: String): ImageBitmap?

    /**
     * The image in [file], which isn't media storage (a rendered PDF page in the cache), decoded and downsampled like
     * [load]; null if it is missing or can't be decoded. Blocking. A loader that can't read files says so with null.
     */
    fun loadFile(file: java.io.File): ImageBitmap? = null

    companion object {
        /** Loads nothing: previews, tests, and a platform that has not provided a loader. */
        val None: MediaImageLoader = object : MediaImageLoader {
            override fun load(hash: String): ImageBitmap? = null
        }
    }
}

/** The app's [MediaImageLoader]; [MediaImageLoader.None] unless the app shell provides one. */
val LocalMediaImageLoader = staticCompositionLocalOf { MediaImageLoader.None }

/**
 * An image from media storage (`![alt](media:<hash>)`), full width and at most [MAX_HEIGHT] tall.
 * Other sources (web URLs, missing files, formats the platform can't decode such as SVG) show a
 * small placeholder with the alt text: cards never load anything from the network.
 */
@Composable
fun MediaImage(src: String, alt: String, modifier: Modifier = Modifier) {
    val loader = LocalMediaImageLoader.current
    val hash = MediaRef.hashOf(src)
    val state by produceState<ImageState>(ImageState.Loading, hash, loader) {
        value = if (hash == null) {
            ImageState.Missing
        } else {
            withContext(Dispatchers.IO) { loader.load(hash) }?.let(ImageState::Loaded) ?: ImageState.Missing
        }
    }
    ImageContent(state, alt, modifier, MAX_HEIGHT)
}

/**
 * An image that isn't in media storage yet, shown the way [MediaImage] shows a stored one: a figure cut from a PDF page for
 * a card in the review queue (docs/pdf/ROADMAP.md, P7), which is stored when the card is accepted. At most [maxHeight] tall.
 */
@Composable
fun MediaFileImage(file: java.io.File, alt: String, modifier: Modifier = Modifier, maxHeight: Dp = MAX_HEIGHT) {
    val loader = LocalMediaImageLoader.current
    val state by produceState<ImageState>(ImageState.Loading, file, loader) {
        value = withContext(Dispatchers.IO) { loader.loadFile(file) }?.let(ImageState::Loaded) ?: ImageState.Missing
    }
    ImageContent(state, alt, modifier, maxHeight)
}

@Composable
private fun ImageContent(state: ImageState, alt: String, modifier: Modifier, maxHeight: Dp) {
    when (state) {
        ImageState.Loading -> Unit
        is ImageState.Loaded -> Image(
            bitmap = state.bitmap,
            contentDescription = alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            // Small images line up with the text rather than floating in the middle.
            alignment = Alignment.CenterStart,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight),
        )
        ImageState.Missing -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs),
        ) {
            Icon(MnemoIcons.Image, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            Text(
                text = alt.ifBlank { stringResource(Res.string.core_ui_image_unavailable) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val MAX_HEIGHT = 320.dp

private sealed interface ImageState {
    data object Loading : ImageState

    data class Loaded(val bitmap: ImageBitmap) : ImageState

    data object Missing : ImageState
}
