package com.yahyafati.mnemo.feature.browse

import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.CardStatus
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeCardBrowserRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val t = Instant.parse("2026-01-01T09:00:00Z")

    private fun studyCard(id: String, deck: String, front: String, back: String, tags: List<String> = emptyList(), state: CardState = CardState.New) =
        StudyCard(
            card = Card(id, "n-$id", deck, 0, state = state, due = t, createdAt = t, updatedAt = t),
            note = Note("n-$id", deck, NoteType.Basic.id, listOf(front, back), tags, createdAt = t, updatedAt = t),
            kind = NoteKind.Basic,
            deckName = deck,
        )

    private val browser = FakeCardBrowserRepository(
        listOf(
            studyCard("c1", "bio", "What do **mitochondria** make?", "ATP", listOf("cells"), CardState.Review),
            studyCard("c2", "bio", "{{c1::Ribosomes}} build proteins", "", listOf("cells")),
            studyCard("c3", "jp", "猫", "cat"),
        ),
    )
    private val decks = FakeDeckRepository().apply {
        addDeck(Deck("bio", "Biology", createdAt = t, updatedAt = t))
        addDeck(Deck("jp", "Japanese", createdAt = t, updatedAt = t))
    }

    private fun viewModel(deckId: String? = null) =
        BrowseViewModel(SavedStateHandle(mapOf(BrowseViewModel.DECK_ID_KEY to deckId)), browser, decks)

    private fun runWith(vm: BrowseViewModel, block: suspend () -> Unit) = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { vm.uiState.collect {} }
        block()
    }

    @Test
    fun rowsArePlainText() {
        val items = browser.cards.value.map { it.toBrowseItem() }
        assertEquals(listOf("What do mitochondria make?", "Ribosomes build proteins", "猫"), items.map { it.front })
        assertEquals("ATP", items.first().back)
        assertEquals(listOf("cells"), items.first().tags)
    }

    @Test
    fun searchReachesTheRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.cards.collect {} }
        vm.onAction(BrowseAction.TextChanged("mito"))
        advanceTimeBy(1_000)
        assertEquals("mito", browser.lastQuery?.text)
    }

    @Test
    fun startsFilteredToTheRouteDeckAndFilters() {
        val vm = viewModel(deckId = "jp")
        runWith(vm) {
            assertEquals("jp", vm.uiState.value.query.deckId)
            assertEquals("Japanese", vm.uiState.value.deckName)
            assertEquals(1, vm.uiState.value.matchCount)
            assertEquals(listOf("Biology", "Japanese"), vm.uiState.value.decks.map { it.path })

            vm.onAction(BrowseAction.DeckSelected(null))
            vm.onAction(BrowseAction.TagSelected("cells"))
            assertEquals(2, vm.uiState.value.matchCount)
            vm.onAction(BrowseAction.StatusSelected(CardStatus.Review))
            assertEquals(1, vm.uiState.value.matchCount)
            // Choosing the active status again clears it.
            vm.onAction(BrowseAction.StatusSelected(CardStatus.Review))
            assertEquals(2, vm.uiState.value.matchCount)
            assertEquals(listOf("cells"), vm.uiState.value.tags)
        }
    }

    @Test
    fun selectionAndBulkEdits() {
        val vm = viewModel()
        runWith(vm) {
            vm.onAction(BrowseAction.ToggleSelected("c1"))
            vm.onAction(BrowseAction.ToggleSelected("c3"))
            assertTrue(vm.uiState.value.selecting)

            vm.onAction(BrowseAction.Suspend(true))
            assertEquals(BrowseMessage.Suspended(2, true), vm.uiState.value.message)
            assertTrue(vm.uiState.value.selection.isEmpty())
            assertEquals(setOf("c1", "c3"), browser.cards.value.filter { it.card.suspended }.map { it.card.id }.toSet())
            vm.onAction(BrowseAction.MessageShown)

            vm.onAction(BrowseAction.SelectAll)
            assertEquals(3, vm.uiState.value.selection.size)
            vm.onAction(BrowseAction.ShowMove)
            assertEquals(BrowseDialog.Move, vm.uiState.value.dialog)
            vm.onAction(BrowseAction.Move("jp"))
            assertEquals(BrowseMessage.Moved(3, "Japanese"), vm.uiState.value.message)
            assertTrue(browser.cards.value.all { it.card.deckId == "jp" })

            vm.onAction(BrowseAction.ToggleSelected("c2"))
            vm.onAction(BrowseAction.AddTag("  exam prep "))
            assertEquals(listOf("cells", "exam-prep"), browser.cards.value.single { it.card.id == "c2" }.note.tags)
            assertEquals(listOf("cells", "exam-prep"), vm.uiState.value.tags)

            vm.onAction(BrowseAction.ToggleSelected("c2"))
            vm.onAction(BrowseAction.ShowDelete)
            assertEquals(BrowseDialog.ConfirmDelete(1), vm.uiState.value.dialog)
            vm.onAction(BrowseAction.ConfirmDelete)
            assertEquals(listOf("c1", "c3"), browser.cards.value.map { it.card.id })
        }
    }

    @Test
    fun changingAFilterClearsTheSelection() {
        val vm = viewModel()
        runWith(vm) {
            vm.onAction(BrowseAction.ToggleSelected("c1"))
            vm.onAction(BrowseAction.StatusSelected(CardStatus.New))
            assertTrue(vm.uiState.value.selection.isEmpty())
        }
    }

    @Test
    fun aRightClickMakesTheClickedCardTheSelectionUnlessItIsInIt() {
        val vm = viewModel()
        runWith(vm) {
            // Nothing selected: the card clicked becomes the selection, and the menu's action is about it alone.
            vm.onAction(BrowseAction.SelectForMenu("c2"))
            assertEquals(setOf("c2"), vm.uiState.value.selection)

            // Another card replaces it...
            vm.onAction(BrowseAction.SelectForMenu("c1"))
            assertEquals(setOf("c1"), vm.uiState.value.selection)

            // ...but a card that is already selected keeps the whole selection, so the action covers all of it.
            vm.onAction(BrowseAction.ToggleSelected("c3"))
            vm.onAction(BrowseAction.SelectForMenu("c3"))
            assertEquals(setOf("c1", "c3"), vm.uiState.value.selection)
            vm.onAction(BrowseAction.Flag(true))
            assertEquals(BrowseMessage.Flagged(2, true), vm.uiState.value.message)
        }
    }
}
