package com.yahyafati.mnemo.core.ui.card

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An image from media storage (`![alt](media:<hash>)`), full width and at most [maxHeight] tall.
 * Other sources (web URLs, missing files, formats Android can't decode such as SVG) show a small
 * placeholder with the alt text: cards never load anything from the network.
 */
@Composable
fun MediaImage(src: String, alt: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hash = MediaRef.hashOf(src)
    val state by produceState<ImageState>(ImageState.Loading, hash) {
        value = if (hash == null) {
            ImageState.Missing
        } else {
            withContext(Dispatchers.IO) { MediaBitmaps.load(File(File(context.filesDir, MediaRef.DIRECTORY), hash)) }
                ?.let(ImageState::Loaded) ?: ImageState.Missing
        }
    }
    when (val s = state) {
        ImageState.Loading -> Unit
        is ImageState.Loaded -> Image(
            bitmap = s.bitmap,
            contentDescription = alt.ifBlank { null },
            contentScale = ContentScale.Fit,
            // Small images line up with the text rather than floating in the middle.
            alignment = Alignment.CenterStart,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = MAX_HEIGHT),
        )
        ImageState.Missing -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs),
        ) {
            Icon(MnemoIcons.Image, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
            Text(
                text = alt.ifBlank { stringResource(R.string.core_ui_image_unavailable) },
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

/** Decoded images, downsampled to screen size and kept in a small memory cache. */
internal object MediaBitmaps {
    private const val MAX_SIDE = 2048
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun load(file: File): ImageBitmap? {
        cache.get(file.path)?.let { return it }
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE || bounds.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap: Bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return bitmap.asImageBitmap().also { cache.put(file.path, it) }
    }
}
