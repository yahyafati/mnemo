package com.yahyafati.mnemo.feature.create.coauthor

import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.DuplicateGroup
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.feature.create.DeckOption

data class CoAuthorUiState(
    val isLoading: Boolean = true,
    /** Where chat, suggestions and rewrites go; null shows the provider setup prompt. */
    val route: AiRoute? = null,
    val decks: List<DeckOption> = emptyList(),
    val deckId: String? = null,
    val messages: List<CoAuthorMessage> = emptyList(),
    val input: String = "",
    /** A request is running: chat, suggestions or rewrites. */
    val busy: Boolean = false,
    /** The provider notice to confirm before the first request to [AiRoute.provider]. */
    val disclosure: AiRoute? = null,
    val notice: CoAuthorNotice? = null,
) {
    val canSend: Boolean get() = route != null && deckId != null && !busy && input.isNotBlank()

    /** The AI actions: a provider, a deck and nothing running. */
    val canAsk: Boolean get() = route != null && deckId != null && !busy

    /** Finding duplicates is done on the device, so it needs no provider. */
    val canFindDuplicates: Boolean get() = deckId != null && !busy
}

/** A short confirmation shown as a snackbar. */
sealed interface CoAuthorNotice {
    data class Added(val cards: Int) : CoAuthorNotice

    data object Deleted : CoAuthorNotice

    data object Applied : CoAuthorNotice
}

/** One entry in the conversation. Actions carry their results, which the user acts on in place. */
sealed interface CoAuthorMessage {
    val id: String

    data class User(override val id: String, val text: String) : CoAuthorMessage

    /** A chat answer; [done] once complete. [failure] is set if it stopped early or never started. */
    data class Reply(override val id: String, val text: String, val done: Boolean = false, val failure: AiFailure? = null) : CoAuthorMessage

    /** "Suggest missing cards": proposals the user adds one by one or all at once. */
    data class Suggestions(
        override val id: String,
        val focus: String?,
        val cards: List<SuggestedCard> = emptyList(),
        val done: Boolean = false,
        val failure: AiFailure? = null,
    ) : CoAuthorMessage {
        val pending: List<SuggestedCard> get() = cards.filter { it.status == SuggestionStatus.Pending }
    }

    /** "Find duplicates": groups of notes asking the same thing; [deleted] note ids are gone. */
    data class Duplicates(
        override val id: String,
        val groups: List<DuplicateGroup>,
        val deleted: Set<String> = emptySet(),
    ) : CoAuthorMessage

    /** "Improve weak cards": the most-forgotten cards, each with a proposed rewrite. */
    data class WeakCards(
        override val id: String,
        val items: List<WeakCardItem>,
        val done: Boolean = false,
    ) : CoAuthorMessage
}

enum class SuggestionStatus { Pending, Added, Discarded }

data class SuggestedCard(val card: GeneratedCard, val status: SuggestionStatus = SuggestionStatus.Pending)

data class WeakCardItem(val card: StudyCard, val state: WeakCardState = WeakCardState.Waiting)

sealed interface WeakCardState {
    /** Queued behind another card's request. */
    data object Waiting : WeakCardState

    data object Loading : WeakCardState

    /** [applicable] is false when the rewrite can't be saved (a side left empty, cloze numbers changed). */
    data class Proposed(val fields: List<String>, val applicable: Boolean) : WeakCardState

    data object Applied : WeakCardState

    data object Dismissed : WeakCardState

    data class Failed(val failure: AiFailure) : WeakCardState
}

sealed interface CoAuthorAction {
    data class SelectDeck(val deckId: String) : CoAuthorAction

    data class InputChanged(val text: String) : CoAuthorAction

    /** Send the input as a chat message. */
    data object Send : CoAuthorAction

    /** Suggest cards the deck is missing; the input, if any, says what to focus on. */
    data object SuggestCards : CoAuthorAction

    data object FindDuplicates : CoAuthorAction

    data object ImproveWeakCards : CoAuthorAction

    /** Stop the request that is running. */
    data object Stop : CoAuthorAction

    data class AddSuggestion(val messageId: String, val cardId: String) : CoAuthorAction

    data class DiscardSuggestion(val messageId: String, val cardId: String) : CoAuthorAction

    data class AddAllSuggestions(val messageId: String) : CoAuthorAction

    data class DeleteNote(val messageId: String, val noteId: String) : CoAuthorAction

    data class ApplyRewrite(val messageId: String, val cardId: String) : CoAuthorAction

    data class DismissRewrite(val messageId: String, val cardId: String) : CoAuthorAction

    data object AcceptDisclosure : CoAuthorAction

    data object DismissDisclosure : CoAuthorAction

    data object NoticeShown : CoAuthorAction

    data object ClearConversation : CoAuthorAction
}
