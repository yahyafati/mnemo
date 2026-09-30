package com.yahyafati.mnemo.core.ui.card

import com.yahyafati.mnemo.core.ui.card.desktop.DesktopMediaImageLoader
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class DesktopMediaImageLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun png(name: String, width: Int, height: Int): File =
        File(folder.root, name).also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }

    @Test
    fun aStoredImageIsDecodedFromItsBytesNotItsName() {
        png("a".repeat(64), 40, 20)

        val image = assertNotNull(DesktopMediaImageLoader(folder.root).load("a".repeat(64)))

        assertEquals(40 to 20, image.width to image.height)
    }

    @Test
    fun aMissingOrUndecodableFileLoadsNothing() {
        File(folder.root, "b".repeat(64)).writeText("not an image")

        val loader = DesktopMediaImageLoader(folder.root)

        assertNull(loader.load("c".repeat(64)))
        assertNull(loader.load("b".repeat(64)))
    }

    @Test
    fun aHugeImageIsScaledDownToScreenSize() {
        png("d".repeat(64), 4096, 1024)

        val image = assertNotNull(DesktopMediaImageLoader(folder.root).load("d".repeat(64)))

        assertEquals(2048 to 512, image.width to image.height)
    }

    @Test
    fun aSecondLoadComesFromTheCache() {
        png("e".repeat(64), 10, 10)
        val loader = DesktopMediaImageLoader(folder.root)

        val first = assertNotNull(loader.load("e".repeat(64)))
        File(folder.root, "e".repeat(64)).delete()

        assertSame(first, loader.load("e".repeat(64)))
    }
}
