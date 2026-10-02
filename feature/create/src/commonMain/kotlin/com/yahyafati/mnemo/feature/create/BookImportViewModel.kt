package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.domain.BookDeckNames
import com.yahyafati.mnemo.core.domain.ChapterDeckRequest
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.SourceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Imports a book (EPUB) as one deck per chapter (docs/epub/ROADMAP.md, B4, ADR 0011): reads the file,
 * lets the user pick chapters and name the book's deck, and creates the decks, empty. Reading is
 * read-only; decks are made only by [BookImportAction.Create].
 *
 * [SavedStateHandle] key (from `BookImportRoute`): `location` is a file to read on arrival (a dropped
 * or opened one); without it the screen asks for a file.
 */
class BookImportViewModel(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
    private val deckRepository: DeckRepository,
    private val createBookDecks: CreateBookDecksUseCase,
    private val bookHandoff: BookHandoff,
) : ViewModel() {
    private val initialLocation: String? = savedStateHandle[LOCATION_KEY]

    private val _uiState = MutableStateFlow(BookImportUiState(reading = initialLocation != null))
    val uiState: StateFlow<BookImportUiState> = _uiState.asStateFlow()

    private var decks: List<Deck> = emptyList()
    private var readJob: Job? = null

    init {
        viewModelScope.launch {
            deckRepository.observeDecks().collect { live ->
                decks = live
                _uiState.update { it.withExisting() }
            }
        }
        initialLocation?.let(::read)
    }

    fun onAction(action: BookImportAction) {
        when (action) {
            is BookImportAction.FilePicked -> read(action.location)
            is BookImportAction.ToggleChapter -> _uiState.update {
                it.copy(checked = if (action.id in it.checked) it.checked - action.id else it.checked + action.id)
            }
            BookImportAction.SelectAll -> _uiState.update { state -> state.copy(checked = state.chapters.mapTo(mutableSetOf()) { it.id }) }
            BookImportAction.SelectNone -> _uiState.update { it.copy(checked = emptySet()) }
            BookImportAction.SelectContent -> _uiState.update { it.copy(checked = it.contentIds()) }
            is BookImportAction.BookNameChanged -> _uiState.update { it.copy(bookName = action.name).withExisting() }
            BookImportAction.Create -> create()
            BookImportAction.GenerateCards -> _uiState.value.let { state ->
                // The decks exist now, so Smart Extract finds the chapter's deck by name.
                state.book?.let { bookHandoff.offer(it, state.bookName) }
            }
            BookImportAction.Reset -> {
                readJob?.cancel()
                _uiState.value = BookImportUiState()
            }
        }
    }

    private fun read(location: String) {
        readJob?.cancel()
        _uiState.value = BookImportUiState(reading = true)
        readJob = viewModelScope.launch {
            when (val result = sources.readBook(SourceInput.Epub(location))) {
                is BookResult.Success -> {
                    val book = result.book.withDefaultTitles()
                    val state = BookImportUiState(
                        book = book,
                        wordCounts = book.chapters.associate { it.id to it.wordCount },
                        bookName = book.defaultName(),
                    )
                    _uiState.value = state.copy(checked = state.contentIds()).withExisting()
                }
                is BookResult.Failure -> _uiState.value = BookImportUiState(problem = result.problem)
            }
        }
    }

    private fun BookImportUiState.contentIds(): Set<Int> = chapters.filter { it.kind == ChapterKind.Content }.mapTo(mutableSetOf()) { it.id }

    /** Marks the chapters whose deck exists under the book's deck, by computing the names the use case would give. */
    private fun BookImportUiState.withExisting(): BookImportUiState {
        val book = book ?: return this
        val root = decks.firstOrNull { it.parentId == null && it.name.equals(BookDeckNames.book(bookName), ignoreCase = true) }
            ?: return copy(existing = emptySet())
        val names = decks.filter { it.parentId == root.id }.mapTo(mutableSetOf()) { it.name.lowercase() }
        val count = book.deckCount()
        return copy(
            existing = book.chapters.filter { BookDeckNames.chapter(it.id + 1, count, it.title).lowercase() in names }.mapTo(mutableSetOf()) { it.id },
        )
    }

    private fun create() {
        val state = _uiState.value
        val book = state.book ?: return
        if (!state.canCreate) return
        val requests = book.chapters.filter { it.id in state.checked }.map { ChapterDeckRequest(it.id, it.title) }
        _uiState.update { it.copy(creating = true, createFailed = false) }
        viewModelScope.launch {
            try {
                val made = createBookDecks(state.bookName, requests, book.deckCount())
                _uiState.update {
                    it.copy(
                        creating = false,
                        created = BookImportResult(
                            bookDeck = BookDeckNames.book(state.bookName),
                            newDecks = made.createdChapterIds.size,
                            reusedDecks = made.chapterDeckIds.size - made.createdChapterIds.size,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing is rolled back; a retry creates only what is missing.
                _uiState.update { it.copy(creating = false, createFailed = true) }
            }
        }
    }

    companion object {
        const val LOCATION_KEY = "location"
    }
}
