package com.yahyafati.mnemo.feature.create

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Smart Extract through the real screen and ViewModel: paste, generate, review, accept. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SmartExtractScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val providers = FakeAiProviderRepository().apply {
        addProvider(
            AiProvider(
                id = "p", name = "Ollama", baseUrl = "http://192.168.1.20:11434/v1", defaultModel = "llama3.2", isLocal = true,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
    }
    private val decks = FakeDeckRepository()
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()

    private val sources = FakeSourceRepository()

    private val viewModel = run {
        runBlocking { decks.saveDeck("Neuroscience") }
        SmartExtractViewModel(
            providers, decks, sources,
            GenerateCardsUseCase(generation, cards), RegenerateCardUseCase(generation, cards), AcceptGeneratedCardsUseCase(cards), BookHandoff(),
        )
    }

    @Test
    fun pasteGenerateReviewAccept() {
        composeRule.setContent {
            MnemoTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartExtractScreen(state, viewModel::onAction, onSetUpAi = {})
            }
        }
        generation.answer(
            card("What does the amygdala process?", "Emotional memories, especially fear."),
            card("The {{c1::basolateral complex}} receives cortical input.", kind = NoteKind.Cloze),
            card("Which nucleus drives freezing?", "The central nucleus."),
        )
        composeRule.onNodeWithText("Ollama · llama3.2").assertExists()
        composeRule.onNodeWithText("Source text").performTextInput("The amygdala processes emotional memories, especially fear.")
        composeRule.onNodeWithText("7 words").assertExists()

        // First request to this provider: the notice comes first.
        composeRule.onNodeWithText("Generate ~3 cards").performScrollTo().performClick()
        composeRule.onNodeWithText("Send to Ollama?").assertExists()
        composeRule.onNodeWithText("Continue").performClick()

        val list = composeRule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("What does the amygdala process?"))
        composeRule.onNodeWithText("Emotional memories, especially fear.").assertExists()
        assertTrue(cards.notes.value.isEmpty())

        // Each proposed card can be reported (Play's AI-content policy): asks first, cancel sends nothing.
        composeRule.onAllNodesWithContentDescription("Report").onFirst().performClick()
        composeRule.onNodeWithText("Report this AI output?").assertExists()
        composeRule.onAllNodesWithText("What does the amygdala process?", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Report this AI output?").assertDoesNotExist()

        // Discard one, accept the rest together.
        list.performScrollToNode(hasText("Which nucleus drives freezing?"))
        composeRule.onNodeWithText("CARD 03").assertExists()
        viewModel.uiState.value.queue.last().card.id.let { id ->
            viewModel.onAction(SmartExtractAction.Discard(id))
        }
        composeRule.onNodeWithText("Ready to add to Neuroscience").assertExists()
        composeRule.onNodeWithText("Accept all (2)").performClick()

        composeRule.onNodeWithText("2 cards added to Neuroscience").assertExists()
        assertEquals(2, cards.notes.value.size)
        assertTrue(cards.notes.value.values.all { it.source == NoteSource.Ai })
        composeRule.onNodeWithText("Accept all (2)").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Clear source text").assertExists()
    }

    @Test
    fun aBooksChapterBecomesTheSourceText() {
        sources.books[SourceInput.Epub("/books/origin.epub")] = BookResult.Success(
            BookSource(
                title = "Origin",
                chapters = listOf(
                    BookChapter(0, "Contents", "Chapter list", ChapterKind.FrontMatter),
                    BookChapter(1, "Variation", "Animals vary under domestication."),
                ),
            ),
        )
        composeRule.setContent {
            MnemoTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartExtractScreen(state, viewModel::onAction, onSetUpAi = {})
            }
        }
        composeRule.onNodeWithText("EPUB").performClick()
        composeRule.onNodeWithText("Choose an EPUB").assertExists()

        // The file picker is the platform's; what it reports goes through the same action.
        viewModel.onAction(SmartExtractAction.EpubPicked("/books/origin.epub"))
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Chapters of Origin").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Front matter", substring = true).assertExists()
        composeRule.onNodeWithText("Variation").performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Chapters of Origin").fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText("From: Origin — Variation").assertExists()
        composeRule.onNodeWithText("Chapter: Variation").assertExists()
        composeRule.onNodeWithText("Change chapter").assertExists()
        assertEquals("Animals vary under domestication.", viewModel.uiState.value.text)
        assertTrue(cards.notes.value.isEmpty())
    }

    @Test
    fun aBookWithDrmSaysSo() {
        sources.books[SourceInput.Epub("/books/drm.epub")] = BookResult.Failure(SourceProblem.Drm)
        composeRule.setContent {
            MnemoTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartExtractScreen(state, viewModel::onAction, onSetUpAi = {})
            }
        }
        composeRule.onNodeWithText("EPUB").performClick()
        viewModel.onAction(SmartExtractAction.EpubPicked("/books/drm.epub"))
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("protected by DRM", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun dictationIsOfferedOnlyWhereThePlatformAndTheDeviceHaveIt() {
        var capabilities by mutableStateOf(PlatformCapabilities())
        var state by mutableStateOf(viewModel.uiState.value)
        composeRule.setContent {
            CompositionLocalProvider(LocalPlatformCapabilities provides capabilities) {
                MnemoTheme { SmartExtractScreen(state, {}, onSetUpAi = {}) }
            }
        }
        composeRule.onNodeWithText("Dictation").assertExists()

        // The platform has no dictation (the desktop app at first).
        capabilities = PlatformCapabilities(dictation = false)
        composeRule.onNodeWithText("Dictation").assertDoesNotExist()
        composeRule.onNodeWithText("PDF").assertExists()

        // The platform has it, but this device has no speech recognizer.
        capabilities = PlatformCapabilities()
        state = state.copy(dictationAvailable = false)
        composeRule.onNodeWithText("Dictation").assertDoesNotExist()
    }
}
