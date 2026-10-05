package com.yahyafati.mnemo.feature.create

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.MediaImageLoader
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/** The page grid and the large view with a keyboard, which only the desktop (or a tablet with one) has: docs/pdf/ROADMAP.md, P8. */
@OptIn(ExperimentalTestApi::class)
class PdfPageKeysTest {
    private val files = object : PdfPageFiles {
        override suspend fun thumbnail(page: Int) = File("p$page.jpg")

        override suspend fun page(page: Int) = File("p$page.jpg")
    }
    private val loader = object : MediaImageLoader {
        override fun load(hash: String): ImageBitmap? = null

        override fun loadFile(file: File): ImageBitmap = ImageBitmap(40, 30).also { Canvas(it).drawRect(Rect(0f, 0f, 40f, 30f), Paint()) }
    }

    @Test
    fun spaceTicksArrowsMoveAndEnterShowsThePageLarge() = runDesktopComposeUiTest(width = 900, height = 900) {
        var pdf by mutableStateOf(PdfSummary(handleId = "1", title = "Slides", pageCount = 6, pages = ""))
        val actions = mutableListOf<SmartExtractAction>()
        setContent {
            MnemoTheme {
                CompositionLocalProvider(
                    LocalMediaImageLoader provides loader,
                    LocalPlatformCapabilities provides PlatformCapabilities(keyboardAndMouse = true),
                ) {
                    PdfPageGrid(
                        pdf = pdf,
                        pageFiles = files,
                        reading = false,
                        onAction = { action ->
                            actions += action
                            if (action is SmartExtractAction.TogglePdfPage) {
                                val now = if (action.page in pdf.selectedPages) pdf.selectedPages - action.page else pdf.selectedPages + action.page
                                pdf = pdf.copy(selectedPages = now)
                            }
                        },
                    )
                }
            }
        }
        onNodeWithText("Arrow keys move between pages, Space ticks a page and Enter shows it large.").assertExists()

        // A click gives the page the focus (and ticks it); Space then unticks it and ticks it again.
        onNodeWithContentDescription("Page 2").performClick()
        onNodeWithContentDescription("Page 2").assertIsFocused()
        assertEquals(setOf(2), pdf.selectedPages)
        onNodeWithContentDescription("Page 2").performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(emptySet(), pdf.selectedPages)
        onNodeWithContentDescription("Page 2").performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(setOf(2), pdf.selectedPages)

        // An arrow key moves the focus to the next page, where Space ticks too.
        onNodeWithContentDescription("Page 2").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithContentDescription("Page 3").assertIsFocused()
        onNodeWithContentDescription("Page 3").performKeyInput { pressKey(Key.Spacebar) }
        assertEquals(setOf(2, 3), pdf.selectedPages)

        // Enter asks for the page large and ticks nothing.
        actions.clear()
        onNodeWithContentDescription("Page 3").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf<SmartExtractAction>(SmartExtractAction.OpenPdfPage(3)), actions)
        assertEquals(setOf(2, 3), pdf.selectedPages)
    }

    @Test
    fun theLargeViewTurnsPagesWithTheArrowKeys() = runDesktopComposeUiTest(width = 900, height = 900) {
        val actions = mutableListOf<SmartExtractAction>()
        setContent {
            MnemoTheme {
                CompositionLocalProvider(
                    LocalMediaImageLoader provides loader,
                    LocalPlatformCapabilities provides PlatformCapabilities(keyboardAndMouse = true),
                ) {
                    PdfPageViewerContent(
                        pdf = PdfSummary(handleId = "1", title = "Slides", pageCount = 6, pages = ""),
                        page = 3,
                        pageFiles = files,
                        onAction = { actions += it },
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Page 3 of 6").assertExists()
        onNodeWithContentDescription("Page 3 of the PDF").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithContentDescription("Page 3 of the PDF").performKeyInput { pressKey(Key.DirectionLeft) }
        assertEquals(listOf<SmartExtractAction>(SmartExtractAction.OpenPdfPage(4), SmartExtractAction.OpenPdfPage(2)), actions)
    }
}
