package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookImportViewModelTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sources = FakeSourceRepository()
    private val decks = FakeDeckRepository()
    private val handoff = BookHandoff()
    private val providers = FakeAiProviderRepository()
    private val generation = FakeCardGenerationRepository()

    private val file = "/books/origin.epub"

    private fun chapter(id: Int, title: String, words: Int = 500, kind: ChapterKind = ChapterKind.Content) =
        BookChapter(id, title, "word ".repeat(words), kind)

    private val book = BookSource(
        title = "Origin",
        author = "Darwin",
        chapters = listOf(
            chapter(0, "Contents", 20, ChapterKind.FrontMatter),
            chapter(1, "Variation"),
            chapter(2, "Struggle", 800),
            chapter(3, "Index", 100, ChapterKind.BackMatter),
        ),
    )

    private fun provide(book: BookSource = this.book) {
        sources.books[SourceInput.Epub(file)] = BookResult.Success(book)
    }

    private fun viewModel(location: String? = null) = BookImportViewModel(
        savedStateHandle = SavedStateHandle(if (location == null) emptyMap() else mapOf("location" to location)),
        sources = sources,
        deckRepository = decks,
        createBookDecks = CreateBookDecksUseCase(decks),
        generateCards = GenerateCardsUseCase(generation, FakeCardRepository()),
        aiProviders = providers,
        bookHandoff = handoff,
    )

    /** Picks [file] and waits for it to be read (the localized fallbacks are read off the main thread). */
    private suspend fun BookImportViewModel.open(): BookImportUiState {
        onAction(BookImportAction.FilePicked(file))
        return uiState.first { !it.reading }
    }

    private suspend fun BookImportViewModel.created(): BookImportUiState {
        onAction(BookImportAction.Create)
        return uiState.first { it.created != null || it.createFailed }
    }

    @Test
    fun asksForAFileUntilOneIsPicked() = runTest {
        val state = viewModel().uiState.value
        assertEquals(BookImportUiState(), state)
        assertNull(state.book)
    }

    @Test
    fun readsTheBookAndChecksItsContent() = runTest {
        provide()
        val state = viewModel().open()

        assertEquals("Origin", state.bookName)
        assertEquals(setOf(1, 2), state.checked)
        assertEquals(mapOf(0 to 20, 1 to 500, 2 to 800, 3 to 100), state.wordCounts)
        assertEquals(1300, state.selectedWords)
        assertTrue(state.canCreate)
        assertTrue(state.existing.isEmpty())
    }

    @Test
    fun aFileGivenInTheRouteIsReadAtOnce() = runTest {
        provide()
        // No pick: the route's file is read on arrival.
        assertNotNull(viewModel(location = file).uiState.first { !it.reading }.book)
    }

    @Test
    fun aBookThatCantBeReadShowsWhy() = runTest {
        sources.books[SourceInput.Epub(file)] = BookResult.Failure(SourceProblem.Drm)
        val vm = viewModel()
        val state = vm.open()
        assertEquals(SourceProblem.Drm, state.problem)
        assertNull(state.book)

        // A file that isn't there any more is told apart, and picking another one starts over.
        sources.books.clear()
        assertEquals(SourceProblem.FileUnavailable, vm.open().problem)
        provide()
        assertNull(vm.open().problem)
    }

    @Test
    fun unnamedChaptersAndBooksGetNamesInTheUsersLanguage() = runTest {
        provide(BookSource(title = "", chapters = listOf(chapter(0, ""), chapter(1, "  "), chapter(2, "Real"))))
        val state = viewModel().open()
        assertEquals("Untitled book", state.bookName)
        assertEquals(listOf("Chapter 1", "Chapter 2", "Real"), state.chapters.map { it.title })
    }

    @Test
    fun theSelectionCanBeChanged() = runTest {
        provide()
        val vm = viewModel()
        vm.open()

        vm.onAction(BookImportAction.SelectAll)
        assertEquals(setOf(0, 1, 2, 3), vm.uiState.value.checked)
        vm.onAction(BookImportAction.SelectNone)
        assertTrue(vm.uiState.value.checked.isEmpty())
        assertEquals(false, vm.uiState.value.canCreate)
        vm.onAction(BookImportAction.ToggleChapter(3))
        vm.onAction(BookImportAction.ToggleChapter(1))
        assertEquals(setOf(3, 1), vm.uiState.value.checked)
        vm.onAction(BookImportAction.ToggleChapter(3))
        assertEquals(setOf(1), vm.uiState.value.checked)
        vm.onAction(BookImportAction.SelectContent)
        assertEquals(setOf(1, 2), vm.uiState.value.checked)
    }

    @Test
    fun aBlankNameCantBeCreated() = runTest {
        provide()
        val vm = viewModel()
        vm.open()
        vm.onAction(BookImportAction.BookNameChanged("  "))
        assertEquals(false, vm.uiState.value.canCreate)
    }

    @Test
    fun createsANumberedDeckForEachCheckedChapter() = runTest {
        provide()
        val vm = viewModel()
        vm.open()
        vm.onAction(BookImportAction.BookNameChanged("Darwin::Origin"))

        val state = vm.created()

        assertEquals(BookImportResult(bookDeck = "Darwin – Origin", newDecks = 2, reusedDecks = 0), state.created)
        val all = decks.getDecks()
        val root = all.single { it.parentId == null }
        assertEquals("Darwin – Origin", root.name)
        // Numbered by position in the whole book, padded to its size, so the list stays in order.
        assertEquals(listOf("02 Variation", "03 Struggle"), all.filter { it.parentId == root.id }.map { it.name }.sorted())
        assertTrue(all.none { it.description.isNotEmpty() })
        assertEquals(false, state.canCreate)
    }

    @Test
    fun importingTheSameBookAgainCreatesNothingNewAndSaysWhatExists() = runTest {
        provide()
        val first = viewModel()
        first.open()
        first.created()
        val before = decks.getDecks().map { it.id }.toSet()

        val vm = viewModel()
        val state = vm.open()
        assertEquals(setOf(1, 2), state.existing)

        // Every chapter picked now: only the two that have no deck are new.
        vm.onAction(BookImportAction.SelectAll)
        assertEquals(2, vm.uiState.value.newDecks)
        val created = vm.created().created
        assertEquals(BookImportResult(bookDeck = "Origin", newDecks = 2, reusedDecks = 2), created)
        assertEquals(4, decks.getDecks().count { it.parentId != null })
        assertTrue(before.all { id -> decks.getDecks().any { it.id == id } })
    }

    @Test
    fun existingDecksFollowTheBookName() = runTest {
        provide()
        val vm = viewModel()
        vm.open()
        vm.created()
        assertEquals(setOf(1, 2), vm.uiState.value.existing)

        vm.onAction(BookImportAction.BookNameChanged("Another"))
        assertTrue(vm.uiState.value.existing.isEmpty())
        // The match ignores case, like deck names do.
        vm.onAction(BookImportAction.BookNameChanged("origin"))
        assertEquals(setOf(1, 2), vm.uiState.value.existing)
    }

    @Test
    fun aDeckWithTheNameOfAChapterElsewhereIsNotTheChapters() = runTest {
        provide()
        // A top-level deck that is named like a chapter deck doesn't count: the chapters are looked up under the book.
        decks.addDeck(Deck("x", "02 Variation", null, createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH))
        assertTrue(viewModel().open().existing.isEmpty())
    }

    @Test
    fun startingOverForgetsTheBook() = runTest {
        provide()
        val vm = viewModel()
        vm.open()
        vm.onAction(BookImportAction.Reset)
        assertEquals(BookImportUiState(), vm.uiState.value)
    }

    @Test
    fun generatingCardsHandsTheBookAndItsDeckNameToSmartExtract() = runTest {
        provide()
        val vm = viewModel()
        vm.open()
        vm.onAction(BookImportAction.BookNameChanged("My Origin"))
        vm.created()
        assertNull(handoff.offer.value)

        vm.onAction(BookImportAction.GenerateCards)
        val offer = assertNotNull(handoff.offer.value)
        assertEquals("My Origin", offer.bookName)
        assertEquals(listOf("Contents", "Variation", "Struggle", "Index"), offer.book.chapters.map { it.title })
        assertNull(offer.chapterId)
    }
    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        createdAt = java.time.Instant.EPOCH, updatedAt = java.time.Instant.EPOCH,
    )

    /** A book whose Variation takes three requests (the fake generation splits on "---" lines). */
    private fun runBook() = BookSource(
        title = "Origin",
        chapters = listOf(
            BookChapter(0, "Contents", "Chapter list", ChapterKind.FrontMatter),
            BookChapter(1, "Variation", "Part one\n---\nPart two\n---\nPart three"),
            BookChapter(2, "Struggle", "A single part"),
            BookChapter(3, "Blank", "  "),
        ),
    )

    private suspend fun BookImportViewModel.openRunBook(withProvider: Boolean = true): BookImportUiState {
        provide(runBook())
        if (withProvider) providers.addProvider(provider)
        val state = open()
        return if (withProvider) uiState.first { it.route != null } else state
    }

    @Test
    fun aRunShowsWhatItWouldSendBeforeAnythingIsMadeOrSent() = runTest {
        val vm = viewModel()
        vm.openRunBook()
        vm.onAction(BookImportAction.ShowBatch)

        val plan = assertNotNull(vm.uiState.value.batchPlan)
        // Content only, in book order; the blank chapter has nothing to send.
        val counts = vm.uiState.value.wordCounts
        assertEquals(BatchPlan(chapterIds = listOf(1, 2), words = counts.getValue(1) + counts.getValue(2), requests = 4), plan)
        assertEquals("Groq", vm.uiState.value.route?.provider?.name)
        assertTrue(decks.getDecks().isEmpty())
        assertNull(handoff.offer.value)
        assertTrue(generation.requests.isEmpty())

        vm.onAction(BookImportAction.DismissBatch)
        assertNull(vm.uiState.value.batchPlan)
    }

    @Test
    fun theRunIncludesFrontMatterOnlyWhenTheUserCheckedIt() = runTest {
        val vm = viewModel()
        vm.openRunBook()
        vm.onAction(BookImportAction.ToggleChapter(0))
        vm.onAction(BookImportAction.ShowBatch)
        assertEquals(listOf(0, 1, 2), vm.uiState.value.batchPlan?.chapterIds)
    }

    @Test
    fun confirmingMakesTheDecksAndHandsTheRunToSmartExtract() = runTest {
        val vm = viewModel()
        vm.openRunBook()
        vm.onAction(BookImportAction.BookNameChanged("My Origin"))
        vm.onAction(BookImportAction.ShowBatch)
        vm.onAction(BookImportAction.ConfirmBatch)

        vm.runStarted.first()
        val offer = assertNotNull(handoff.offer.value)
        assertEquals("My Origin", offer.bookName)
        assertEquals(listOf(1, 2), offer.batch)
        // Every checked chapter got its deck, the blank one too, and those are the ids handed over.
        val all = decks.getDecks()
        assertEquals(setOf(1, 2, 3), offer.deckIds.keys)
        assertEquals(offer.deckIds.values.toSet(), all.filter { it.parentId != null }.map { it.id }.toSet())
        assertNull(vm.uiState.value.batchPlan)
        assertEquals(BookImportResult(bookDeck = "My Origin", newDecks = 3, reusedDecks = 0), vm.uiState.value.created)
        assertTrue(generation.requests.isEmpty(), "this screen sends nothing")
    }

    @Test
    fun withoutAProviderTheRunCantStartButTheDecksCanStillBeMade() = runTest {
        val vm = viewModel()
        vm.openRunBook(withProvider = false)
        assertNull(vm.uiState.value.route)
        vm.onAction(BookImportAction.ShowBatch)
        assertNotNull(vm.uiState.value.batchPlan) // the dialog shows the setup prompt

        vm.onAction(BookImportAction.ConfirmBatch)
        assertTrue(decks.getDecks().isEmpty())
        assertNull(handoff.offer.value)

        vm.onAction(BookImportAction.DismissBatch)
        assertEquals(true, vm.uiState.value.canCreate)
    }

    @Test
    fun aSelectionWithNoTextHasNothingToRun() = runTest {
        val vm = viewModel()
        vm.openRunBook()
        vm.onAction(BookImportAction.SelectNone)
        vm.onAction(BookImportAction.ToggleChapter(3))
        assertEquals(true, vm.uiState.value.canCreate)
        assertEquals(false, vm.uiState.value.canGenerate)
        vm.onAction(BookImportAction.ShowBatch)
        assertNull(vm.uiState.value.batchPlan)
    }

    @Test
    fun theRouteSurvivesStartingOver() = runTest {
        val vm = viewModel()
        vm.openRunBook()
        vm.onAction(BookImportAction.Reset)
        assertNotNull(vm.uiState.value.route)
    }
}
