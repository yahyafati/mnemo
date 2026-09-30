package com.yahyafati.mnemo

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.di.TestMnemoApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.core.component.inject
import org.koin.test.KoinTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * With no provider, the AI entry point shows a setup prompt instead of failing (ROADMAP Phase 3),
 * and it leads through adding one.
 */
@Config(application = TestMnemoApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class AiProvidersFlowTest : KoinTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val providers: AiProviderRepository by inject()

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun field(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasSetTextAction() and hasText(label)).performScrollTo()

    @Test
    fun smartExtractLeadsToProviderSetup() {
        awaitText("No decks yet")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("Smart Extract").performClick()
        awaitText("Connect an AI provider")
        composeRule.onNodeWithText("Set up a provider").performClick()

        awaitText("No AI providers")
        // Not a tab: the shell's bars are gone.
        composeRule.onNodeWithText("Analytics").assertDoesNotExist()
        composeRule.onNodeWithText("Add provider").performClick()

        awaitText("Start from")
        composeRule.onNodeWithText("Ollama").performClick()
        // localhost on a phone is the phone: the editor explains, and the LAN address is accepted.
        awaitText("On a phone, localhost is the phone itself")
        field("Base URL").performTextReplacement("http://192.168.1.20:11434/v1")
        field("Default model").performTextReplacement("llama3.2")
        composeRule.onNodeWithText("Save").performClick()

        awaitText("192.168.1.20 · llama3.2")
        composeRule.onNodeWithText("Default").assertExists()
        val route = runBlocking { providers.routeFor(AiTask.Extract) }!!
        assertEquals("Ollama", route.provider.name)
        assertTrue(route.provider.isLocal)

        composeRule.onNodeWithContentDescription("Back").performClick()
        awaitText("Ollama · llama3.2")
    }

    @Test
    fun plainHttpToAPublicServerIsRefused() {
        awaitText("No decks yet")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        awaitText("No provider yet")
        composeRule.onNodeWithText("AI providers").performScrollTo().performClick()
        composeRule.onNodeWithText("Add provider").performClick()
        awaitText("Start from")
        composeRule.onNodeWithText("Custom").performClick()
        field("Base URL").performTextReplacement("http://api.example.com/v1")
        awaitText("Use https://")
        composeRule.onNodeWithText("Save").performClick()
        // Still in the editor, nothing saved.
        composeRule.onNodeWithText("Start from").assertExists()
        assertTrue(runBlocking { providers.routeFor(AiTask.Extract) } == null)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
