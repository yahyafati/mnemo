package com.yahyafati.mnemo.desktop

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.squareup.zstd.okio.zstdCompress
import com.squareup.zstd.okio.zstdDecompress
import okio.Buffer
import okio.buffer
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three native libraries the desktop app depends on load and work on this OS, and the ImageIO plugin that scanned PDF pages need. CI runs it on
 * Linux, Windows and macOS (D1); the D0 spike proved it on macOS arm64 and Linux x64 only.
 */
class NativeLibrariesTest {
    @Test
    fun `bundled SQLite opens a database and stores a row`() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            connection.execSQL("CREATE TABLE deck (id TEXT PRIMARY KEY, name TEXT NOT NULL)")
            connection.execSQL("INSERT INTO deck VALUES ('d1', 'Biology::Cells')")
            connection.prepare("SELECT name, sqlite_version() FROM deck").use { statement ->
                assertTrue(statement.step())
                assertEquals("Biology::Cells", statement.getText(0))
                assertTrue(statement.getText(1).startsWith("3."))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun `zstd compresses and decompresses`() {
        val text = "Mnemo remembers what you forget. ".repeat(200)
        val compressed = Buffer().also { buffer ->
            buffer.zstdCompress().buffer().use { it.writeUtf8(text) }
        }
        assertTrue(compressed.size < text.length)

        val restored = compressed.zstdDecompress().buffer().use { it.readUtf8() }
        assertEquals(text, restored)
    }

    @Test
    fun `Skia draws and encodes a PNG`() {
        val surface = Surface.makeRasterN32Premul(64, 32)
        surface.canvas.clear(Color.WHITE)
        surface.canvas.drawRect(
            org.jetbrains.skia.Rect.makeXYWH(8f, 8f, 16f, 16f),
            Paint().apply { color = Color.RED },
        )
        val png = checkNotNull(surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)).bytes

        // The PNG signature.
        assertEquals(listOf(0x89, 0x50, 0x4E, 0x47), png.take(4).map { it.toInt() and 0xFF })
        assertTrue(png.size > 60)
    }

    @Test
    fun `ImageIO finds the JBIG2 plugin PDFBox needs to draw scanned pages`() {
        // Without it a JBIG2 scan renders as a white page and nothing throws (ADR 0014, "As built (P0)").
        assertTrue(ImageIO.getImageReadersByFormatName("JBIG2").hasNext())
        // Pages are written as JPEG.
        assertTrue(ImageIO.getImageWritersByFormatName("jpeg").hasNext())
    }
}
