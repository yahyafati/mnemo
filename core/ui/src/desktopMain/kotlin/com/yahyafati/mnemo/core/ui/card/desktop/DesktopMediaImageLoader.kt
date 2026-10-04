package com.yahyafati.mnemo.core.ui.card.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.yahyafati.mnemo.core.ui.card.MediaImageLoader
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.io.File

/**
 * [MediaImageLoader] on Skia: files are read from [directory] (one per hash, no extension, so the
 * format is sniffed from the bytes), decoded, scaled down to [MAX_SIDE] pixels on the longer side,
 * and kept in a small memory cache of about [CACHE_BYTES].
 */
class DesktopMediaImageLoader(private val directory: File) : MediaImageLoader {
    private val cache = BitmapCache(CACHE_BYTES)

    override fun load(hash: String): ImageBitmap? = loadFile(File(directory, hash))

    override fun loadFile(file: File): ImageBitmap? {
        cache[file.path]?.let { return it }
        if (!file.isFile) return null
        val decoded = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull() ?: return null
        val bitmap = decoded.use { downscaled(it).toComposeImageBitmap() }
        cache[file.path] = bitmap
        return bitmap
    }

    private fun downscaled(image: Image): Image {
        val longest = maxOf(image.width, image.height)
        if (longest <= MAX_SIDE) return image
        val scale = MAX_SIDE.toFloat() / longest
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val target = Bitmap().apply { allocPixels(ImageInfo.makeN32Premul(width, height)) }
        Canvas(target).use { canvas ->
            canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                SamplingMode.LINEAR,
                null,
                true,
            )
        }
        return Image.makeFromBitmap(target)
    }

    /** Least recently used first; evicts until the pixels fit. */
    private class BitmapCache(private val maxBytes: Long) {
        private val entries = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {}
        private var bytes = 0L

        @Synchronized
        operator fun get(key: String): ImageBitmap? = entries[key]

        @Synchronized
        operator fun set(key: String, value: ImageBitmap) {
            entries.put(key, value)?.let { bytes -= it.size() }
            bytes += value.size()
            val iterator = entries.entries.iterator()
            while (bytes > maxBytes && entries.size > 1 && iterator.hasNext()) {
                val eldest = iterator.next()
                if (eldest.key == key) continue
                bytes -= eldest.value.size()
                iterator.remove()
            }
        }

        private fun ImageBitmap.size() = width.toLong() * height * 4
    }

    private companion object {
        const val MAX_SIDE = 2048
        const val CACHE_BYTES = 32L * 1024 * 1024
    }
}
