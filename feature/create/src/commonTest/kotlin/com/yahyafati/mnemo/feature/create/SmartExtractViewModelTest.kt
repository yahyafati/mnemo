package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.GeneratedCardProblem
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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

class SmartExtractViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val decks = FakeDeckRepository()
    private val sources = FakeSourceRepository()
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val handoff = BookHandoff()
    private val deckId = runBlocking { decks.saveDeck("Neuroscience") }

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun viewModel() = SmartExtractViewModel(
        aiProviders = providers,
        deckRepository = decks,
        sources = sources,
        generateCards = GenerateCardsUseCase(generation, cards),
        regenerateCard = RegenerateCardUseCase(generation, cards),
        acceptCards = AcceptGeneratedCardsUseCase(cards),
        bookHandoff = handoff,
    )

    private fun readyViewModel(text: String = "The amygdala processes fear.") = viewModel().also {
        providers.addProvider(provider)
        it.onAction(SmartExtractAction.TextChanged(text))
    }

    private val SmartExtractViewModel.state get() = uiState.value

    @Test
    fun setupPromptUntilAProviderIsReady() = runTest {
        val vm = viewModel()
        assertNull(vm.state.route)
        assertEquals(deckId, vm.state.deckId)

        // Without a model a provider isn't ready yet.
        providers.addProvider(provider.copy(defaultModel = null))
        assertNull(vm.state.route)

        providers.addProvider(provider)
        assertEquals(AiTask.Extract, assertNotNull(vm.state.route).task)
        assertEquals("llama-3.3-70b", vm.state.route?.modelId)
    }

    @Test
    fun theQueueFillsWhileCardsStreamIn() = runTest {
        val vm = readyViewModel()
        val stream = generation.streamed()
        vm.onAction(SmartExtractAction.SetDensity(ExtractDensity.Concise))
        vm.onAction(SmartExtractAction.ToggleArchetype(CardArchetype.CaseStudy))
        vm.onAction(SmartExtractAction.SetLanguage("Spanish"))
        vm.onAction(SmartExtractAction.Generate)

        assertEquals(GenerationState.Running(0, 1), vm.state.generation)
        val options = generation.requests.single().options
        assertEquals(ExtractDensity.Concise, options.density)
        assertTrue(CardArchetype.CaseStudy in options.archetypes)
        assertEquals("Spanish", options.language)

        stream.send(GenerationUpdate.Card(card("What does the amygdala process?", "Fear")))
        assertEquals(listOf("What does the amygdala process?"), vm.state.queue.map { it.card.front })
        assertTrue(vm.state.isGenerating)

        stream.send(GenerationUpdate.Card(card("What does the amygdala process", "Fear, again"))) // a duplicate
        stream.send(GenerationUpdate.Card(card("The {{c1::amygdala}} processes fear.", kind = NoteKind.Cloze)))
        stream.send(GenerationUpdate.Done())
        assertEquals(GenerationState.Done(added = 2), vm.state.generation)
        assertEquals(2, vm.state.queue.size)
        assertEquals(1, vm.state.duplicatesSkipped)
        // Nothing is saved before acceptance.
        assertTrue(cards.notes.value.isEmpty())
    }

    @Test
    fun theProviderNoticeComesBeforeTheFirstRequest() = runTest {
        val vm = viewModel()
        providers.addProvider(provider.copy(disclosureAcceptedAt = null))
        vm.onAction(SmartExtractAction.TextChanged("Some notes"))
        vm.onAction(SmartExtractAction.Generate)
        assertEquals("p", vm.state.disclosure?.provider?.id)
        assertTrue(generation.requests.isEmpty())

        vm.onAction(SmartExtractAction.DismissDisclosure)
        assertNull(vm.state.disclosure)
        assertTrue(generation.requests.isEmpty())

        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertEquals(1, generation.requests.size)
        assertNotNull(providers.getProvider("p")?.disclosureAcceptedAt)
    }

    @Test
    fun aFailureKeepsTheCardsAndRetryResumes() = runTest {
        val vm = readyViewModel("Part one\n---\nPart two")
        var attempts = 0
        generation.respond = { request ->
            when {
                request.part == 0 -> flowOf(GenerationUpdate.Card(card("Q1", "A1")), GenerationUpdate.Done())
                attempts++ == 0 -> flowOf(GenerationUpdate.Card(card("Q2", "A2")), GenerationUpdate.Done(AiFailure(AiProblem.Unreachable)))
                else -> flowOf(GenerationUpdate.Card(card("Q3", "A3")), GenerationUpdate.Done())
            }
        }
        vm.onAction(SmartExtractAction.Generate)
        assertEquals(GenerationState.Failed(AiFailure(AiProblem.Unreachable), part = 1, parts = 2), vm.state.generation)
        assertEquals(listOf("Q1", "Q2"), vm.state.queue.map { it.card.front })
        assertEquals(listOf("Part one", "Part two"), vm.state.queue.map { it.source })

        vm.onAction(SmartExtractAction.Retry)
        assertEquals(listOf(0, 1, 1), generation.requests.map { it.part })
        assertEquals(listOf("Q1", "Q2", "Q3"), vm.state.queue.map { it.card.front })
        assertEquals(GenerationState.Done(added = 1), vm.state.generation)
    }

    @Test
    fun acceptingSavesAiNotesInOneTransaction() = runTest {
        val vm = readyViewModel()
        generation.answer(
            card("What is LTP?", "Lasting synaptic strengthening", tags = listOf("memory")),
            card("The {{c1::hippocampus}} stores {{c2::episodes}}.", kind = NoteKind.Cloze),
            card("Where is the amygdala?", "Temporal lobe"),
        )
        vm.onAction(SmartExtractAction.Generate)
        val (ltp, cloze, where) = vm.state.queue.map { it.card.id }

        // An edit that leaves a card unusable keeps it out of Accept All.
        vm.onAction(SmartExtractAction.Edit(where))
        vm.onAction(SmartExtractAction.EditBack(where, " "))
        assertEquals(GeneratedCardProblem.EmptyBack, vm.state.queue.last().problem)
        vm.onAction(SmartExtractAction.EditFront(ltp, "What is long-term potentiation?"))

        vm.onAction(SmartExtractAction.AcceptAll)
        assertEquals(1, cards.addNotesCalls)
        val notes = cards.notes.value.values
        assertEquals(setOf("What is long-term potentiation?", "The {{c1::hippocampus}} stores {{c2::episodes}}."), notes.map { it.fields[0] }.toSet())
        assertTrue(notes.all { it.source == NoteSource.Ai && it.deckId == deckId })
        assertEquals(3, cards.cards.value.size) // one basic, two cloze
        assertEquals(ExtractMessage.Accepted(3, "Neuroscience"), vm.state.message)
        assertEquals(listOf(where), vm.state.queue.map { it.card.id })

        vm.onAction(SmartExtractAction.MessageShown)
        vm.onAction(SmartExtractAction.EditBack(where, "Medial temporal lobe"))
        vm.onAction(SmartExtractAction.Accept(where))
        assertTrue(vm.state.queue.isEmpty())
        assertEquals(2, cards.addNotesCalls)
        assertTrue(cloze.isNotEmpty())
    }

    @Test
    fun acceptingIntoADeckThatHasCardsAsksOncePerDeck() = runTest {
        decks.setCounts(deckId, FakeDeckRepository.DeckCounts(total = 12))
        val vm = readyViewModel()
        generation.answer(card("Q1", "A1"), card("Q2", "A2"), card("Q3", "A3"))
        vm.onAction(SmartExtractAction.Generate)
        val (first, second) = vm.state.queue.map { it.card.id }

        vm.onAction(SmartExtractAction.Accept(first))
        assertEquals(NonEmptyDeck("Neuroscience", 12), vm.state.nonEmptyDeck)
        assertTrue(cards.notes.value.isEmpty(), "nothing is saved until the user agrees")
        assertEquals(3, vm.state.queue.size)

        vm.onAction(SmartExtractAction.CancelNonEmptyDeck)
        assertNull(vm.state.nonEmptyDeck)
        assertTrue(cards.notes.value.isEmpty())

        vm.onAction(SmartExtractAction.Accept(first))
        vm.onAction(SmartExtractAction.ConfirmNonEmptyDeck)
        assertNull(vm.state.nonEmptyDeck)
        assertEquals(1, cards.notes.value.size)
        assertEquals(2, vm.state.queue.size)
        assertEquals(second, vm.state.queue.first().card.id)

        vm.onAction(SmartExtractAction.AcceptAll)
        assertNull(vm.state.nonEmptyDeck, "the deck was agreed to already")
        assertEquals(3, cards.notes.value.size)
    }

    @Test
    fun anEmptyDeckIsNotAskedAboutAgainOnceCardsWentIn() = runTest {
        val vm = readyViewModel()
        vm.onAction(SmartExtractAction.SelectDeck(deckId))
        generation.answer(card("Q1", "A1"), card("Q2", "A2"))
        vm.onAction(SmartExtractAction.Generate)
        val first = vm.state.queue.first().card.id

        vm.onAction(SmartExtractAction.Accept(first))
        assertNull(vm.state.nonEmptyDeck)
        // The deck list catches up with the saved card.
        decks.setCounts(deckId, FakeDeckRepository.DeckCounts(total = 1))
        vm.onAction(SmartExtractAction.AcceptAll)
        assertNull(vm.state.nonEmptyDeck)
        assertEquals(2, cards.notes.value.size)
    }

    @Test
    fun choosingAnotherDeckDropsTheQuestion() = runTest {
        decks.setCounts(deckId, FakeDeckRepository.DeckCounts(total = 1))
        val other = decks.saveDeck("Empty")
        val vm = readyViewModel()
        generation.answer(card("Q1", "A1"))
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.SelectDeck(deckId))

        vm.onAction(SmartExtractAction.AcceptAll)
        assertNotNull(vm.state.nonEmptyDeck)
        vm.onAction(SmartExtractAction.SelectDeck(other))
        assertNull(vm.state.nonEmptyDeck)
        vm.onAction(SmartExtractAction.AcceptAll)
        assertEquals(other, cards.notes.value.values.single().deckId)
    }

    @Test
    fun regenerateAndDiscard() = runTest {
        val vm = readyViewModel()
        generation.answer(card("Vague question?", "Vague answer"), card("Keep me?", "Yes"))
        vm.onAction(SmartExtractAction.Generate)
        val (vague, keep) = vm.state.queue.map { it.card.id }

        generation.answer(card("What does the amygdala process?", "Fear"))
        vm.onAction(SmartExtractAction.Regenerate(vague))
        assertEquals(listOf("What does the amygdala process?", "Keep me?"), vm.state.queue.map { it.card.front })
        assertEquals("Vague question?", generation.requests.last().replacing?.front)
        assertEquals("The amygdala processes fear.", generation.requests.last().text)

        generation.respond = { flowOf(GenerationUpdate.Done(AiFailure(AiProblem.RateLimited))) }
        vm.onAction(SmartExtractAction.Regenerate(keep))
        assertEquals(ExtractMessage.RegenerateFailed(AiFailure(AiProblem.RateLimited)), vm.state.message)
        assertTrue(vm.state.queue.none { it.regenerating })

        vm.onAction(SmartExtractAction.Discard(keep))
        assertEquals(1, vm.state.queue.size)
        vm.onAction(SmartExtractAction.DiscardAll)
        assertTrue(vm.state.queue.isEmpty())
        assertTrue(cards.notes.value.isEmpty())
    }

    @Test
    fun aDisambiguationPageIsFlaggedUntilTheNextReadOrClear() = runTest {
        val vm = readyViewModel(text = "")
        sources.results[SourceInput.Link("example.com/m")] = SourceResult.Success(SourceText("- Mercury (planet)", disambiguation = true))
        sources.results[SourceInput.Link("example.com/a")] = SourceResult.Success(SourceText("An article."))
        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Link))

        vm.onAction(SmartExtractAction.LinkChanged("example.com/m"))
        vm.onAction(SmartExtractAction.FetchLink)
        assertTrue(vm.state.disambiguation)

        vm.onAction(SmartExtractAction.LinkChanged("example.com/a"))
        vm.onAction(SmartExtractAction.FetchLink)
        assertFalse(vm.state.disambiguation)

        vm.onAction(SmartExtractAction.LinkChanged("example.com/m"))
        vm.onAction(SmartExtractAction.FetchLink)
        vm.onAction(SmartExtractAction.ClearText)
        assertFalse(vm.state.disambiguation)
    }

    @Test
    fun pdfsAndLinksFillTheTextBox() = runTest {
        val vm = readyViewModel(text = "")
        sources.results[SourceInput.Pdf("content://doc/1")] = SourceResult.Success(SourceText("Page text.", title = "Lecture 3", truncated = true))
        sources.results[SourceInput.Link("example.com/a")] = SourceResult.Failure(SourceProblem.NoText)

        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        vm.onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        assertEquals("Page text.", vm.state.text)
        assertEquals("Lecture 3", vm.state.title)
        assertTrue(vm.state.truncated)

        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Link))
        vm.onAction(SmartExtractAction.LinkChanged(" example.com/a "))
        vm.onAction(SmartExtractAction.FetchLink)
        assertEquals(SourceProblem.NoText, vm.state.sourceProblem)
        assertEquals("Page text.", vm.state.text) // a failed read keeps the text

        vm.onAction(SmartExtractAction.Generate)
        assertEquals("Lecture 3", generation.requests.single().title)

        vm.onAction(SmartExtractAction.ClearText)
        assertEquals("", vm.state.text)
        assertNull(vm.state.title)
        assertTrue(!vm.state.canGenerate)
    }

    @Test
    fun aDroppedFileBecomesTheSourceByItsKind() = runTest {
        val vm = readyViewModel(text = "")
        sources.results[SourceInput.Pdf("/home/me/Lecture 3.pdf")] = SourceResult.Success(SourceText("From the PDF.", title = "Lecture 3"))
        sources.results[SourceInput.TextFile("/home/me/notes.md")] = SourceResult.Success(SourceText("# Notes\nFrom the file.", title = "notes"))

        vm.onAction(SmartExtractAction.FileDropped("/home/me/Lecture 3.pdf"))
        assertEquals(SourceKind.Pdf, vm.state.sourceKind)
        assertEquals("From the PDF.", vm.state.text)

        // A text file goes to the paste box, which shows what was read.
        vm.onAction(SmartExtractAction.FileDropped("/home/me/notes.md"))
        assertEquals(SourceKind.Paste, vm.state.sourceKind)
        assertEquals("# Notes\nFrom the file.", vm.state.text)
        assertEquals("notes", vm.state.title)

        // Something else is left alone.
        vm.onAction(SmartExtractAction.FileDropped("/home/me/photo.png"))
        assertEquals("# Notes\nFrom the file.", vm.state.text)
        assertEquals(DroppedFile.Pdf, DroppedFile.of("SLIDES.PDF"))
        assertNull(DroppedFile.of("archive"))
    }

    @Test
    fun dictationAppendsWhatIsSaid() = runTest {
        val vm = readyViewModel(text = "Notes:")
        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Dictation))
        vm.onAction(SmartExtractAction.StartDictation)
        sources.dictation.emit(DictationEvent.Listening)
        sources.dictation.emit(DictationEvent.Partial("the amygdala"))
        assertEquals(DictationState.Listening("the amygdala"), vm.state.dictation)
        sources.dictation.emit(DictationEvent.Final("The amygdala processes fear."))
        assertEquals("Notes: The amygdala processes fear.", vm.state.text)

        // Stopping keeps the phrase in progress.
        sources.dictation.emit(DictationEvent.Partial("It sits in"))
        vm.onAction(SmartExtractAction.StopDictation)
        assertEquals("Notes: The amygdala processes fear. It sits in", vm.state.text)
        assertEquals(DictationState.Off, vm.state.dictation)

        sources.dictationAvailable = false
        vm.onAction(SmartExtractAction.StartDictation)
        assertEquals(DictationState.Failed(DictationProblem.Unavailable), vm.state.dictation)
        vm.onAction(SmartExtractAction.DictationPermissionDenied)
        assertIs<DictationState.Failed>(vm.state.dictation)
    }

    @Test
    fun dictationIsOnlyOfferedWhereTheDeviceCanRecognizeSpeech() = runTest {
        assertEquals(true, viewModel().state.dictationAvailable)

        sources.dictationAvailable = false
        assertEquals(false, viewModel().state.dictationAvailable)
    }
}
