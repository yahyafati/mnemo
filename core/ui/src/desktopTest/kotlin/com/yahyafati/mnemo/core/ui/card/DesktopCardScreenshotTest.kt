package com.yahyafati.mnemo.core.ui.card

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.ui.platform.desktop.ProvideDesktopPlatform
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * What a card looks like on the desktop: Markdown, inline and display math drawn by JLaTeXMath, a
 * stored image decoded by Skia, and a sound link. The media folder is built here, the way the app
 * stores media: one file per hash, no extension.
 *
 * Record:  ./gradlew :core:ui:recordRoborazziDesktop
 * Verify:  ./gradlew :core:ui:verifyRoborazziDesktop
 */
@OptIn(ExperimentalTestApi::class)
class DesktopCardScreenshotTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val imageHash = "1".repeat(64)
    private val soundHash = "2".repeat(64)

    private fun mediaFolder(): File = folder.newFolder("media").also { media ->
        val image = BufferedImage(480, 160, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            paint = GradientPaint(0f, 0f, Color(0x4F46E5), 480f, 160f, Color(0x6FFBBE))
            fillRect(0, 0, 480, 160)
            dispose()
        }
        ImageIO.write(image, "png", File(media, imageHash))
        checkNotNull(javaClass.getResourceAsStream("/audio/tone.mp3")).use { it.copyTo(File(media, soundHash).outputStream()) }
    }

    private val mathCard = CardSides(
        front = "What is \\(\\int_0^1 x^2\\,dx\\)? Recall that \\(f(x) = x^2\\).\n\n" +
            "![area under the curve](${MediaRef.of(imageHash)})\n\n[sound:${MediaRef.of(soundHash)}]",
        back = "\\[\\int_0^1 x^2\\,dx = \\frac{1}{3}\\]\n\nBecause the antiderivative is \\(\\frac{x^3}{3}\\), " +
            "and \\(\\cancel{x}\\) is not TeX this renderer knows.",
    )

    private fun capture(darkTheme: Boolean, sides: CardSides, revealed: Boolean, file: String) =
        runDesktopComposeUiTest(width = 520, height = 620) {
            val media = mediaFolder()
            setContent {
                ProvideDesktopPlatform(mediaDirectory = media) {
                    MnemoTheme(darkTheme = darkTheme) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                            CardFace(sides = sides, revealed = revealed, modifier = Modifier.padding(24.dp))
                        }
                    }
                }
            }
            waitForIdle()
            onRoot().captureRoboImage(file)
        }

    @Test
    fun mathImageAndSoundOnTheFront() =
        capture(false, mathCard, revealed = false, file = "src/desktopTest/screenshots/card_math_front_light.png")

    @Test
    fun mathOnTheRevealedBackInDarkTheme() =
        capture(true, mathCard, revealed = true, file = "src/desktopTest/screenshots/card_math_back_dark.png")

    @Test
    fun aClozeCardKeepsItsDeletionNextToAFormula() =
        capture(
            false,
            CardSides(front = "The area is {{c1::\\(\\frac{1}{3}\\)}} of the unit square, since \\(x^2\\) is convex.", back = "", clozeOrdinal = 1),
            revealed = true,
            file = "src/desktopTest/screenshots/card_math_cloze_light.png",
        )

    @Test
    fun texTheRendererCannotDrawStaysReadable() = runDesktopComposeUiTest(width = 520, height = 400) {
        setContent {
            ProvideDesktopPlatform(mediaDirectory = folder.newFolder("empty")) {
                MnemoTheme { CardFace(sides = CardSides(front = "Cancel \\(\\cancel{x}\\) out", back = ""), revealed = false) }
            }
        }
        waitForIdle()

        onNodeWithText("\\cancel{x}", substring = true).assertExists()
    }
}
