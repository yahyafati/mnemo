package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.ChapterDeckRequest
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A book run (docs/epub/ROADMAP.md, B6): the chapters in order, one review queue at a time, and never on to the
 * next chapter without the user.
 */
class SmartExtractBatchTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val decks = FakeDeckRepository()
    private val sources = FakeSourceRepository()
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val handoff = BookHandoff()

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private val book = BookSource(
        title = "Origin",
        chapters = listOf(
            BookChapter(0, "Variation", "Animals vary under domestication."),
            BookChapter(1, "Struggle", "More are born than can survive."),
            BookChapter(2, "Selection", "Natural selection acts on variation."),
        ),
    )

    /** The provider is there before the ViewModel, as it is once the app has been used. */
    private fun viewModel(withProvider: AiProvider = provider): SmartExtractViewModel {
        providers.addProvider(withProvider)
        return SmartExtractViewModel(
            aiProviders = providers,
            deckRepository = decks,
            sources = sources,
            generateCards = GenerateCardsUseCase(generation, cards),
            regenerateCard = RegenerateCardUseCase(generation, cards),
            acceptCards = AcceptGeneratedCardsUseCase(cards, FakeMediaRepository()),
            bookHandoff = handoff,
            userSettings = FakeUserSettingsRepository(),
            readPdfPages = ReadPdfPagesUseCase(sources, FakePdfReadRepository()),
        )
    }

    private suspend fun makeDecks() = CreateBookDecksUseCase(decks)("Origin", book.chapters.map { ChapterDeckRequest(it.id, it.title) }, book.chapters.size)

    /** Hands the run over as the book import does, for [ids], and waits for Smart Extract to take it. */
    private suspend fun SmartExtractViewModel.startRun(ids: List<Int> = listOf(0, 1, 2), deckIds: Map<Int, String>? = null): SmartExtractUiState {
        handoff.offer(book, "Origin", batch = ids, deckIds = deckIds ?: makeDecks().chapterDeckIds)
        return uiState.first { it.batch != null || it.batchConfirmation != null }
    }

    private fun chapterTexts() = generation.requests.map { it.text }

    @Test
    fun theRunStartsAtTheFirstChapterAtOnceInItsDeck() = runTest {
        val made = makeDecks()
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()

        val state = vm.startRun(deckIds = made.chapterDeckIds)

        assertEquals(0, state.batch?.position)
        assertEquals(3, state.batch?.total)
        assertEquals(listOf("Variation", "Struggle", "Selection"), state.batch?.chapters?.map { it.title })
        assertEquals(listOf("Animals vary under domestication."), chapterTexts())
        assertEquals("Origin — Variation", generation.requests.single().title)
        assertEquals(made.chapterDeckIds.getValue(0), state.deckId)
        assertEquals(SourceKind.Epub, state.sourceKind)
        assertTrue(!state.showChapters, "the run chooses the chapters, so the list stays closed")
        assertEquals(1, vm.uiState.value.queue.size)
        assertTrue(cards.notes.value.isEmpty(), "nothing is saved before Accept")
    }

    @Test
    fun aFinishedChapterDoesNotGoOnByItself() = runTest {
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun()

        val state = vm.uiState.value
        assertTrue(state.generation is GenerationState.Done)
        assertEquals(0, state.batch?.position)
        assertEquals(1, generation.requests.size, "the next chapter waits for the user")
    }

    @Test
    fun nextAsksBeforeDiscardingCardsAndThenGoesOn() = runTest {
        val made = makeDecks()
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun(deckIds = made.chapterDeckIds)

        vm.onAction(SmartExtractAction.BatchNext)
        assertEquals(BatchConfirmation.Advance, vm.uiState.value.batchConfirmation)
        assertEquals(1, vm.uiState.value.queue.size, "asking changes nothing")
        assertEquals(1, generation.requests.size)

        vm.onAction(SmartExtractAction.CancelBatchDiscard)
        assertNull(vm.uiState.value.batchConfirmation)
        assertEquals(0, vm.uiState.value.batch?.position)

        vm.onAction(SmartExtractAction.BatchNext)
        vm.onAction(SmartExtractAction.ConfirmBatchDiscard)
        val state = vm.uiState.value
        assertEquals(1, state.batch?.position)
        assertNull(state.batchConfirmation)
        assertEquals(listOf("Animals vary under domestication.", "More are born than can survive."), chapterTexts())
        assertEquals(made.chapterDeckIds.getValue(1), state.deckId)
        assertEquals(1, state.queue.size, "only the new chapter's cards are in the queue")
        assertTrue(cards.notes.value.isEmpty())
    }

    @Test
    fun acceptingEverythingLetsTheRunGoOnWithoutAsking() = runTest {
        val made = makeDecks()
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun(deckIds = made.chapterDeckIds)

        vm.onAction(SmartExtractAction.AcceptAll)
        assertEquals(made.chapterDeckIds.getValue(0), cards.notes.value.values.single().deckId)
        vm.onAction(SmartExtractAction.BatchNext)

        assertNull(vm.uiState.value.batchConfirmation)
        assertEquals(1, vm.uiState.value.batch?.position)
        assertEquals(2, generation.requests.size)
    }

    @Test
    fun theLastChapterEndsTheRun() = runTest {
        val made = makeDecks()
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun(ids = listOf(1, 2), deckIds = made.chapterDeckIds)
        assertEquals(1, vm.uiState.value.batch?.chapters?.first()?.id)

        vm.onAction(SmartExtractAction.DiscardAll)
        vm.onAction(SmartExtractAction.BatchNext)
        assertEquals(2, vm.uiState.value.batch?.chapters?.get(vm.uiState.value.batch!!.position)?.id)
        assertTrue(vm.uiState.value.batch!!.isLast)

        vm.onAction(SmartExtractAction.DiscardAll)
        vm.onAction(SmartExtractAction.BatchNext)
        val state = vm.uiState.value
        assertNull(state.batch)
        assertEquals(ExtractMessage.BatchFinished(2), state.message)
        assertEquals(2, generation.requests.size, "finishing sends nothing more")
    }

    @Test
    fun aFailureKeepsTheRunWhereItIsAndRetryResumes() = runTest {
        generation.respond = { flowOf(GenerationUpdate.Done(AiFailure(AiProblem.RateLimited))) }
        val vm = viewModel()
        vm.startRun()

        val failed = vm.uiState.value
        assertEquals(AiFailure(AiProblem.RateLimited), (failed.generation as GenerationState.Failed).failure)
        assertEquals(0, failed.batch?.position)
        assertEquals(1, generation.requests.size, "a rate limit doesn't skip ahead")

        generation.answer(card("What varies?", "Animals."))
        vm.onAction(SmartExtractAction.Retry)
        assertEquals(listOf("Animals vary under domestication.", "Animals vary under domestication."), chapterTexts())
        assertEquals(0, vm.uiState.value.batch?.position)
        assertEquals(1, vm.uiState.value.queue.size)
    }

    @Test
    fun aFailedChapterCanBeSkippedByTheUser() = runTest {
        generation.respond = { flowOf(GenerationUpdate.Done(AiFailure(AiProblem.Unauthorized))) }
        val vm = viewModel()
        vm.startRun()

        vm.onAction(SmartExtractAction.BatchNext) // the queue is empty, so no question
        assertEquals(1, vm.uiState.value.batch?.position)
        assertEquals(listOf("Animals vary under domestication.", "More are born than can survive."), chapterTexts())
    }

    @Test
    fun stoppingEndsTheRunAndKeepsWhatWasGenerated() = runTest {
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun()

        vm.onAction(SmartExtractAction.BatchStop)
        val state = vm.uiState.value
        assertNull(state.batch)
        assertEquals(1, state.queue.size)
        assertEquals("Animals vary under domestication.", state.text)
        assertEquals(1, generation.requests.size)
    }

    @Test
    fun startingARunOverCardsWaitingForReviewAsksFirst() = runTest {
        val made = makeDecks()
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        sources.books[SourceInput.Epub("/books/origin.epub")] = BookResult.Success(book)
        vm.onAction(SmartExtractAction.EpubPicked("/books/origin.epub"))
        vm.onAction(SmartExtractAction.SelectChapter(2))
        vm.onAction(SmartExtractAction.Generate)
        assertEquals(1, vm.uiState.value.queue.size)

        vm.startRun(deckIds = made.chapterDeckIds)
        assertEquals(BatchConfirmation.Start, vm.uiState.value.batchConfirmation)
        assertNull(vm.uiState.value.batch)
        assertEquals(1, generation.requests.size, "nothing new is sent before the answer")

        vm.onAction(SmartExtractAction.CancelBatchDiscard)
        assertNull(vm.uiState.value.batchConfirmation)
        assertNull(vm.uiState.value.batch)
        assertEquals(1, vm.uiState.value.queue.size)

        handoff.offer(book, "Origin", batch = listOf(0, 1), deckIds = made.chapterDeckIds)
        vm.uiState.first { it.batchConfirmation != null }
        vm.onAction(SmartExtractAction.ConfirmBatchDiscard)
        val state = vm.uiState.value
        assertEquals(0, state.batch?.position)
        assertEquals(2, generation.requests.size)
        assertEquals("Animals vary under domestication.", generation.requests.last().text)
    }

    @Test
    fun theProviderNoticeStillComesBeforeTheFirstRequest() = runTest {
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel(provider.copy(disclosureAcceptedAt = null))
        val state = vm.startRun()

        assertNotNull(state.disclosure)
        assertTrue(generation.requests.isEmpty())
        assertEquals(0, state.batch?.position)

        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertEquals(1, generation.requests.size)
        assertEquals(1, vm.uiState.value.queue.size)
    }

    @Test
    fun chaptersWithoutTextAreLeftOutOfTheRun() = runTest {
        val vm = viewModel()
        handoff.offer(
            BookSource(title = "Origin", chapters = listOf(BookChapter(0, "Blank", "  "), BookChapter(1, "Text", "Some words."))),
            "Origin",
            batch = listOf(0, 1),
        )
        val state = vm.uiState.first { it.batch != null }
        assertEquals(listOf("Text"), state.batch?.chapters?.map { it.title })
    }

    @Test
    fun choosingAChapterByHandEndsTheRun() = runTest {
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        vm.startRun()

        vm.onAction(SmartExtractAction.SelectChapter(2))
        assertNull(vm.uiState.value.batch)
        assertEquals(2, vm.uiState.value.chapterId)
        vm.onAction(SmartExtractAction.BatchNext)
        assertEquals(1, generation.requests.size)
    }

    @Test
    fun theDeckTheImportMadeIsKeptBeforeTheDeckListHasIt() = runTest {
        generation.answer(card("What varies?", "Animals."))
        val vm = viewModel()
        val state = vm.startRun(deckIds = mapOf(0 to "just-made", 1 to "also-made", 2 to "third"))
        assertEquals("just-made", state.deckId)

        // The list changes without it, as when it hasn't caught up with the decks the import just made.
        decks.saveDeck("Something else")
        assertEquals("just-made", vm.uiState.value.deckId)
    }
}
