package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.ContextSummary
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.Overlap
import com.yahyafati.mnemo.core.model.SourceProblem

/**
 * Importing a book (ADR 0011, B4): a file is read into chapters, the user picks the ones
 * to make decks for, and the decks are created empty. Nothing is saved until [BookImportAction.Create].
 */
data class BookImportUiState(
    /** Waiting for a file to be read. */
    val reading: Boolean = false,
    val problem: SourceProblem? = null,
    val book: BookSource? = null,
    /** Words per chapter id, counted once when the book was read (counting is a pass over the text). */
    val wordCounts: Map<Int, Int> = emptyMap(),
    /** The name of the book's own deck, which the chapter decks go in. Editable. */
    val bookName: String = "",
    /** The ids of the chapters that will get a deck. */
    val checked: Set<Int> = emptySet(),
    /** Chapters that already have a deck under [bookName] (from an earlier import). */
    val existing: Set<Int> = emptySet(),
    val creating: Boolean = false,
    val createFailed: Boolean = false,
    val created: BookImportResult? = null,
    /** The provider and model Smart Extract would use for a book run; null when none is set up. */
    val route: AiRoute? = null,
    /** What "Create decks and generate" would send; set while the user is asked to confirm it. */
    val batchPlan: BatchPlan? = null,
    /**
     * The card settings a book run starts Smart Extract with, chosen in its dialog: card styles and density as Smart
     * Extract has them by default, the Advanced part as remembered (ADR 0015, C5).
     */
    val options: ExtractOptions = ExtractOptions(),
    /** Whether the dialog shows the Advanced options. */
    val showAdvanced: Boolean = false,
) {
    val chapters: List<BookChapter> get() = book?.chapters.orEmpty()

    val selectedWords: Int get() = checked.sumOf { wordCounts[it] ?: 0 }

    /** Selected chapters that have no deck yet. */
    val newDecks: Int get() = checked.count { it !in existing }

    val canCreate: Boolean get() = book != null && checked.isNotEmpty() && bookName.isNotBlank() && !creating && created == null

    /** Chapters without any text can't be generated from, so a selection of only those has nothing to run. */
    val canGenerate: Boolean get() = canCreate && selectedWords > 0

    /** A chapter to open in Smart Extract: the first checked one with text, else the first of the book's content with text. */
    val chapterToOpen: Int?
        get() = chapters.firstOrNull { it.id in checked && it.text.isNotBlank() }?.id
            ?: chapters.firstOrNull { it.kind == ChapterKind.Content && it.text.isNotBlank() }?.id
            ?: chapters.firstOrNull { it.text.isNotBlank() }?.id

    /** The AI requests the run would send with the chosen [options] (a summary's request depends on them, ADR 0015, C4). */
    val batchRequests: Int get() = batchPlan?.parts?.sumOf { options.requests(it) } ?: 0

    /** A run needs at least one card style, like Smart Extract's Generate. */
    val canStartBatch: Boolean get() = route != null && options.archetypes.isNotEmpty()
}

/**
 * What a book run (ADR 0011, B6) would send, shown before anything is: [chapterIds] in book order,
 * their [words], and how many [parts] [GenerateCardsUseCase.split] cuts each into (one request each, plus a summary's).
 */
data class BatchPlan(val chapterIds: List<Int>, val words: Int, val parts: List<Int>)

/** What creating the decks did. */
data class BookImportResult(
    /** The book's deck, as the deck list shows it. */
    val bookDeck: String,
    val newDecks: Int,
    /** Chapters that already had their deck, and were left as they were. */
    val reusedDecks: Int,
)

sealed interface BookImportAction {
    /** An EPUB was picked or dropped: read it. */
    data class FilePicked(val location: String) : BookImportAction

    data class ToggleChapter(val id: Int) : BookImportAction

    data object SelectAll : BookImportAction

    data object SelectNone : BookImportAction

    /** Only the chapters that are the book's content, not its front and back matter. */
    data object SelectContent : BookImportAction

    data class BookNameChanged(val name: String) : BookImportAction

    data object Create : BookImportAction

    /**
     * After the decks are made: hand the book to Smart Extract with the first chapter's text in the box and its deck
     * chosen, so the user can edit the text and settings before generating.
     */
    data object GenerateCards : BookImportAction

    /** Without making decks: hand the book to Smart Extract with [BookImportUiState.chapterToOpen]'s text in the box. */
    data object OpenInSmartExtract : BookImportAction

    /** "Create decks and generate": work out what a run would send and ask the user to confirm it. */
    data object ShowBatch : BookImportAction

    data object DismissBatch : BookImportAction

    /** The run's card settings, in its dialog. The Advanced ones are remembered, as in Smart Extract. */
    data class SetDensity(val density: ExtractDensity) : BookImportAction

    data class ToggleArchetype(val archetype: CardArchetype) : BookImportAction

    data object ToggleAdvanced : BookImportAction

    data class SetLanguage(val language: String?) : BookImportAction

    data class SetSkipIllustrations(val skip: Boolean) : BookImportAction

    data class SetOverlap(val overlap: Overlap) : BookImportAction

    data class SetContextSummary(val summary: ContextSummary) : BookImportAction

    /** Confirmed: create the decks, then hand the run to Smart Extract. */
    data object ConfirmBatch : BookImportAction

    /** Back to asking for a file, from a problem or from the result. */
    data object Reset : BookImportAction
}
