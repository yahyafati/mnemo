package com.yahyafati.mnemo.core.ui.card

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.ui.card.web.LocalMathRenderer
import com.yahyafati.mnemo.core.ui.card.web.MathRenderer
import com.yahyafati.mnemo.core.ui.card.web.MathText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** What cards draw goes through what the platform provides: a math renderer and an image loader. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PlatformSeamsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val hash = "a".repeat(64)

    @Test
    fun mathShowsItsTexWhenThePlatformHasNoRenderer() {
        composeRule.setContent { MnemoTheme { MathText("Energy is E = mc^2 in a vacuum", TextStyle.Default, serif = false) } }

        composeRule.onNodeWithText("Energy is E = mc^2 in a vacuum", substring = true).assertExists()
    }

    @Test
    fun aPlatformMathRendererIsUsedInstead() {
        val seen = mutableListOf<String>()
        val renderer = object : MathRenderer {
            @androidx.compose.runtime.Composable
            override fun Render(markdown: String, style: TextStyle, serif: Boolean, modifier: androidx.compose.ui.Modifier, clozeOrdinal: Int?, revealed: Boolean) {
                seen += markdown
                androidx.compose.material3.Text("rendered by the platform")
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalMathRenderer provides renderer) {
                MnemoTheme { MathText("\\(x^2\\)", TextStyle.Default, serif = true) }
            }
        }

        composeRule.onNodeWithText("rendered by the platform").assertExists()
        assertEquals(listOf("\\(x^2\\)"), seen)
    }

    @Test
    fun anImageComesFromThePlatformLoader() {
        val loaded = mutableListOf<String>()
        val loader = object : MediaImageLoader {
            override fun load(hash: String): ImageBitmap {
                loaded += hash
                return ImageBitmap(8, 8)
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalMediaImageLoader provides loader) {
                MnemoTheme { MediaImage(MediaRef.of(hash), "a cell") }
            }
        }
        // The image loads off the main thread, which the test clock doesn't wait for.
        composeRule.waitUntilExactlyOneExists(hasContentDescription("a cell"), timeoutMillis = 5_000)

        assertEquals(listOf(hash), loaded)
    }

    @Test
    fun anImageThatCannotBeLoadedShowsItsAltText() {
        composeRule.setContent { MnemoTheme { MediaImage(MediaRef.of(hash), "a cell") } }

        composeRule.waitUntilExactlyOneExists(hasText("a cell"), timeoutMillis = 5_000)
    }

    @Test
    fun anImageThatIsNotStoredMediaShowsItsAltTextWithoutAskingTheLoader() {
        val loader = object : MediaImageLoader {
            override fun load(hash: String): ImageBitmap = error("must not be asked")
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalMediaImageLoader provides loader) {
                MnemoTheme { MediaImage("https://example.com/cell.png", "from the web") }
            }
        }

        composeRule.waitUntilExactlyOneExists(hasText("from the web"), timeoutMillis = 5_000)
    }
}
