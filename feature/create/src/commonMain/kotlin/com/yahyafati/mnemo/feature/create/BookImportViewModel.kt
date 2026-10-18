package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.domain.BookDeckNames
import com.yahyafati.mnemo.core.domain.BookDecks
import com.yahyafati.mnemo.core.domain.ChapterDeckRequest
import com.yahyafati.mnemo.core.domain.CreateBookDecksUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.ExtractAdvanced
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.SourceInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Imports a book (EPUB) as one deck per chapter (ADR 0011, B4): reads the file,
 * lets the user pick chapters and name the book's deck, and creates the decks, empty. Reading is
 * read-only; decks are made only by [BookImportAction.Create] or by confirming a book run
 * ([BookImportAction.ConfirmBatch], B6), which makes them and then hands the run to Smart Extract. Nothing is
 * sent to an AI provider from here: the confirmation only shows what Smart Extract would send, and asks for the card
 * settings the run starts with. [BookImportAction.OpenInSmartExtract] hands the book over without making any deck, with
 * a chapter's text in Smart Extract's box to be edited.
 *
 * [SavedStateHandle] key (from `BookImportRoute`): `location` is a file to read on arrival (a dropped
 * or opened one); without it the screen asks for a file.
 */
class BookImportViewModel(
    savedStateHandle: SavedStateHandle,
    private val sources: SourceRepository,
    private val deckRepository: DeckRepository,
    private val createBookDecks: CreateBookDecksUseCase,
    private val generateCards: GenerateCardsUseCase,
    private val aiProviders: AiProviderRepository,
    private val bookHandoff: BookHandoff,
    private val userSettings: UserSettingsRepository,
) : ViewModel() {
    private val initialLocation: String? = savedStateHandle[LOCATION_KEY]

    private val _uiState = MutableStateFlow(BookImportUiState(reading = initialLocation != null))
    val uiState: StateFlow<BookImportUiState> = _uiState.asStateFlow()

    private var decks: List<Deck> = emptyList()

    /** The user changed an Advanced option before the remembered ones arrived: theirs wins. */
    private var advancedChosen = false
    private var readJob: Job? = null

    private val runStarts = Channel<Unit>(Channel.CONFLATED)

    /** Emits once a confirmed book run was handed to Smart Extract, so the screen can leave for it. */
    val runStarted: Flow<Unit> = runStarts.receiveAsFlow()

    init {
        viewModelScope.launch {
            // The run starts from the remembered Advanced options (ADR 0015, C5): they decide whether a chapter is summarised.
            val remembered = userSettings.settings.first().extractAdvanced
            if (!advancedChosen) _uiState.update { it.copy(options = it.options.withAdvanced(remembered)) }
        }
        viewModelScope.launch {
            deckRepository.observeDecks().collect { live ->
                decks = live
                _uiState.update { it.withExisting() }
            }
        }
        viewModelScope.launch {
            aiProviders.observeEffectiveRoutes().collect { routes ->
                _uiState.update { it.copy(route = routes[AiTask.Extract]) }
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
            // After Create the decks exist, so Smart Extract finds the chapter's deck by name; without them it keeps its own.
            BookImportAction.GenerateCards, BookImportAction.OpenInSmartExtract -> _uiState.value.let { state ->
                state.book?.let { bookHandoff.offer(it, state.bookName, chapterId = state.chapterToOpen) }
            }
            BookImportAction.ShowBatch -> showBatch()
            BookImportAction.DismissBatch -> _uiState.update { it.copy(batchPlan = null) }
            is BookImportAction.SetDensity -> _uiState.update { it.copy(options = it.options.copy(density = action.density)) }
            is BookImportAction.ToggleArchetype -> _uiState.update {
                val archetypes = it.options.archetypes
                it.copy(options = it.options.copy(archetypes = if (action.archetype in archetypes) archetypes - action.archetype else archetypes + action.archetype))
            }
            BookImportAction.ToggleAdvanced -> _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
            is BookImportAction.SetLanguage -> updateAdvanced { it.copy(language = action.language) }
            is BookImportAction.SetSkipIllustrations -> updateAdvanced { it.copy(skipIllustrations = action.skip) }
            is BookImportAction.SetOverlap -> updateAdvanced { it.copy(overlap = action.overlap) }
            is BookImportAction.SetContextSummary -> updateAdvanced { it.copy(contextSummary = action.summary) }
            BookImportAction.ConfirmBatch -> confirmBatch()
            BookImportAction.Reset -> {
                readJob?.cancel()
                replace(BookImportUiState())
            }
        }
    }

    private fun read(location: String) {
        readJob?.cancel()
        replace(BookImportUiState(reading = true))
        readJob = viewModelScope.launch {
            when (val result = sources.readBook(SourceInput.Epub(location))) {
                is BookResult.Success -> {
                    val book = result.book.withDefaultTitles()
                    val state = BookImportUiState(
                        book = book,
                        wordCounts = book.chapters.associate { it.id to it.wordCount },
                        bookName = book.defaultName(),
                    )
                    replace(state.copy(checked = state.contentIds()).withExisting())
                }
                is BookResult.Failure -> replace(BookImportUiState(problem = result.problem))
            }
        }
    }

    /** Starts over with [state], keeping what doesn't come from the file: the provider route and the card settings. */
    private fun replace(state: BookImportUiState) {
        val current = _uiState.value
        _uiState.value = state.copy(route = current.route, options = current.options, showAdvanced = current.showAdvanced)
    }

    /** Changes one of the Advanced options and remembers the lot, as Smart Extract does (ADR 0015, C5). */
    private fun updateAdvanced(transform: (ExtractAdvanced) -> ExtractAdvanced) {
        advancedChosen = true
        _uiState.update { it.copy(options = it.options.withAdvanced(transform(it.options.advanced))) }
        val advanced = _uiState.value.options.advanced
        viewModelScope.launch { userSettings.setExtractAdvanced(advanced) }
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

    /** Works out what a run over the checked chapters would send, so the user can see it before anything is made or sent. */
    private fun showBatch() {
        val state = _uiState.value
        if (!state.canGenerate) return
        val chapters = state.chapters.filter { it.id in state.checked && it.text.isNotBlank() }
        _uiState.update {
            it.copy(
                batchPlan = BatchPlan(
                    chapterIds = chapters.map { chapter -> chapter.id },
                    words = chapters.sumOf { chapter -> state.wordCounts[chapter.id] ?: 0 },
                    // The requests follow from these and the dialog's settings: a chapter of three parts or more may also take the request that summarises it (ADR 0015, C4).
                    parts = chapters.map { chapter -> generateCards.split(chapter.text).size },
                ),
            )
        }
    }

    /** Creates the decks of the checked chapters, then offers the run to Smart Extract with those decks' ids. */
    private fun confirmBatch() {
        val state = _uiState.value
        val plan = state.batchPlan ?: return
        val book = state.book ?: return
        if (!state.canStartBatch || !state.canGenerate) return
        _uiState.update { it.copy(batchPlan = null) }
        createDecks(state) { made ->
            bookHandoff.offer(book, state.bookName, batch = plan.chapterIds, deckIds = made.chapterDeckIds, options = state.options)
            runStarts.trySend(Unit)
        }
    }

    private fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        createDecks(state)
    }

    /** Makes a deck for each checked chapter (those that have one are reused), then calls [onCreated]. */
    private fun createDecks(state: BookImportUiState, onCreated: (BookDecks) -> Unit = {}) {
        val book = state.book ?: return
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
                onCreated(made)
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
