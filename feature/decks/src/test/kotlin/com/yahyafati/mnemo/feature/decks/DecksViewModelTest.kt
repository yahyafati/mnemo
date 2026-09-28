package com.yahyafati.mnemo.feature.decks

import com.yahyafati.mnemo.core.domain.GetTodaySummaryUseCase
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository.DeckCounts
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import com.yahyafati.mnemo.core.ui.deck.DeckDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = TestClock(Instant.parse("2026-01-01T09:00:00Z"))
    private val decks = FakeDeckRepository()
    // Lazy: the ViewModel must be created after MainDispatcherRule has set Dispatchers.Main.
    private val viewModel by lazy {
        DecksViewModel(
            deckRepository = decks,
            getTodaySummary = GetTodaySummaryUseCase(
                decks, FakeCardRepository(), FakeReviewRepository(), FakeUserSettingsRepository(), clock,
            ),
            clock = clock,
        )
    }

    private fun runWithState(block: suspend () -> Unit) = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        block()
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun emptyLibrary() = runWithState {
        assertEquals(false, state.isLoading)
        assertEquals(false, state.hasDecks)
        assertEquals(Greeting.Morning, state.greeting)
    }

    @Test
    fun newDeckDialogCreatesNestedDecks() = runWithState {
        viewModel.onAction(DecksAction.NewDeck)
        assertEquals(DecksDialog.NewDeck, state.dialog)

        viewModel.onAction(DecksAction.SaveDeck(DeckDraft("Languages::Japanese", "Language")))
        assertNull(state.dialog)
        val top = state.decks.single()
        assertEquals("Languages", top.name)
        assertEquals("Japanese", top.children.single().name)
        assertEquals("Language", top.children.single().category)
    }

    @Test
    fun searchFilterAndStar() = runWithState {
        val bio = decks.saveDeck("Biology", category = "Science")
        decks.saveDeck("Spanish", category = "Language")
        decks.setCounts(bio, DeckCounts(due = 3, total = 10))

        viewModel.onAction(DecksAction.FilterSelected(DeckFilter.Due))
        assertEquals(listOf("Biology"), state.decks.map { it.name })
        assertEquals(1, state.filterCounts.due)

        // Selecting the active filter again clears it.
        viewModel.onAction(DecksAction.FilterSelected(DeckFilter.Due))
        assertEquals(2, state.decks.size)

        viewModel.onAction(DecksAction.QueryChanged("span"))
        assertEquals(listOf("Spanish"), state.decks.map { it.name })
        viewModel.onAction(DecksAction.QueryChanged(""))

        viewModel.onAction(DecksAction.ToggleStar(bio))
        viewModel.onAction(DecksAction.FilterSelected(DeckFilter.Starred))
        assertEquals(listOf("Biology"), state.decks.map { it.name })
        assertEquals(listOf("Language", "Science"), state.categories)
    }

    @Test
    fun subdeckCountsRollUpAndExpand() = runWithState {
        val child = decks.saveDeck("Parent::Child")
        decks.setCounts(child, DeckCounts(due = 2, new = 1, total = 5))
        val parent = state.decks.single()
        assertEquals(2, parent.dueCount)
        assertEquals(5, parent.totalCount)
        assertEquals(false, parent.expanded)

        viewModel.onAction(DecksAction.ToggleExpanded(parent.id))
        assertTrue(state.decks.single().expanded)
    }

    @Test
    fun deleteAsksFirst() = runWithState {
        val id = decks.saveDeck("Old")
        viewModel.onAction(DecksAction.DeleteDeck(id))
        assertIs<DecksDialog.ConfirmDelete>(state.dialog)
        viewModel.onAction(DecksAction.ConfirmDelete)
        assertTrue(decks.getDecks().isEmpty())
        assertEquals(false, state.hasDecks)
    }

    @Test
    fun editPrefillsTheFullPath() = runWithState {
        val id = decks.saveDeck("A::B", description = "desc")
        viewModel.onAction(DecksAction.EditDeck(id))
        val dialog = assertIs<DecksDialog.EditDeck>(state.dialog)
        assertEquals("A::B", dialog.draft.path)
        viewModel.onAction(DecksAction.SaveDeck(dialog.draft.copy(path = "A::C")))
        assertEquals("C", decks.getDeck(id)?.name)
        assertEquals(1, decks.observeDecks().first().count { it.name == "A" })
    }
}
