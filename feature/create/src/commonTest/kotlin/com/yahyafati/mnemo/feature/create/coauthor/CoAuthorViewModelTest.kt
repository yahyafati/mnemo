package com.yahyafati.mnemo.feature.create.coauthor

import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.FindDuplicateNotesUseCase
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCoAuthorRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
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

class CoAuthorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val decks = FakeDeckRepository()
    private val cards = FakeCardRepository()
    private val coAuthor = FakeCoAuthorRepository()
    private val deckId = runBlocking { decks.saveDeck("Biology") }
    private val childId = runBlocking { decks.saveDeck("Biology::Cells") }

    private val provider = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
        disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private fun viewModel(withProvider: Boolean = true) = CoAuthorViewModel(
        savedStateHandle = SavedStateHandle(mapOf(CoAuthorViewModel.DECK_ID_KEY to deckId)),
        aiProviders = providers.also { if (withProvider) it.addProvider(provider) },
        deckRepository = decks,
        cardRepository = cards,
        coAuthor = coAuthor,
        findDuplicates = FindDuplicateNotesUseCase(decks, cards),
        acceptCards = AcceptGeneratedCardsUseCase(cards, FakeMediaRepository()),
    )

    private val CoAuthorViewModel.state get() = uiState.value

    @Test
    fun chatSendsTheDeckAndItsSubdecksWithTheConversation() = runTest {
        cards.addNote(deckId, NoteKind.Basic, listOf("What makes ATP?", "Mitochondria"), emptyList())
        cards.addNote(childId, NoteKind.Basic, listOf("Where is DNA?", "Nucleus"), emptyList())
        val vm = viewModel()
        assertEquals(deckId, vm.state.deckId)
        vm.onAction(CoAuthorAction.InputChanged("What am I missing?"))
        vm.onAction(CoAuthorAction.Send)

        val (deck, history) = coAuthor.chats.single()
        assertEquals("Biology", deck.name)
        assertEquals(2, deck.notes.size)
        assertEquals(listOf(ChatTurn(true, "What am I missing?")), history)
        val reply = assertIs<CoAuthorMessage.Reply>(vm.state.messages.last())
        assertEquals("A reply.", reply.text)
        assertTrue(reply.done)
        assertFalse(vm.state.busy)
        assertEquals("", vm.state.input)

        // The next message carries the conversation so far.
        vm.onAction(CoAuthorAction.InputChanged("And enzymes?"))
        vm.onAction(CoAuthorAction.Send)
        assertEquals(
            listOf(ChatTurn(true, "What am I missing?"), ChatTurn(false, "A reply."), ChatTurn(true, "And enzymes?")),
            coAuthor.chats.last().second,
        )
    }

    @Test
    fun suggestionsSkipWhatTheDeckHasAndCanBeAdded() = runTest {
        cards.addNote(deckId, NoteKind.Basic, listOf("What makes ATP?", "Mitochondria"), emptyList())
        coAuthor.suggest = {
            flowOf(
                GenerationUpdate.Card(card("What **makes** ATP", "Mitochondria").copy(id = "dup")),
                GenerationUpdate.Card(card("What do enzymes lower?", "Activation energy").copy(id = "a")),
                GenerationUpdate.Card(card("Where are ribosomes made?", "Nucleolus").copy(id = "b")),
                GenerationUpdate.Card(card("No answer").copy(id = "invalid")),
                GenerationUpdate.Done(),
            )
        }
        val vm = viewModel()
        vm.onAction(CoAuthorAction.InputChanged("enzymes"))
        vm.onAction(CoAuthorAction.SuggestCards)
        assertEquals("enzymes", coAuthor.suggestions.single().second)

        val message = assertIs<CoAuthorMessage.Suggestions>(vm.state.messages.single())
        assertEquals(listOf("a", "b"), message.cards.map { it.card.id })
        vm.onAction(CoAuthorAction.DiscardSuggestion(message.id, "b"))
        vm.onAction(CoAuthorAction.AddAllSuggestions(message.id))

        val added = cards.notes.value.values.single { it.source == NoteSource.Ai }
        assertEquals(listOf("What do enzymes lower?", "Activation energy"), added.fields)
        val statuses = assertIs<CoAuthorMessage.Suggestions>(vm.state.messages.single()).cards.map { it.status }
        assertEquals(listOf(SuggestionStatus.Added, SuggestionStatus.Discarded), statuses)
        assertEquals(CoAuthorNotice.Added(1), vm.state.notice)
    }

    @Test
    fun duplicatesAreFoundOnTheDeviceEvenWithoutAProvider() = runTest {
        val first = cards.addNote(deckId, NoteKind.Basic, listOf("What makes ATP?", "Mitochondria"), emptyList())
        val second = cards.addNote(childId, NoteKind.Basic, listOf("what makes **ATP**", "The mitochondria"), emptyList())
        val vm = viewModel(withProvider = false)
        assertNull(vm.state.route)
        assertTrue(vm.state.canFindDuplicates)
        assertFalse(vm.state.canAsk)

        vm.onAction(CoAuthorAction.FindDuplicates)
        val message = assertIs<CoAuthorMessage.Duplicates>(vm.state.messages.single())
        assertEquals(listOf(first.id, second.id), message.groups.single().notes.map { it.id })

        vm.onAction(CoAuthorAction.DeleteNote(message.id, second.id))
        assertNull(cards.getNote(second.id))
        assertEquals(setOf(second.id), assertIs<CoAuthorMessage.Duplicates>(vm.state.messages.single()).deleted)
        assertTrue(coAuthor.chats.isEmpty())
    }

    @Test
    fun weakCardsGetRewritesThatCanBeApplied() = runTest {
        val note = cards.addNote(deckId, NoteKind.Basic, listOf("Krebs cycle location?", "Matrix"), listOf("t"), hint = "h")
        val cloze = cards.addNote(deckId, NoteKind.Cloze, listOf("{{c1::ATP}} synthase", ""), emptyList())
        cards.addNote(deckId, NoteKind.Basic, listOf("Easy", "Card"), emptyList())
        cards.cards.value.values.forEach { card ->
            val lapses = when (card.noteId) {
                note.id -> 6
                cloze.id -> 4
                else -> 1
            }
            cards.putCard(card.copy(lapses = lapses, reps = 10))
        }
        coAuthor.improve = { card ->
            if (card.kind == NoteKind.Cloze) {
                RewriteOutcome.Proposed(listOf("{{c2::ATP}} synthase", ""))
            } else {
                RewriteOutcome.Proposed(listOf("Where in the mitochondrion is the Krebs cycle?", "The matrix"))
            }
        }
        val vm = viewModel()
        vm.onAction(CoAuthorAction.ImproveWeakCards)

        val message = assertIs<CoAuthorMessage.WeakCards>(vm.state.messages.single())
        assertTrue(message.done)
        assertEquals(listOf(note.id, cloze.id), message.items.map { it.card.note.id })
        // Changing cloze numbers would lose the card's history, so it can't be applied.
        assertEquals(false, assertIs<WeakCardState.Proposed>(message.items[1].state).applicable)

        vm.onAction(CoAuthorAction.ApplyRewrite(message.id, message.items[0].card.card.id))
        val updated = assertNotNull(cards.getNote(note.id))
        assertEquals(listOf("Where in the mitochondrion is the Krebs cycle?", "The matrix"), updated.fields)
        assertEquals("h", updated.hint)
        assertEquals(listOf("t"), updated.tags)
        assertEquals(WeakCardState.Applied, assertIs<CoAuthorMessage.WeakCards>(vm.state.messages.single()).items[0].state)
    }

    @Test
    fun theProviderNoticeComesFirstAndFailuresAreShown() = runTest {
        providers.addProvider(provider.copy(disclosureAcceptedAt = null))
        coAuthor.reply = { flowOf(AssistUpdate.Done(AiFailure(AiProblem.RateLimited))) }
        val vm = viewModel(withProvider = false)
        vm.onAction(CoAuthorAction.InputChanged("Hi"))
        vm.onAction(CoAuthorAction.Send)
        assertNotNull(vm.state.disclosure)
        assertTrue(coAuthor.chats.isEmpty())

        vm.onAction(CoAuthorAction.AcceptDisclosure)
        assertEquals(1, coAuthor.chats.size)
        assertEquals(AiFailure(AiProblem.RateLimited), assertIs<CoAuthorMessage.Reply>(vm.state.messages.last()).failure)
    }

    @Test
    fun switchingDecksStartsANewConversation() = runTest {
        val vm = viewModel()
        vm.onAction(CoAuthorAction.InputChanged("Hi"))
        vm.onAction(CoAuthorAction.Send)
        assertEquals(2, vm.state.messages.size)
        vm.onAction(CoAuthorAction.SelectDeck(childId))
        assertTrue(vm.state.messages.isEmpty())
    }
}
