package com.yahyafati.mnemo.core.ui.card.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.yahyafati.mnemo.core.ui.card.MediaImageLoader
import java.io.File

/**
 * [MediaImageLoader] on `BitmapFactory`: files are read from [directory] (one per hash), decoded
 * downsampled to screen size, and kept in a small memory cache.
 */
class AndroidMediaImageLoader(private val directory: File) : MediaImageLoader {
    override fun load(hash: String): ImageBitmap? = MediaBitmaps.load(File(directory, hash))
}

/** Decoded images, downsampled to screen size and kept in a small memory cache. */
private object MediaBitmaps {
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
