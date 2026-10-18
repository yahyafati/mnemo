package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.SummaryResult
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.SummarizeSourceUseCase
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.ContextSummary
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceContextRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The context summary of a long source (ADR 0015, C4): when it is made, what it is cached by, how it is edited and what a failure offers. */
class SmartExtractContextTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val sources = FakeSourceRepository()
    private val generation = FakeCardGenerationRepository()
    private val contexts = FakeSourceContextRepository()
    private val cards = FakeCardRepository()

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    /** Three parts (the fake splits on `---` lines) are enough for Auto to write a summary. */
    private val longText = "One\n---\nTwo\n---\nThree"

    private fun readyViewModel(text: String = longText) = SmartExtractViewModel(
        aiProviders = providers,
        deckRepository = FakeDeckRepository(),
        sources = sources,
        generateCards = GenerateCardsUseCase(generation, cards, SummarizeSourceUseCase(contexts)),
        regenerateCard = RegenerateCardUseCase(generation, cards),
        acceptCards = AcceptGeneratedCardsUseCase(cards, FakeMediaRepository()),
        bookHandoff = BookHandoff(),
        userSettings = FakeUserSettingsRepository(),
        readPdfPages = ReadPdfPagesUseCase(sources, FakePdfReadRepository()),
    ).also {
        providers.addProvider(provider)
        it.onAction(SmartExtractAction.TextChanged(text))
    }

    private val SmartExtractViewModel.state get() = uiState.value

    @Test
    fun aLongSourceIsSummarisedOnceBeforeItsPartsAndEveryPartCarriesTheSummary() = runTest {
        val vm = readyViewModel()
        contexts.respond = { SummaryResult.Success("About loss aversion.") }
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(longText, contexts.requests.single().text)
        assertEquals("llama-3.3-70b", contexts.routes.single().modelId)
        assertEquals(listOf("About loss aversion.", "About loss aversion.", "About loss aversion."), generation.requests.map { it.context })
        assertEquals(SourceContext("About loss aversion."), vm.state.context)
        assertEquals(GenerationState.Done(added = 0), vm.state.generation)
    }

    @Test
    fun theRequestCountIncludesTheSummary() = runTest {
        val vm = readyViewModel()
        assertEquals(4, vm.state.requests)

        vm.onAction(SmartExtractAction.SetContextSummary(ContextSummary.Off))
        assertEquals(3, vm.state.requests)

        vm.onAction(SmartExtractAction.SetContextSummary(ContextSummary.Auto))
        vm.onAction(SmartExtractAction.TextChanged("One\n---\nTwo"))
        assertEquals(2, vm.state.requests)
    }

    @Test
    fun aShortSourceIsItsOwnContext() = runTest {
        val vm = readyViewModel("One\n---\nTwo")
        vm.onAction(SmartExtractAction.Generate)

        assertTrue(contexts.requests.isEmpty())
        assertNull(vm.state.context)
        assertEquals(listOf("", ""), generation.requests.map { it.context })
    }

    @Test
    fun offNeverSummarises() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.SetContextSummary(ContextSummary.Off))
        vm.onAction(SmartExtractAction.Generate)

        assertTrue(contexts.requests.isEmpty())
        assertNull(vm.state.context)
        assertEquals(3, generation.requests.size)
        assertTrue(generation.requests.all { it.context.isEmpty() })
    }

    @Test
    fun theCardsLanguageIsTheSummarysToo() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.SetLanguage("Spanish"))
        vm.onAction(SmartExtractAction.Generate)
        assertEquals("Spanish", contexts.requests.single().language)
    }

    @Test
    fun aSecondGenerateOnTheSameTextDoesNotAskAgain() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(1, contexts.requests.size)
        assertEquals(6, generation.requests.size)
        assertNotNull(vm.state.context)
    }

    @Test
    fun anotherLanguageOrAnotherTextIsAnotherSummary() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.SetLanguage("French"))
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.TextChanged("A\n---\nB\n---\nC"))
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(3, contexts.requests.size)
    }

    @Test
    fun resumingAfterAFailedPartDoesNotAskAgainAndKeepsTheSummary() = runTest {
        val vm = readyViewModel()
        contexts.respond = { SummaryResult.Success("The summary.") }
        generation.respond = { request ->
            if (request.part == 1) flowOf(GenerationUpdate.Done(AiFailure(AiProblem.Unreachable))) else flowOf(GenerationUpdate.Done())
        }
        vm.onAction(SmartExtractAction.Generate)
        assertIs<GenerationState.Failed>(vm.state.generation)

        generation.respond = { flowOf(GenerationUpdate.Done()) }
        vm.onAction(SmartExtractAction.Retry)

        assertEquals(1, contexts.requests.size)
        assertEquals(listOf(0, 1, 1, 2), generation.requests.map { it.part })
        assertTrue(generation.requests.all { it.context == "The summary." })
    }

    @Test
    fun anEditedSummaryIsWhatLaterPartsRegenerateAndTheNextGenerateSend() = runTest {
        val vm = readyViewModel()
        generation.respond = { request ->
            if (request.part == 1) flowOf(GenerationUpdate.Done(AiFailure(AiProblem.Unreachable))) else flowOf(GenerationUpdate.Card(card("What is part ${request.part}?", "A").copy(id = "c${request.part}", chunkIndex = request.part)), GenerationUpdate.Done())
        }
        vm.onAction(SmartExtractAction.Generate)
        assertIs<GenerationState.Failed>(vm.state.generation)

        vm.onAction(SmartExtractAction.EditContext)
        assertTrue(assertNotNull(vm.state.context).editing)
        vm.onAction(SmartExtractAction.ContextChanged("Edited by the user."))
        vm.onAction(SmartExtractAction.DoneEditingContext)
        assertEquals(SourceContext("Edited by the user.", expanded = true, editing = false), vm.state.context)

        // The parts not yet sent read the edit.
        generation.respond = { request -> flowOf(GenerationUpdate.Card(card("What is part ${request.part}?", "A").copy(id = "n${request.part}", chunkIndex = request.part)), GenerationUpdate.Done()) }
        vm.onAction(SmartExtractAction.Retry)
        assertEquals(listOf("Edited by the user.", "Edited by the user."), generation.requests.takeLast(2).map { it.context })

        // So does Regenerate.
        generation.answer(card("Why is part 0 important?", "Because."))
        vm.onAction(SmartExtractAction.Regenerate(vm.state.queue.first().card.id))
        assertEquals("Edited by the user.", generation.requests.last().context)

        // And a second Generate on the same text reuses the edit instead of asking again.
        vm.onAction(SmartExtractAction.Generate)
        assertEquals(1, contexts.requests.size)
        assertEquals("Edited by the user.", generation.requests.last().context)
    }

    @Test
    fun theSummaryCanBeShownAndHidden() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.Generate)
        assertFalse(assertNotNull(vm.state.context).expanded)

        vm.onAction(SmartExtractAction.ToggleContext)
        assertTrue(assertNotNull(vm.state.context).expanded)
        vm.onAction(SmartExtractAction.ToggleContext)
        assertFalse(assertNotNull(vm.state.context).expanded)
    }

    @Test
    fun theSummaryIsNotEditedWhileARunIsGoing() = runTest {
        val vm = readyViewModel()
        val updates = generation.streamed()
        vm.onAction(SmartExtractAction.Generate)
        assertIs<GenerationState.Running>(vm.state.generation)
        assertNotNull(vm.state.context)

        vm.onAction(SmartExtractAction.EditContext)
        assertFalse(assertNotNull(vm.state.context).editing)

        updates.trySend(GenerationUpdate.Done())
    }

    @Test
    fun aFailedSummaryStopsBeforeAnyPartAndRetryAsksAgain() = runTest {
        val vm = readyViewModel()
        contexts.respond = { SummaryResult.Failure(AiFailure(AiProblem.RateLimited)) }
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(AiProblem.RateLimited, assertIs<GenerationState.SummaryFailed>(vm.state.generation).failure.problem)
        assertTrue(generation.requests.isEmpty())
        assertNull(vm.state.context)

        contexts.respond = { SummaryResult.Success("Now it works.") }
        vm.onAction(SmartExtractAction.Retry)

        assertEquals(2, contexts.requests.size)
        assertEquals(3, generation.requests.size)
        assertEquals("Now it works.", vm.state.context?.text)
        assertEquals(GenerationState.Done(added = 0), vm.state.generation)
    }

    @Test
    fun generateWithoutContextGoesOnWithoutASummary() = runTest {
        val vm = readyViewModel()
        contexts.respond = { SummaryResult.Failure(AiFailure(AiProblem.Unreachable)) }
        vm.onAction(SmartExtractAction.Generate)
        assertIs<GenerationState.SummaryFailed>(vm.state.generation)

        vm.onAction(SmartExtractAction.GenerateWithoutContext)

        assertEquals(1, contexts.requests.size)
        assertEquals(3, generation.requests.size)
        assertTrue(generation.requests.all { it.context.isEmpty() })
        assertNull(vm.state.context)
        assertEquals(GenerationState.Done(added = 0), vm.state.generation)
    }

    @Test
    fun generateWithoutContextDoesNothingWhenNothingFailed() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.GenerateWithoutContext)
        assertEquals(GenerationState.Idle, vm.state.generation)
        assertTrue(generation.requests.isEmpty())
    }

    @Test
    fun stoppingWhileTheSummaryIsWrittenStopsTheRun() = runTest {
        val vm = readyViewModel()
        contexts.respond = { awaitCancellation() }
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(GenerationState.Summarizing, vm.state.generation)
        assertTrue(vm.state.isGenerating)
        assertFalse(vm.state.canGenerate)

        vm.onAction(SmartExtractAction.Stop)
        assertEquals(GenerationState.Idle, vm.state.generation)
        assertTrue(generation.requests.isEmpty())
    }

    @Test
    fun emptyingTheBoxForgetsTheSummary() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.Generate)
        assertNotNull(vm.state.context)

        vm.onAction(SmartExtractAction.ClearText)
        assertNull(vm.state.context)
    }

    @Test
    fun aNewRunReplacesTheSummaryOfTheLastOne() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.Generate)
        assertNotNull(vm.state.context)

        vm.onAction(SmartExtractAction.TextChanged("One\n---\nTwo"))
        vm.onAction(SmartExtractAction.Generate)
        assertNull(vm.state.context)
    }
}
