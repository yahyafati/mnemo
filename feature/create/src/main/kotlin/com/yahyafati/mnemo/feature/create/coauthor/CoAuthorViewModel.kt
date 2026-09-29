package com.yahyafati.mnemo.feature.create.coauthor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.CoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.DeckNode
import com.yahyafati.mnemo.core.domain.FindDuplicateNotesUseCase
import com.yahyafati.mnemo.core.domain.GeneratedCardValidator
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.Cloze
import com.yahyafati.mnemo.core.model.CoAuthorDeck
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.feature.create.DeckOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * AI Co-Author (PROJECT_OVERVIEW §4.3): a chat scoped to one deck (and its subdecks) that answers
 * questions, suggests missing cards, finds duplicates and improves the cards the learner keeps
 * forgetting (lapse data, ADR 0007). Suggestions and rewrites are proposals: nothing changes until
 * the user adds or applies one. Duplicates are found on the device.
 *
 * `deckId` in the [SavedStateHandle] preselects a deck.
 */
@HiltViewModel
class CoAuthorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aiProviders: AiProviderRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val coAuthor: CoAuthorRepository,
    private val findDuplicates: FindDuplicateNotesUseCase,
    private val acceptCards: AcceptGeneratedCardsUseCase,
) : ViewModel() {
    private val initialDeckId: String? = savedStateHandle[DECK_ID_KEY]

    private val _uiState = MutableStateFlow(CoAuthorUiState())
    val uiState: StateFlow<CoAuthorUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var afterDisclosure: (() -> Unit)? = null
    private val disclosed = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(aiProviders.observeEffectiveRoutes(), deckRepository.observeDeckSummaries()) { routes, decks ->
                routes[AiTask.CoAuthor] to decks.map { DeckOption(it.deck.id, it.path) }.sortedBy { it.path.lowercase() }
            }.collect { (route, decks) ->
                _uiState.update { state ->
                    val current = state.deckId?.takeIf { id -> decks.any { it.id == id } }
                    val preferred = initialDeckId?.takeIf { id -> decks.any { it.id == id } }
                    state.copy(isLoading = false, route = route, decks = decks, deckId = current ?: preferred ?: decks.firstOrNull()?.id)
                }
            }
        }
    }

    fun onAction(action: CoAuthorAction) {
        when (action) {
            is CoAuthorAction.SelectDeck -> if (action.deckId != _uiState.value.deckId) {
                // A new deck starts a new conversation: the old one was about other cards.
                job?.cancel()
                _uiState.update { it.copy(deckId = action.deckId, messages = emptyList(), busy = false) }
            }
            is CoAuthorAction.InputChanged -> _uiState.update { it.copy(input = action.text) }
            CoAuthorAction.Send -> send()
            CoAuthorAction.SuggestCards -> suggest()
            CoAuthorAction.FindDuplicates -> duplicates()
            CoAuthorAction.ImproveWeakCards -> improveWeakCards()
            CoAuthorAction.Stop -> stop()
            is CoAuthorAction.AddSuggestion -> addSuggestions(action.messageId, setOf(action.cardId))
            is CoAuthorAction.AddAllSuggestions -> {
                val message = message<CoAuthorMessage.Suggestions>(action.messageId) ?: return
                addSuggestions(action.messageId, message.pending.map { it.card.id }.toSet())
            }
            is CoAuthorAction.DiscardSuggestion -> updateMessage<CoAuthorMessage.Suggestions>(action.messageId) { message ->
                message.copy(cards = message.cards.map { if (it.card.id == action.cardId) it.copy(status = SuggestionStatus.Discarded) else it })
            }
            is CoAuthorAction.DeleteNote -> deleteNote(action.messageId, action.noteId)
            is CoAuthorAction.ApplyRewrite -> applyRewrite(action.messageId, action.cardId)
            is CoAuthorAction.DismissRewrite -> setWeakState(action.messageId, action.cardId, WeakCardState.Dismissed)
            CoAuthorAction.AcceptDisclosure -> {
                val route = _uiState.value.disclosure ?: return
                disclosed += route.provider.id
                _uiState.update { it.copy(disclosure = null) }
                viewModelScope.launch { aiProviders.acceptDisclosure(route.provider.id) }
                afterDisclosure?.invoke()
                afterDisclosure = null
            }
            CoAuthorAction.DismissDisclosure -> {
                afterDisclosure = null
                _uiState.update { it.copy(disclosure = null) }
            }
            CoAuthorAction.NoticeShown -> _uiState.update { it.copy(notice = null) }
            CoAuthorAction.ClearConversation -> {
                job?.cancel()
                _uiState.update { it.copy(messages = emptyList(), busy = false) }
            }
        }
    }

    private fun send() {
        val state = _uiState.value
        val route = state.route ?: return
        if (!state.canSend) return
        val text = state.input.trim()
        withDisclosure(route) {
            val replyId = newId()
            val history = _uiState.value.messages.mapNotNull { message ->
                when (message) {
                    is CoAuthorMessage.User -> ChatTurn(true, message.text)
                    is CoAuthorMessage.Reply -> ChatTurn(false, message.text).takeIf { message.done && message.failure == null && message.text.isNotBlank() }
                    else -> null
                }
            } + ChatTurn(true, text)
            _uiState.update {
                it.copy(
                    input = "",
                    busy = true,
                    messages = it.messages + CoAuthorMessage.User(newId(), text) + CoAuthorMessage.Reply(replyId, ""),
                )
            }
            request {
                val deck = deck() ?: return@request
                coAuthor.chat(route, deck, history).collect { update ->
                    when (update) {
                        is AssistUpdate.Text -> updateMessage<CoAuthorMessage.Reply>(replyId) { it.copy(text = it.text + update.delta) }
                        is AssistUpdate.Done -> updateMessage<CoAuthorMessage.Reply>(replyId) { it.copy(done = true, failure = update.failure) }
                    }
                }
            }
        }
    }

    private fun suggest() {
        val state = _uiState.value
        val route = state.route ?: return
        if (!state.canAsk) return
        val focus = state.input.trim().ifEmpty { null }
        withDisclosure(route) {
            val id = newId()
            _uiState.update { it.copy(input = "", busy = true, messages = it.messages + CoAuthorMessage.Suggestions(id, focus)) }
            request {
                val deck = deck() ?: return@request
                val known = deck.notes.map { GeneratedCardValidator.key(it.field(0)) }.toMutableSet()
                coAuthor.suggest(route, deck, focus).collect { update ->
                    when (update) {
                        is GenerationUpdate.Card -> {
                            // Only valid cards the deck (or this list) doesn't have yet.
                            val card = GeneratedCardValidator.validate(update.card) ?: return@collect
                            if (!known.add(GeneratedCardValidator.key(card.front))) return@collect
                            updateMessage<CoAuthorMessage.Suggestions>(id) { it.copy(cards = it.cards + SuggestedCard(card)) }
                        }
                        is GenerationUpdate.Done -> updateMessage<CoAuthorMessage.Suggestions>(id) { it.copy(done = true, failure = update.failure) }
                    }
                }
            }
        }
    }

    private fun duplicates() {
        val deckId = _uiState.value.deckId ?: return
        if (!_uiState.value.canFindDuplicates) return
        _uiState.update { it.copy(busy = true) }
        request {
            val groups = findDuplicates(deckId)
            _uiState.update { it.copy(messages = it.messages + CoAuthorMessage.Duplicates(newId(), groups)) }
        }
    }

    private fun improveWeakCards() {
        val state = _uiState.value
        val route = state.route ?: return
        if (!state.canAsk) return
        withDisclosure(route) {
            val id = newId()
            _uiState.update { it.copy(busy = true) }
            request {
                val deckId = _uiState.value.deckId ?: return@request
                val ids = DeckNode.subtreeIds(deckRepository.getDecks(), deckId)
                val weak = cardRepository.getMostLapsed(ids, MIN_LAPSES, MAX_WEAK_CARDS)
                    .distinctBy { it.note.id } // one rewrite per note, even if several of its cards are weak
                _uiState.update { it.copy(messages = it.messages + CoAuthorMessage.WeakCards(id, weak.map { card -> WeakCardItem(card) })) }
                for (card in weak) {
                    setWeakState(id, card.card.id, WeakCardState.Loading)
                    val state = when (val outcome = coAuthor.improve(route, card)) {
                        is RewriteOutcome.Proposed -> WeakCardState.Proposed(outcome.fields, applicable(card, outcome.fields))
                        is RewriteOutcome.Failed -> WeakCardState.Failed(outcome.failure)
                    }
                    setWeakState(id, card.card.id, state)
                }
                updateMessage<CoAuthorMessage.WeakCards>(id) { it.copy(done = true) }
            }
        }
    }

    private fun stop() {
        job?.cancel()
        _uiState.update { state ->
            state.copy(
                busy = false,
                messages = state.messages.map { message ->
                    when (message) {
                        is CoAuthorMessage.Reply -> message.copy(done = true)
                        is CoAuthorMessage.Suggestions -> message.copy(done = true)
                        is CoAuthorMessage.WeakCards -> message.copy(
                            done = true,
                            items = message.items.map { item ->
                                if (item.state == WeakCardState.Loading || item.state == WeakCardState.Waiting) item.copy(state = WeakCardState.Dismissed) else item
                            },
                        )
                        else -> message
                    }
                },
            )
        }
    }

    private fun addSuggestions(messageId: String, cardIds: Set<String>) {
        val deckId = _uiState.value.deckId ?: return
        val message = message<CoAuthorMessage.Suggestions>(messageId) ?: return
        val cards = message.pending.filter { it.card.id in cardIds }.map { it.card }
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val result = acceptCards(deckId, cards)
            val added = result.acceptedIds.toSet()
            updateMessage<CoAuthorMessage.Suggestions>(messageId) { current ->
                current.copy(cards = current.cards.map { if (it.card.id in added) it.copy(status = SuggestionStatus.Added) else it })
            }
            if (result.cardCount > 0) _uiState.update { it.copy(notice = CoAuthorNotice.Added(result.cardCount)) }
        }
    }

    private fun deleteNote(messageId: String, noteId: String) {
        viewModelScope.launch {
            cardRepository.deleteNote(noteId)
            updateMessage<CoAuthorMessage.Duplicates>(messageId) { it.copy(deleted = it.deleted + noteId) }
            _uiState.update { it.copy(notice = CoAuthorNotice.Deleted) }
        }
    }

    private fun applyRewrite(messageId: String, cardId: String) {
        val item = message<CoAuthorMessage.WeakCards>(messageId)?.items?.firstOrNull { it.card.card.id == cardId } ?: return
        val proposal = item.state as? WeakCardState.Proposed ?: return
        if (!proposal.applicable) return
        val note = item.card.note
        viewModelScope.launch {
            cardRepository.updateNote(note.id, note.deckId, proposal.fields.map { it.trim() }, note.tags, note.hint)
            setWeakState(messageId, cardId, WeakCardState.Applied)
            _uiState.update { it.copy(notice = CoAuthorNotice.Applied) }
        }
    }

    /** The deck to send: the selected one and its subdecks, by its full name. */
    private suspend fun deck(): CoAuthorDeck? {
        val deckId = _uiState.value.deckId ?: return null
        val decks = deckRepository.getDecks()
        val ids = DeckNode.subtreeIds(decks, deckId)
        val name = _uiState.value.decks.firstOrNull { it.id == deckId }?.path ?: decks.firstOrNull { it.id == deckId }?.name.orEmpty()
        return CoAuthorDeck(name, cardRepository.getNotesInDecks(ids, MAX_NOTES_READ))
    }

    /** Runs [block] as the one request in flight; [CoAuthorUiState.busy] until it ends. */
    private fun request(block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun withDisclosure(route: AiRoute, action: () -> Unit) {
        if (route.provider.disclosureAcceptedAt != null || route.provider.id in disclosed) {
            action()
        } else {
            afterDisclosure = action
            _uiState.update { it.copy(disclosure = route) }
        }
    }

    private fun setWeakState(messageId: String, cardId: String, state: WeakCardState) = updateMessage<CoAuthorMessage.WeakCards>(messageId) { message ->
        message.copy(items = message.items.map { if (it.card.card.id == cardId) it.copy(state = state) else it })
    }

    private inline fun <reified M : CoAuthorMessage> message(id: String): M? = _uiState.value.messages.firstOrNull { it.id == id } as? M

    private inline fun <reified M : CoAuthorMessage> updateMessage(id: String, crossinline transform: (M) -> M) = _uiState.update { state ->
        state.copy(messages = state.messages.map { if (it.id == id && it is M) transform(it) else it })
    }

    internal companion object {
        const val DECK_ID_KEY = "deckId"

        /** A card counts as weak from this many lapses. */
        const val MIN_LAPSES = 3

        /** Weak cards rewritten per request, most-forgotten first. */
        const val MAX_WEAK_CARDS = 5

        /** Notes read for the deck context and duplicates; the prompt samples fewer. */
        const val MAX_NOTES_READ = 5_000

        private fun newId() = UUID.randomUUID().toString()

        /** A rewrite can be saved if no side is empty and a cloze note keeps its numbers (its cards and their history). */
        fun applicable(card: StudyCard, fields: List<String>): Boolean {
            val front = fields.getOrElse(0) { "" }
            val back = fields.getOrElse(1) { "" }
            return when {
                front.isBlank() -> false
                card.kind == NoteKind.Cloze -> Cloze.ordinals(front) == Cloze.ordinals(card.note.field(0))
                else -> back.isNotBlank()
            }
        }
    }
}
