package com.yahyafati.mnemo.feature.decks

import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.RetentionOverview
import com.yahyafati.mnemo.core.model.TodaySummary
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.ui.deck.DeckDraft
import java.time.Instant
import java.time.LocalDate

data class DecksUiState(
    val isLoading: Boolean = true,
    /** When this state was built; relative times ("2h ago") are measured from it. */
    val now: Instant = Instant.EPOCH,
    val date: LocalDate = LocalDate.ofEpochDay(0),
    val greeting: Greeting = Greeting.Morning,
    val today: TodaySummary = TodaySummary.Empty,
    /** The Retained and Mastered tiles. */
    val retention: RetentionOverview = RetentionOverview.Empty,
    /** Top-level decks after search and filter; subdecks hang off each item. */
    val decks: List<DeckItem> = emptyList(),
    val hasDecks: Boolean = false,
    val query: String = "",
    val filter: DeckFilter = DeckFilter.All,
    val filterCounts: FilterCounts = FilterCounts(),
    val categories: List<String> = emptyList(),
    val dialog: DecksDialog? = null,
    val importState: TransferState<ImportSummary> = TransferState.Idle,
    val exportState: TransferState<Unit> = TransferState.Idle,
)

enum class Greeting { Morning, Afternoon, Evening }

sealed interface DeckFilter {
    data object All : DeckFilter

    /** Decks with cards to study today (due or new). */
    data object Due : DeckFilter

    data object Starred : DeckFilter

    data class Category(val name: String) : DeckFilter
}

data class FilterCounts(val all: Int = 0, val due: Int = 0, val starred: Int = 0)

/** A deck as the list shows it. Counts include subdecks. */
data class DeckItem(
    val id: String,
    val name: String,
    val category: String?,
    val starred: Boolean,
    val dueCount: Int,
    val newCount: Int,
    val totalCount: Int,
    val lastReviewedAt: Instant?,
    val children: List<DeckItem>,
    val expanded: Boolean,
    /** Retention health: average current recall of its studied cards, or null with none. */
    val recall: Double? = null,
) {
    val hasCardsToStudy: Boolean get() = dueCount + newCount > 0

    /** All subdecks, depth-first, with their depth below this deck (1 = direct child). */
    fun descendants(depth: Int = 1): List<Pair<DeckItem, Int>> =
        children.flatMap { listOf(it to depth) + it.descendants(depth + 1) }
}

sealed interface DecksDialog {
    data object NewDeck : DecksDialog

    data class EditDeck(val deckId: String, val draft: DeckDraft) : DecksDialog

    data class ConfirmDelete(val deckId: String, val name: String, val cardCount: Int) : DecksDialog
}

sealed interface DecksAction {
    data class QueryChanged(val query: String) : DecksAction

    data class FilterSelected(val filter: DeckFilter) : DecksAction

    data class ToggleStar(val deckId: String) : DecksAction

    data class ToggleExpanded(val deckId: String) : DecksAction

    data object NewDeck : DecksAction

    data class EditDeck(val deckId: String) : DecksAction

    data class DeleteDeck(val deckId: String) : DecksAction

    data class SaveDeck(val draft: DeckDraft) : DecksAction

    data object ConfirmDelete : DecksAction

    data object DismissDialog : DecksAction

    /** Import the Anki package the user picked. */
    data class Import(val uri: String) : DecksAction

    /** Export [deckId] as an Anki package to the file the user picked. */
    data class Export(val deckId: String, val uri: String) : DecksAction

    /** Hide a finished import or export result. */
    data object DismissTransfer : DecksAction
}
