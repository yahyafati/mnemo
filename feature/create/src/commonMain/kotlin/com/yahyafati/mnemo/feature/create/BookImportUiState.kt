package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.SourceProblem

/**
 * Importing a book (docs/epub/ROADMAP.md, B4): a file is read into chapters, the user picks the ones
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
) {
    val chapters: List<BookChapter> get() = book?.chapters.orEmpty()

    val selectedWords: Int get() = checked.sumOf { wordCounts[it] ?: 0 }

    /** Selected chapters that have no deck yet. */
    val newDecks: Int get() = checked.count { it !in existing }

    val canCreate: Boolean get() = book != null && checked.isNotEmpty() && bookName.isNotBlank() && !creating && created == null
}

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

    /** Back to asking for a file, from a problem or from the result. */
    data object Reset : BookImportAction
}
