package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The book import through the real screen and ViewModel: read, choose chapters, create the decks. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BookImportScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val sources = FakeSourceRepository()
    private val decks = FakeDeckRepository()
    private val file = "content://books/origin.epub"

    private fun chapter(id: Int, title: String, words: Int, kind: ChapterKind = ChapterKind.Content) =
        BookChapter(id, title, "word ".repeat(words), kind)

    private val providers = FakeAiProviderRepository()
    private val handoff = BookHandoff()

    private fun bookImportViewModel(handle: SavedStateHandle) = BookImportViewModel(
        savedStateHandle = handle,
        sources = sources,
        deckRepository = decks,
        createBookDecks = CreateBookDecksUseCase(decks),
        generateCards = GenerateCardsUseCase(FakeCardGenerationRepository(), FakeCardRepository()),
        aiProviders = providers,
        bookHandoff = handoff,
    )

    private fun showScreen(closed: () -> Unit = {}) {
        val viewModel = bookImportViewModel(SavedStateHandle(mapOf("location" to file)))
        composeRule.setContent {
            MnemoTheme {
                val state = viewModel.uiState.collectAsState()
                BookImportScreen(state.value, viewModel::onAction, onClose = closed)
            }
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.let { !it.reading } }
    }

    @Test
    fun chooseChaptersAndCreateTheDecks() {
        sources.books[SourceInput.Epub(file)] = BookResult.Success(
            BookSource(
                title = "Origin of Species",
                author = "Charles Darwin",
                chapters = listOf(
                    chapter(0, "Title page", 20, ChapterKind.FrontMatter),
                    chapter(1, "Variation", 500),
                    chapter(2, "Struggle", 800),
                ),
            ),
        )
        var closed = false
        showScreen { closed = true }

        // The title, and the name of the book's deck, which starts as the same.
        composeRule.onAllNodesWithText("Origin of Species").assertCountEquals(2)
        composeRule.onNodeWithText("by Charles Darwin").assertExists()
        // Content is checked, the title page is listed but not.
        composeRule.onNodeWithText("2 of 3 chapters selected · 1300 words").assertExists()
        composeRule.onNodeWithText("Title page").assertIsOff()
        composeRule.onNodeWithText("Variation").assertIsOn()
        composeRule.onNodeWithText("20 words · Front matter").assertExists()
        composeRule.onNodeWithText("Create 2 decks").assertIsEnabled()

        composeRule.onNodeWithText("Struggle").performClick()
        composeRule.onNodeWithText("Struggle").assertIsOff()
        composeRule.onNodeWithText("1 of 3 chapters selected · 500 words").assertExists()
        composeRule.onNodeWithText("None").performClick()
        composeRule.onNodeWithText("Create 0 decks").assertIsNotEnabled()
        composeRule.onNodeWithText("Content only").performClick()
        composeRule.onNodeWithText("Create 2 decks").assertIsEnabled()

        // The book's deck is named by the user.
        val nameField = hasSetTextAction()
        composeRule.onNode(nameField).performTextClearance()
        composeRule.onNodeWithText("Create 2 decks").assertIsNotEnabled()
        composeRule.onNode(nameField).performTextInput("Darwin")
        composeRule.onNodeWithText("Create 2 decks").performClick()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTextCount("Decks are ready") > 0 }
        composeRule.onNodeWithText("2 new decks in Darwin. The decks are empty.", substring = true).assertExists()
        val all = runBlocking { decks.getDecks() }
        assertEquals(listOf("02 Variation", "03 Struggle"), all.filter { it.parentId != null }.map { it.name }.sorted())
        assertEquals("Darwin", all.single { it.parentId == null }.name)

        composeRule.onNodeWithText("Done").performClick()
        assertTrue(closed)
    }

    @Test
    fun aBookRunShowsWhatItSendsAndCreatesTheDecksOnlyWhenConfirmed() {
        providers.addProvider(
            AiProvider(
                id = "p", name = "Ollama", baseUrl = "http://192.168.1.20:11434/v1", defaultModel = "llama3.2", isLocal = true,
                createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
            ),
        )
        sources.books[SourceInput.Epub(file)] = BookResult.Success(
            BookSource(title = "Origin", chapters = listOf(chapter(0, "Variation", 500), chapter(1, "Struggle", 800))),
        )
        var left = false
        val viewModel = bookImportViewModel(SavedStateHandle(mapOf("location" to file)))
        composeRule.setContent {
            MnemoTheme {
                androidx.compose.runtime.LaunchedEffect(viewModel) { viewModel.runStarted.collect { left = true } }
                BookImportScreen(viewModel.uiState.collectAsState().value, viewModel::onAction, onClose = {})
            }
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.let { !it.reading && it.route != null } }

        composeRule.onNodeWithText("Create decks and generate").performClick()
        composeRule.onNodeWithText("Generate cards for these chapters?").assertExists()
        composeRule.onNodeWithText("2 chapters · 1300 words").assertExists()
        composeRule.onNodeWithText("Sends 2 requests to Ollama (llama3.2).").assertExists()
        composeRule.onNodeWithText("no charge per request", substring = true).assertExists()
        // Showing the numbers made nothing.
        assertTrue(runBlocking { decks.getDecks() }.isEmpty())

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Generate cards for these chapters?").assertDoesNotExist()
        composeRule.onNodeWithText("Create decks and generate").performClick()
        composeRule.onNodeWithText("Create and start").performClick()

        composeRule.waitUntil(5_000) { left }
        assertEquals(2, runBlocking { decks.getDecks() }.count { it.parentId != null })
        assertEquals(listOf(0, 1), handoff.offer.value?.batch)
    }

    @Test
    fun aBookRunWithoutAProviderOffersTheSetupInstead() {
        sources.books[SourceInput.Epub(file)] = BookResult.Success(BookSource(title = "Origin", chapters = listOf(chapter(0, "Variation", 500))))
        var setUp = false
        val viewModel = bookImportViewModel(SavedStateHandle(mapOf("location" to file)))
        composeRule.setContent {
            MnemoTheme { BookImportScreen(viewModel.uiState.collectAsState().value, viewModel::onAction, onClose = {}, onSetUpAi = { setUp = true }) }
        }
        composeRule.waitUntil(5_000) { !viewModel.uiState.value.reading }

        composeRule.onNodeWithText("Create decks and generate").performClick()
        composeRule.onNodeWithText("Generating cards needs an AI provider.", substring = true).assertExists()
        composeRule.onNodeWithText("Create and start").assertDoesNotExist()
        composeRule.onNodeWithText("Set up a provider").performClick()
        assertTrue(setUp)
        assertTrue(runBlocking { decks.getDecks() }.isEmpty())
    }

    @Test
    fun aBookWithDrmExplainsItself() {
        sources.books[SourceInput.Epub(file)] = BookResult.Failure(SourceProblem.Drm)
        showScreen()
        composeRule.onNodeWithText("This book is protected by DRM. Mnemo can only read books without DRM.").assertExists()
        composeRule.onNodeWithText("Choose an EPUB").assertExists()
    }

    @Test
    fun withoutAFileTheScreenAsksForOne() {
        val viewModel = bookImportViewModel(SavedStateHandle())
        composeRule.setContent { MnemoTheme { BookImportScreen(viewModel.uiState.collectAsState().value, viewModel::onAction, onClose = {}) } }
        composeRule.onNodeWithText("Choose a book").assertExists()
        composeRule.onNodeWithText("Choose an EPUB").assertExists()
    }

    @Test
    fun theCreateTabOffersTheImportWithoutAProvider() {
        var opened = false
        composeRule.setContent {
            MnemoTheme {
                SmartExtractScreen(SmartExtractUiState(isLoading = false), {}, onSetUpAi = {}, onImportBook = { opened = true })
            }
        }
        composeRule.onNodeWithText("Import a book").assertExists()
        composeRule.onNodeWithText("Choose an EPUB").performClick()
        assertTrue(opened)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
