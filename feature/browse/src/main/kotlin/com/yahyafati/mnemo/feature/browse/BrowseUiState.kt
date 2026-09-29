package com.yahyafati.mnemo.feature.browse

import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.CardSort
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.CardStatus
import java.time.Instant

data class BrowseUiState(
    val query: CardQuery = CardQuery(),
    /** How many cards match [query]. */
    val matchCount: Int = 0,
    val decks: List<DeckOption> = emptyList(),
    val tags: List<String> = emptyList(),
    /** Selected card ids; non-empty means selection mode. */
    val selection: Set<String> = emptySet(),
    val dialog: BrowseDialog? = null,
    /** A one-off confirmation after a bulk edit, shown as a snackbar. */
    val message: BrowseMessage? = null,
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val deckName: String? get() = decks.firstOrNull { it.id == query.deckId }?.path
}

data class DeckOption(val id: String, val path: String)

/** A card as a browser row shows it. */
data class BrowseItem(
    val cardId: String,
    val noteId: String,
    /** Plain text of the card's question and answer. */
    val front: String,
    val back: String,
    val deckName: String,
    val state: CardState,
    val due: Instant,
    val suspended: Boolean,
    val flagged: Boolean,
    val tags: List<String>,
)

sealed interface BrowseDialog {
    data object Move : BrowseDialog

    data object AddTag : BrowseDialog

    data object RemoveTag : BrowseDialog

    data class ConfirmDelete(val cardCount: Int) : BrowseDialog
}

sealed interface BrowseMessage {
    data class Suspended(val count: Int, val suspended: Boolean) : BrowseMessage

    data class Flagged(val count: Int, val flagged: Boolean) : BrowseMessage

    data class Moved(val count: Int, val deck: String) : BrowseMessage

    data class Tagged(val count: Int, val tag: String, val added: Boolean) : BrowseMessage

    data class Deleted(val count: Int) : BrowseMessage
}

sealed interface BrowseAction {
    data class TextChanged(val text: String) : BrowseAction

    data class DeckSelected(val deckId: String?) : BrowseAction

    data class TagSelected(val tag: String?) : BrowseAction

    data class StatusSelected(val status: CardStatus?) : BrowseAction

    data class SortSelected(val sort: CardSort) : BrowseAction

    data class ToggleSelected(val cardId: String) : BrowseAction

    data object SelectAll : BrowseAction

    data object ClearSelection : BrowseAction

    data class Suspend(val suspended: Boolean) : BrowseAction

    data class Flag(val flagged: Boolean) : BrowseAction

    data object ShowMove : BrowseAction

    data class Move(val deckId: String) : BrowseAction

    data object ShowAddTag : BrowseAction

    data class AddTag(val tag: String) : BrowseAction

    data object ShowRemoveTag : BrowseAction

    data class RemoveTag(val tag: String) : BrowseAction

    data object ShowDelete : BrowseAction

    data object ConfirmDelete : BrowseAction

    data object DismissDialog : BrowseAction

    data object MessageShown : BrowseAction
}
