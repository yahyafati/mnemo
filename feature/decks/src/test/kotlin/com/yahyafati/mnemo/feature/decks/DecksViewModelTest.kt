package com.yahyafati.mnemo.feature.decks

import com.yahyafati.mnemo.core.domain.GetRetentionOverviewUseCase
import com.yahyafati.mnemo.core.domain.GetTodaySummaryUseCase
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDataTransferRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository.DeckCounts
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeStatsRepository
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
    private val transfers = FakeDataTransferRepository()
    private val stats = FakeStatsRepository()
    private val settings = FakeUserSettingsRepository()
    // Lazy: the ViewModel must be created after MainDispatcherRule has set Dispatchers.Main.
    private val viewModel by lazy {
        DecksViewModel(
            deckRepository = decks,
            transferRepository = transfers,
            getTodaySummary = GetTodaySummaryUseCase(
                decks, FakeCardRepository(), FakeReviewRepository(), settings, clock,
            ),
            getRetentionOverview = GetRetentionOverviewUseCase(stats, settings, clock),
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

    @Test
    fun importExportAndTheirResults() = runWithState {
        viewModel.onAction(DecksAction.Import("content://downloads/deck.apkg"))
        assertEquals(listOf("content://downloads/deck.apkg"), transfers.imports)
        assertIs<TransferState.Running>(state.importState)

        val summary = ImportSummary(1, 10, 20, 5, 2, 0, 0)
        transfers.importState.value = TransferState.Succeeded(summary)
        assertEquals(TransferState.Succeeded(summary), state.importState)
        viewModel.onAction(DecksAction.DismissTransfer)
        assertEquals(TransferState.Idle, state.importState)

        viewModel.onAction(DecksAction.Export("deck-1", "content://docs/deck.apkg"))
        assertEquals(Triple("content://docs/deck.apkg", ExportFormat.Apkg, "deck-1"), transfers.exports.single())
    }

    @Test
    fun retentionTilesAndHealthIncludeSubdecks() = runWithState {
        val child = decks.saveDeck("Languages::Japanese")
        val parent = decks.getDeck(child)!!.parentId!!
        decks.saveDeck("Biology")
        stats.deckMaturity.value = listOf(DeckMaturity(child, 0, 0, 2, 6, 200.0))
        // Ratio 0 is 100% recall, ratio 1 is 90%.
        stats.retrievability.value = listOf(
            RetrievabilityBucket(parent, cards = 1, meanElapsedRatio = 0.0),
            RetrievabilityBucket(child, cards = 1, meanElapsedRatio = 1.0),
        )

        assertEquals(6, state.retention.matureCards)
        assertNull(state.retention.retention)
        val languages = state.decks.single { it.name == "Languages" }
        assertEquals(0.95, languages.recall!!, 1e-9)
        assertEquals(0.9, languages.children.single().recall!!, 1e-9)
        assertNull(state.decks.single { it.name == "Biology" }.recall)
    }
}
