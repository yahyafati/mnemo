package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.ChapterDeckRequest
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Smart Extract's EPUB source (docs/epub/ROADMAP.md, B5): a book's chapter becomes the text, its deck the destination. */
class SmartExtractBookTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val decks = FakeDeckRepository()
    private val sources = FakeSourceRepository()
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val handoff = BookHandoff()
    private val otherDeckId = runBlocking { decks.saveDeck("Neuroscience") }

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private val file = "/books/origin.epub"

    private val book = BookSource(
        title = "Origin",
        chapters = listOf(
            BookChapter(0, "Contents", "Chapter list", ChapterKind.FrontMatter),
            BookChapter(1, "Variation", "Animals vary under domestication."),
            BookChapter(2, "Struggle", "More are born than can survive.", truncated = true),
        ),
    )

    private fun provide(result: BookResult = BookResult.Success(book)) {
        sources.books[SourceInput.Epub(file)] = result
    }

    private fun viewModel() = SmartExtractViewModel(
        aiProviders = providers,
        deckRepository = decks,
        sources = sources,
        generateCards = GenerateCardsUseCase(generation, cards),
        regenerateCard = RegenerateCardUseCase(generation, cards),
        acceptCards = AcceptGeneratedCardsUseCase(cards),
        bookHandoff = handoff,
        userSettings = FakeUserSettingsRepository(),
        readPdfPages = ReadPdfPagesUseCase(sources, FakePdfReadRepository()),
    ).also { providers.addProvider(provider) }

    /** The deck ids the book import would have made, for [chapters] of a book called [name]. */
    private suspend fun makeDecks(name: String, vararg chapters: Pair<Int, String>) =
        CreateBookDecksUseCase(decks)(name, chapters.map { ChapterDeckRequest(it.first, it.second) }, book.chapters.size)

    private suspend fun SmartExtractViewModel.pick(): SmartExtractUiState {
        onAction(SmartExtractAction.EpubPicked(file))
        return uiState.first { !it.reading && (it.book != null || it.sourceProblem != null) }
    }

    @Test
    fun aPickedBookOpensItsChapterListAndAChapterFillsTheBox() = runTest {
        provide()
        val vm = viewModel()
        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Epub))
        val picked = vm.pick()

        assertEquals(SourceKind.Epub, picked.sourceKind)
        assertEquals(listOf("Contents", "Variation", "Struggle"), picked.book?.chapters?.map { it.title })
        assertEquals(ChapterKind.FrontMatter, picked.book?.chapters?.first()?.kind)
        assertTrue(picked.showChapters)
        assertEquals("", picked.text) // nothing is chosen until the user chooses

        vm.onAction(SmartExtractAction.SelectChapter(2))
        val chosen = vm.uiState.value
        assertEquals("More are born than can survive.", chosen.text)
        assertEquals("Origin — Struggle", chosen.title)
        assertEquals(2, chosen.chapterId)
        assertTrue(chosen.truncated)
        assertTrue(!chosen.showChapters)
        assertEquals(6, chosen.wordCount)
    }

    @Test
    fun theTextCanStillBeEditedAndTheListReopened() = runTest {
        provide()
        val vm = viewModel()
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(1))
        vm.onAction(SmartExtractAction.TextChanged("Edited"))
        assertEquals("Edited", vm.uiState.value.text)
        assertEquals(1, vm.uiState.value.chapterId)

        vm.onAction(SmartExtractAction.ShowChapters)
        assertTrue(vm.uiState.value.showChapters)
        vm.onAction(SmartExtractAction.DismissChapters)
        assertTrue(!vm.uiState.value.showChapters)
    }

    @Test
    fun theChaptersDeckIsSelectedWhenTheImportMadeIt() = runTest {
        provide()
        val made = makeDecks("Origin", 1 to "Variation", 2 to "Struggle")
        val vm = viewModel()
        vm.pick()
        assertEquals(otherDeckId, vm.uiState.value.deckId)

        vm.onAction(SmartExtractAction.SelectChapter(2))
        assertEquals(made.chapterDeckIds.getValue(2), vm.uiState.value.deckId)
        vm.onAction(SmartExtractAction.SelectChapter(1))
        assertEquals(made.chapterDeckIds.getValue(1), vm.uiState.value.deckId)
    }

    @Test
    fun aChapterWithoutADeckLeavesTheChoiceAlone() = runTest {
        provide()
        makeDecks("Origin", 1 to "Variation")
        val vm = viewModel()
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(2))
        assertEquals(otherDeckId, vm.uiState.value.deckId)
    }

    @Test
    fun aDeckOfAnotherBookWithTheSameChapterNameIsNotTheChapters() = runTest {
        provide()
        makeDecks("Another book", 2 to "Struggle")
        val vm = viewModel()
        vm.pick()
        vm.onAction(SmartExtractAction.SelectDeck(otherDeckId))
        vm.onAction(SmartExtractAction.SelectChapter(2))
        assertEquals(otherDeckId, vm.uiState.value.deckId)
    }

    @Test
    fun aDeckTheUserChoseIsKeptWhenTheyChooseAnotherChapterWithoutOne() = runTest {
        provide()
        val made = makeDecks("Origin", 1 to "Variation")
        val vm = viewModel()
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(1))
        assertEquals(made.chapterDeckIds.getValue(1), vm.uiState.value.deckId)

        vm.onAction(SmartExtractAction.SelectDeck(otherDeckId))
        vm.onAction(SmartExtractAction.SelectChapter(2)) // no deck for it
        assertEquals(otherDeckId, vm.uiState.value.deckId)
    }

    @Test
    fun aBookThatCantBeReadSaysWhy() = runTest {
        provide(BookResult.Failure(SourceProblem.Drm))
        val vm = viewModel()
        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Epub))
        val state = vm.pick()
        assertEquals(SourceProblem.Drm, state.sourceProblem)
        assertNull(state.book)
        assertEquals("", state.text)
        assertTrue(!state.reading)
    }

    @Test
    fun aBookFromTheImportIsTakenAtOnceUnderTheNameTheUserGaveIt() = runTest {
        val made = makeDecks("My Origin", 1 to "Variation", 2 to "Struggle")
        val vm = viewModel()
        handoff.offer(book, "My Origin", chapterId = 2)

        val state = vm.uiState.first { it.chapterId != null }
        assertEquals(SourceKind.Epub, state.sourceKind)
        assertEquals("More are born than can survive.", state.text)
        assertEquals(made.chapterDeckIds.getValue(2), state.deckId)
        assertTrue(!state.showChapters)
        assertNull(handoff.offer.value) // taken: it isn't offered again to a screen that opens later
    }

    @Test
    fun aBookFromTheImportWithoutAChapterOpensTheList() = runTest {
        // The offer is there before Smart Extract is, as when its tab was never opened.
        handoff.offer(book, "Origin")
        val vm = viewModel()
        val state = vm.uiState.first { it.book != null }
        assertTrue(state.showChapters)
        assertEquals(SourceKind.Epub, state.sourceKind)
        assertEquals(3, state.book?.chapters?.size)
        assertNull(handoff.offer.value)
    }

    @Test
    fun generatingSendsTheChaptersTextAndSavesNothingBeforeAccept() = runTest {
        provide()
        val made = makeDecks("Origin", 1 to "Variation")
        val vm = viewModel()
        generation.answer(card("What varies under domestication?", "Animals."))
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(1))
        vm.onAction(SmartExtractAction.Generate)

        val request = generation.requests.single()
        assertEquals("Animals vary under domestication.", request.text)
        assertEquals("Origin — Variation", request.title)
        assertEquals(1, vm.uiState.value.queue.size)
        assertTrue(cards.notes.value.isEmpty(), "nothing is saved before Accept")

        vm.onAction(SmartExtractAction.AcceptAll)
        assertEquals(1, cards.notes.value.size)
        assertEquals(made.chapterDeckIds.getValue(1), cards.notes.value.values.single().deckId)
    }

    @Test
    fun aLongChapterSaysHowManyRequestsItTakes() = runTest {
        provide(BookResult.Success(BookSource(title = "Long", chapters = listOf(BookChapter(0, "One", "Part one\n---\nPart two\n---\nPart three")))))
        val vm = viewModel()
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(0))
        assertEquals(3, vm.uiState.value.requests)

        vm.onAction(SmartExtractAction.TextChanged("Short"))
        assertEquals(1, vm.uiState.value.requests)
        vm.onAction(SmartExtractAction.ClearText)
        assertEquals(0, vm.uiState.value.requests)
    }

    @Test
    fun theProviderNoticeStillComesFirst() = runTest {
        provide()
        val vm = viewModel()
        providers.addProvider(provider.copy(disclosureAcceptedAt = null))
        vm.pick()
        vm.onAction(SmartExtractAction.SelectChapter(1))
        vm.onAction(SmartExtractAction.Generate)
        assertNotNull(vm.uiState.value.disclosure)
        assertTrue(generation.requests.isEmpty())
    }
}
