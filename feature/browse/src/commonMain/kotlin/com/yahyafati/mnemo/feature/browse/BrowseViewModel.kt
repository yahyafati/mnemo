package com.yahyafati.mnemo.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.yahyafati.mnemo.core.data.repository.CardBrowserRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.markdown.Markdown
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The card browser. [SavedStateHandle] key `deckId` (from `BrowseRoute`) starts it filtered to a
 * deck. Bulk actions apply to the selection.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseViewModel(
    savedStateHandle: SavedStateHandle,
    private val browser: CardBrowserRepository,
    deckRepository: DeckRepository,
) : ViewModel() {
    private val query = MutableStateFlow(CardQuery(deckId = savedStateHandle[DECK_ID_KEY]))
    private val selection = MutableStateFlow(emptySet<String>())
    private val dialog = MutableStateFlow<BrowseDialog?>(null)
    private val message = MutableStateFlow<BrowseMessage?>(null)
    private val tags = MutableStateFlow(emptyList<String>())

    /** Typing searches after a short pause; other filters apply at once. */
    private val searchQuery: Flow<CardQuery> = query
        .debounce { if (it.text.isBlank()) 0 else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()

    val cards: Flow<PagingData<BrowseItem>> = searchQuery
        .flatMapLatest { browser.browse(it) }
        .map { data -> data.map { it.toBrowseItem() } }
        .cachedIn(viewModelScope)

    private val decks = deckRepository.observeDeckSummaries()
        .map { list -> list.map { DeckOption(it.deck.id, it.path) }.sortedBy { it.path.lowercase() } }

    val uiState: StateFlow<BrowseUiState> = combine(
        combine(query, searchQuery.flatMapLatest { browser.count(it) }, ::Pair),
        decks,
        tags,
        combine(selection, dialog, message, ::Triple),
    ) { (q, count), deckOptions, tagList, (selected, d, m) ->
        BrowseUiState(q, count, deckOptions, tagList, selected, d, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState(query = query.value))

    init {
        refreshTags()
    }

    fun onAction(action: BrowseAction) {
        when (action) {
            is BrowseAction.TextChanged -> query.update { it.copy(text = action.text) }
            is BrowseAction.DeckSelected -> changeFilter { it.copy(deckId = action.deckId) }
            is BrowseAction.TagSelected -> changeFilter { it.copy(tag = action.tag) }
            is BrowseAction.StatusSelected -> changeFilter { it.copy(status = if (it.status == action.status) null else action.status) }
            is BrowseAction.SortSelected -> query.update { it.copy(sort = action.sort) }
            is BrowseAction.ToggleSelected -> selection.update {
                if (action.cardId in it) it - action.cardId else it + action.cardId
            }
            is BrowseAction.SelectForMenu -> selection.update { if (action.cardId in it) it else setOf(action.cardId) }
            BrowseAction.SelectAll -> viewModelScope.launch { selection.value = browser.cardIds(query.value).toSet() }
            BrowseAction.ClearSelection -> selection.value = emptySet()
            is BrowseAction.Suspend -> bulk({ BrowseMessage.Suspended(it, action.suspended) }) { browser.setSuspended(it, action.suspended) }
            is BrowseAction.Flag -> bulk({ BrowseMessage.Flagged(it, action.flagged) }) { browser.setFlagged(it, action.flagged) }
            BrowseAction.ShowMove -> dialog.value = BrowseDialog.Move
            is BrowseAction.Move -> {
                val deck = uiState.value.decks.firstOrNull { it.id == action.deckId }?.path.orEmpty()
                bulk({ BrowseMessage.Moved(it, deck) }) { browser.moveToDeck(it, action.deckId) }
            }
            BrowseAction.ShowAddTag -> dialog.value = BrowseDialog.AddTag
            is BrowseAction.AddTag -> {
                // Tags have no spaces, as in Anki.
                val tag = action.tag.trim().replace(Regex("\\s+"), "-")
                if (tag.isEmpty()) {
                    dialog.value = null
                } else {
                    bulk({ BrowseMessage.Tagged(it, tag, added = true) }) { browser.addTag(it, tag) }
                }
            }
            BrowseAction.ShowRemoveTag -> dialog.value = BrowseDialog.RemoveTag
            is BrowseAction.RemoveTag -> bulk({ BrowseMessage.Tagged(it, action.tag, added = false) }) { browser.removeTag(it, action.tag) }
            BrowseAction.ShowDelete -> dialog.value = BrowseDialog.ConfirmDelete(selection.value.size)
            BrowseAction.ConfirmDelete -> bulk(BrowseMessage::Deleted) { browser.deleteNotes(it) }
            BrowseAction.DismissDialog -> dialog.value = null
            BrowseAction.MessageShown -> message.value = null
        }
    }

    private fun changeFilter(transform: (CardQuery) -> CardQuery) {
        query.update(transform)
        selection.value = emptySet()
    }

    /** Runs [edit] on the selection, then clears it and confirms with [confirm]. */
    private fun bulk(confirm: (Int) -> BrowseMessage, edit: suspend (Set<String>) -> Unit) {
        val ids = selection.value
        dialog.value = null
        if (ids.isEmpty()) return
        viewModelScope.launch {
            edit(ids)
            selection.value = emptySet()
            message.value = confirm(ids.size)
            refreshTags()
        }
    }

    private fun refreshTags() {
        viewModelScope.launch { tags.value = browser.tags() }
    }

    companion object {
        const val DECK_ID_KEY = "deckId"
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}

/** A card as a browser row shows it: plain text, clozes revealed. */
internal fun StudyCard.toBrowseItem(): BrowseItem {
    val sides = sides
    return BrowseItem(
        cardId = card.id,
        noteId = note.id,
        front = Markdown.plainText(sides.front),
        back = Markdown.plainText(sides.back),
        deckName = deckName,
        state = card.state,
        due = card.due,
        suspended = card.suspended,
        flagged = card.flagged,
        tags = note.tags,
    )
}
