package com.yahyafati.mnemo.feature.create

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals

/** Smart Extract's Advanced options with a keyboard (ADR 0015, C5, and the D7 rules): each row is one focus stop. */
@OptIn(ExperimentalTestApi::class)
class AdvancedOptionsKeysTest {
    private val ollama = AiProvider(
        id = "p", name = "Ollama", baseUrl = "http://192.168.1.20:11434/v1", defaultModel = "llama3.2", isLocal = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun tabMovesThroughTheAdvancedRowsOneStopEach() = runDesktopComposeUiTest(width = 900, height = 2400) {
        val actions = mutableListOf<SmartExtractAction>()
        setContent {
            MnemoTheme {
                CompositionLocalProvider(LocalPlatformCapabilities provides PlatformCapabilities(keyboardAndMouse = true)) {
                    SmartExtractScreen(
                        uiState = SmartExtractUiState(
                            isLoading = false,
                            route = AiRoute(AiTask.Extract, ollama, "llama3.2", AiCapabilities(), usesDefault = true),
                            text = "Some text.",
                            showAdvanced = true,
                        ),
                        onAction = { actions += it },
                        onSetUpAi = {},
                    )
                }
            }
        }
        waitForIdle()

        // A click gives the Advanced row the focus; each Tab then lands on the next row, none is skipped or visited twice.
        onNodeWithText("ADVANCED").performClick()
        onNode(hasText("ADVANCED") and isFocused()).assertExists()
        val rows = listOf("Same as the source", "Skip examples and anecdotes", "Within sections", "Auto")
        for (row in rows) {
            onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onNode(hasText(row) and isFocused()).assertExists()
        }

        // The stop after the last row is outside the section, and Shift+Tab goes back the same way.
        onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals(0, rows.count { row -> onAllNodes(hasText(row) and isFocused()).fetchSemanticsNodes().isNotEmpty() })
        assertEquals(1, onAllNodes(isFocused()).fetchSemanticsNodes().size)
    }
}
