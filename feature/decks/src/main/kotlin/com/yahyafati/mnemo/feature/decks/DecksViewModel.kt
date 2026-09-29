package com.yahyafati.mnemo.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.domain.DeckNode
import com.yahyafati.mnemo.core.domain.GetTodaySummaryUseCase
import com.yahyafati.mnemo.core.model.DeckSummary
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TodaySummary
import com.yahyafati.mnemo.core.ui.deck.DeckDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DecksViewModel @Inject constructor(
    private val deckRepository: DeckRepository,
    private val transferRepository: DataTransferRepository,
    getTodaySummary: GetTodaySummaryUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<DeckFilter>(DeckFilter.All)
    private val expanded = MutableStateFlow(emptySet<String>())
    private val dialog = MutableStateFlow<DecksDialog?>(null)
    private val summaries = deckRepository.observeDeckSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val controls = combine(query, filter, expanded, dialog) { q, f, e, d -> Controls(q, f, e, d) }

    private val transfers = combine(transferRepository.importState, transferRepository.exportState, ::Pair)

    val uiState: StateFlow<DecksUiState> = combine(summaries, getTodaySummary(), controls, transfers) { decks, today, c, (importing, exporting) ->
        val state = if (decks == null) DecksUiState(dialog = c.dialog) else buildState(decks, today, c)
        state.copy(importState = importing, exportState = exporting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DecksUiState())

    fun onAction(action: DecksAction) {
        when (action) {
            is DecksAction.QueryChanged -> query.value = action.query
            is DecksAction.FilterSelected -> filter.update { if (it == action.filter) DeckFilter.All else action.filter }
            is DecksAction.ToggleExpanded -> expanded.update {
                if (action.deckId in it) it - action.deckId else it + action.deckId
            }
            is DecksAction.ToggleStar -> viewModelScope.launch {
                val deck = summary(action.deckId)?.deck ?: return@launch
                deckRepository.setStarred(deck.id, !deck.starred)
            }
            DecksAction.NewDeck -> dialog.value = DecksDialog.NewDeck
            is DecksAction.EditDeck -> summary(action.deckId)?.let { s ->
                dialog.value = DecksDialog.EditDeck(
                    deckId = s.deck.id,
                    draft = DeckDraft(s.path, s.deck.category.orEmpty(), s.deck.description),
                )
            }
            is DecksAction.DeleteDeck -> summaries.value?.let { all ->
                val node = DeckNode.build(all).flatMap { it.flatten() }.firstOrNull { it.deck.id == action.deckId }
                    ?: return@let
                dialog.value = DecksDialog.ConfirmDelete(node.deck.id, node.summary.path, node.totalCount)
            }
            is DecksAction.SaveDeck -> {
                val editing = dialog.value as? DecksDialog.EditDeck
                dialog.value = null
                viewModelScope.launch {
                    deckRepository.saveDeck(
                        path = action.draft.path,
                        description = action.draft.description,
                        category = action.draft.category,
                        id = editing?.deckId,
                    )
                }
            }
            DecksAction.ConfirmDelete -> {
                val confirm = dialog.value as? DecksDialog.ConfirmDelete ?: return
                dialog.value = null
                viewModelScope.launch { deckRepository.deleteDeck(confirm.deckId) }
            }
            DecksAction.DismissDialog -> dialog.value = null
            is DecksAction.Import -> transferRepository.startImport(action.uri)
            is DecksAction.Export -> transferRepository.startExport(action.uri, ExportFormat.Apkg, action.deckId)
            DecksAction.DismissTransfer -> transferRepository.clearFinished()
        }
    }

    private fun summary(deckId: String): DeckSummary? = summaries.value?.firstOrNull { it.deck.id == deckId }

    private fun buildState(decks: List<DeckSummary>, today: TodaySummary, c: Controls): DecksUiState {
        val now = clock.now()
        val hour = now.atZone(clock.zone()).hour
        val roots = DeckNode.build(decks)
        val items = roots.map { it.toItem(c.expanded) }
        val query = c.query.trim()
        val visible = items.filter { item ->
            val matchesQuery = query.isEmpty() || item.matches(query)
            val matchesFilter = when (val f = c.filter) {
                DeckFilter.All -> true
                DeckFilter.Due -> item.hasCardsToStudy
                DeckFilter.Starred -> item.starred || item.descendants().any { it.first.starred }
                is DeckFilter.Category -> item.category.equals(f.name, ignoreCase = true)
            }
            matchesQuery && matchesFilter
        }
        return DecksUiState(
            isLoading = false,
            now = now,
            date = StudyDay.date(now, clock.zone()),
            greeting = when (hour) {
                in 4..11 -> Greeting.Morning
                in 12..17 -> Greeting.Afternoon
                else -> Greeting.Evening
            },
            today = today,
            decks = visible,
            hasDecks = items.isNotEmpty(),
            query = c.query,
            filter = c.filter,
            filterCounts = FilterCounts(
                all = items.size,
                due = items.count { it.hasCardsToStudy },
                starred = items.count { item -> item.starred || item.descendants().any { it.first.starred } },
            ),
            categories = items.mapNotNull { it.category }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() },
            dialog = c.dialog,
        )
    }

    private fun DeckNode.toItem(expanded: Set<String>): DeckItem = DeckItem(
        id = deck.id,
        name = deck.name,
        category = deck.category,
        starred = deck.starred,
        dueCount = dueCount,
        newCount = newCount,
        totalCount = totalCount,
        lastReviewedAt = lastReviewedAt,
        children = children.map { it.toItem(expanded) },
        expanded = deck.id in expanded,
    )

    private fun DeckItem.matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) ||
            category?.contains(query, ignoreCase = true) == true ||
            children.any { it.matches(query) }

    private data class Controls(
        val query: String,
        val filter: DeckFilter,
        val expanded: Set<String>,
        val dialog: DecksDialog?,
    )
}
