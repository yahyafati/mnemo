package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.BookDeckNames
import com.yahyafati.mnemo.core.domain.ExtractEvent
import com.yahyafati.mnemo.core.domain.ExtractRequest
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.PageSource
import com.yahyafati.mnemo.core.domain.PageTranscriptionCache
import com.yahyafati.mnemo.core.domain.PdfReadPlan
import com.yahyafati.mnemo.core.domain.PdfReadPlanResult
import com.yahyafati.mnemo.core.domain.ReadPagesEvent
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.RegenerateResult
import com.yahyafati.mnemo.core.domain.SkipReason
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.BookChapter
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.BookSource
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.DisclosureStep
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.PageLabels
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PageRangesResult
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageText
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceSections
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.model.pendingDisclosures
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Smart Extract (ARCHITECTURE §5.2): source → text → streamed cards in a review queue → accepted
 * notes. Nothing is saved until a card is accepted; cards that arrived before a failure stay in
 * the queue, and Retry resumes at the part that failed.
 *
 * A link whose page has sections (a Wikipedia article's, docs/web/ROADMAP.md, W4) fills the box with the chosen ones:
 * all of them, or the one a `#Fragment` in the link names. Choosing others rewrites the box, after asking if the user
 * has edited it. The page's text and sections stay here, not in the UI state.
 *
 * A PDF (docs/pdf/ROADMAP.md, P1) is opened, not just read: the repository keeps a copy and the Pages field chooses
 * which of its pages the box holds, so any 300 pages of a long PDF can be used. Applying other pages rewrites the box,
 * after asking if the user has edited it. The open PDF is closed when another source replaces it or the text is
 * cleared; a copy that outlives the ViewModel is deleted by the repository when it is a day old.
 *
 * Pages can be read three ways (P5): **Text** (the text layer, free), **Auto** (the layer where a page has one, a vision
 * model's transcription of the page where it has none) and **Read pages with AI**. The AI modes first plan which pages
 * need a request, ask the user to agree to that number, then show the text growing in the box page by page; a failure
 * keeps the pages done and Resume goes on from the page that failed. Transcriptions are kept for the session
 * ([PageTranscriptionCache]), so changing the pages or the mode doesn't pay twice.
 *
 * The Epub source (docs/epub/ROADMAP.md, B5) reads a book into chapters, kept here and not in the UI
 * state, and puts one chapter's text in the box like a PDF's. When the book's chapter decks exist
 * (made by the book import) the chapter's deck is selected, found by computing its name.
 *
 * A **book run** (B6) goes through several chapters in book order. It is this same screen state: the chapter's
 * text in the box, its deck chosen, Generate, one review queue. It moves on only when the user says so
 * ([SmartExtractAction.BatchNext]), never by itself, so a failure (a rate limit, a bad key) or a review that isn't
 * finished leaves it where it is, and Retry resumes at the part that failed. Like the queue, the run lives in
 * this ViewModel: it survives rotation, not the process ending (the book is in memory only, ADR 0011).
 */
class SmartExtractViewModel(
    private val aiProviders: AiProviderRepository,
    private val deckRepository: DeckRepository,
    private val sources: SourceRepository,
    private val generateCards: GenerateCardsUseCase,
    private val regenerateCard: RegenerateCardUseCase,
    private val acceptCards: AcceptGeneratedCardsUseCase,
    private val bookHandoff: BookHandoff,
    private val userSettings: UserSettingsRepository,
    private val readPdfPages: ReadPdfPagesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SmartExtractUiState(dictationAvailable = sources.isDictationAvailable()))
    val uiState: StateFlow<SmartExtractUiState> = _uiState.asStateFlow()

    /** The source being generated from, kept so Retry can resume it. */
    private class Run(val parts: List<String>, val options: ExtractOptions, val title: String?, val deckId: String?)

    private var run: Run? = null
    private var generationJob: Job? = null
    private var dictationJob: Job? = null
    private var readJob: Job? = null

    /** The open PDF, and the text its pages last gave the box (to tell the user's edits from it). */
    private var pdf: PdfHandle? = null
    private var pdfText: String? = null

    /** What the AI modes transcribed in this session, and the read waiting for the user's agreement or going on. */
    private val transcriptions = PageTranscriptionCache()

    private class PdfRun(val route: AiRoute, val plan: PdfReadPlan, var failedAt: Int = 0)

    private var pdfRun: PdfRun? = null

    /** The book whose chapters can be chosen, and the name its deck has (or would have) under the book's root. */
    private var book: BookSource? = null
    private var bookName: String = ""

    /** The page whose sections can be chosen, and the text the box was last given from them (to tell edits from it). */
    private var sectionSource: SourceText? = null
    private var sectionText: String? = null

    /** The sections whose text is in the box. */
    private var selectedSections: Set<Int> = emptySet()

    /** The selection waiting for the user to agree to replace their edits. */
    private var pendingSections: Set<Int>? = null

    /** The deck path of the chosen chapter, until the decks list has it (the first emission may come later). */
    private var preferredDeckPath: String? = null

    /** The run waiting for the user to agree to discard the queue before it starts. */
    private class PendingBatch(val chapterIds: List<Int>, val deckIds: Map<Int, String>)

    private var pendingBatch: PendingBatch? = null

    /** The decks the book import made for the run's chapters, by chapter id. */
    private var batchDecks: Map<Int, String> = emptyMap()

    /** The cards waiting for the user to agree to add them to a deck that already has some. */
    private var pendingAccept: List<String>? = null

    /** Decks the user agreed to add to, or that this session already added to: they aren't asked about again. */
    private val confirmedDecks = mutableSetOf<String>()

    /** A deck known to exist that the decks list may not have yet (the run's first chapter, made a moment ago). */
    private var pinnedDeckId: String? = null

    private val adoptedBooks = Channel<Unit>(Channel.CONFLATED)

    /** Emits when a book arrives from the book import, so the screen can show Smart Extract. */
    val bookAdopted: Flow<Unit> = adoptedBooks.receiveAsFlow()

    /** What to do once the provider notice is accepted. */
    private var afterDisclosure: (() -> Unit)? = null

    /** Providers whose notice was accepted here; the repository's flow may not have caught up yet. */
    private val disclosed = mutableSetOf<String>()

    /** The same for the notice about page images, and the notices still to show after the one on screen. */
    private val imageDisclosed = mutableSetOf<String>()
    private var disclosureSteps: List<DisclosureStep> = emptyList()

    init {
        viewModelScope.launch {
            combine(aiProviders.observeEffectiveRoutes(), deckRepository.observeDeckSummaries()) { routes, decks ->
                Triple(routes[AiTask.Extract], routes[AiTask.ReadPages], decks.map { DeckOption(it.deck.id, it.path, it.totalCount) }.sortedBy { it.path.lowercase() })
            }.collect { (route, readRoute, decks) ->
                val preferred = preferredDeck(decks)
                val pinned = pinnedDeckId
                if (pinned != null && decks.any { it.id == pinned }) pinnedDeckId = null
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        route = route,
                        readRoute = readRoute,
                        decks = decks,
                        deckId = preferred?.id
                            ?: state.deckId?.takeIf { id -> id == pinned || decks.any { it.id == id } }
                            ?: decks.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            bookHandoff.offer.filterNotNull().collect { offer ->
                bookHandoff.take(offer)
                adopt(offer.book, offer.bookName, offer.chapterId, openChapters = offer.chapterId == null && offer.batch.isEmpty())
                if (offer.batch.isNotEmpty()) {
                    // A run generates at once, which needs the provider route: wait for the first load when this
                    // ViewModel was made by the handoff itself.
                    _uiState.first { !it.isLoading }
                    startBatch(offer.batch, offer.deckIds)
                }
            }
        }
    }

    fun onAction(action: SmartExtractAction) {
        when (action) {
            is SmartExtractAction.SelectSource -> {
                if (action.kind != SourceKind.Dictation) stopDictation()
                _uiState.update { it.copy(sourceKind = action.kind, sourceProblem = null) }
            }
            is SmartExtractAction.TextChanged -> _uiState.update {
                if (action.text.isBlank()) it.withText(action.text).copy(title = null, truncated = false, disambiguation = false) else it.withText(action.text)
            }
            SmartExtractAction.ClearText -> {
                endBatch()
                clearSections()
                // An AI read of PDF pages would go on filling the box that was just emptied.
                val aiReading = _uiState.value.pdf?.readState != null
                if (aiReading) readJob?.cancel()
                releasePdf()
                _uiState.update {
                    it.withText("").copy(title = null, truncated = false, disambiguation = false, chapterId = null, sourceProblem = null, reading = it.reading && !aiReading)
                }
            }
            is SmartExtractAction.LinkChanged -> _uiState.update { it.copy(link = action.link, sourceProblem = null) }
            SmartExtractAction.FetchLink -> _uiState.value.link.takeIf { it.isNotBlank() }?.let { read(SourceInput.Link(it.trim())) }
            is SmartExtractAction.PdfPicked -> openPdf(action.uri)
            is SmartExtractAction.PdfPagesChanged -> pdf?.let { handle ->
                _uiState.update { state ->
                    state.pdf?.let { state.copy(pdf = it.copy(pages = action.text, error = choosePages(action.text, handle.info.pageCount).second)) } ?: state
                }
            }
            SmartExtractAction.ApplyPdfPages -> applyPdfPages(confirmed = false)
            is SmartExtractAction.SetPdfReadMode -> setPdfReadMode(action.mode)
            is SmartExtractAction.SetPdfQuality -> setPdfQuality(action.quality)
            SmartExtractAction.ReadPdfPagesWithAi -> {
                setPdfReadMode(PdfReadMode.Auto)
                applyPdfPages(confirmed = false)
            }
            SmartExtractAction.ConfirmPdfRead -> confirmPdfRead()
            SmartExtractAction.DismissPdfReadConfirmation -> {
                pdfRun = null
                _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(readConfirmation = null)) } ?: state }
            }
            SmartExtractAction.CancelPdfRead -> cancelPdfRead()
            SmartExtractAction.ResumePdfRead -> resumePdfRead()
            SmartExtractAction.ConfirmPdfReplace -> applyPdfPages(confirmed = true)
            SmartExtractAction.CancelPdfReplace -> _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(replaceConfirmation = false)) } ?: state }
            SmartExtractAction.ShowPdfChapters -> pdf?.let { handle ->
                _uiState.update { state ->
                    state.pdf?.let { state.copy(pdf = it.copy(showChapters = true, selectedChapters = chaptersIn(it.pages, handle.info))) } ?: state
                }
            }
            is SmartExtractAction.TogglePdfChapter -> pdf?.let { toggleChapter(it, action.id) }
            SmartExtractAction.DismissPdfChapters -> _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(showChapters = false)) } ?: state }
            SmartExtractAction.ApplyPdfChapters -> {
                _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(showChapters = false)) } ?: state }
                applyPdfPages(confirmed = false)
            }
            is SmartExtractAction.EpubPicked -> readBook(action.uri)
            is SmartExtractAction.SelectChapter -> {
                endBatch()
                selectChapter(action.id)
            }
            SmartExtractAction.BatchNext -> nextChapter(confirmed = false)
            SmartExtractAction.BatchStop -> {
                generationJob?.cancel()
                endBatch()
                _uiState.update { if (it.isGenerating) it.copy(generation = GenerationState.Idle) else it }
            }
            SmartExtractAction.ConfirmBatchDiscard -> when (_uiState.value.batchConfirmation) {
                BatchConfirmation.Start -> pendingBatch?.let(::beginBatch)
                BatchConfirmation.Advance -> nextChapter(confirmed = true)
                null -> Unit
            }
            SmartExtractAction.CancelBatchDiscard -> {
                pendingBatch = null
                _uiState.update { it.copy(batchConfirmation = null) }
            }
            SmartExtractAction.ShowSections -> _uiState.update { if (it.sections != null) it.copy(showSections = true) else it }
            SmartExtractAction.DismissSections -> {
                pendingSections = null
                _uiState.update { it.copy(showSections = false, sectionsConfirmation = false) }
            }
            is SmartExtractAction.ToggleSection -> _uiState.value.sections?.let { sections ->
                chooseSections(if (action.id in sections.selected) sections.selected - action.id else sections.selected + action.id)
            }
            is SmartExtractAction.SelectAllSections -> _uiState.value.sections?.let { sections ->
                chooseSections(if (action.all) sections.options.map { it.id }.toSet() else emptySet())
            }
            SmartExtractAction.ConfirmSectionReplace -> pendingSections?.let(::applySections)
            SmartExtractAction.CancelSectionReplace -> {
                pendingSections = null
                _uiState.update { it.copy(sectionsConfirmation = false) }
            }
            SmartExtractAction.ShowChapters -> _uiState.update { if (it.book != null) it.copy(showChapters = true) else it }
            SmartExtractAction.DismissChapters -> _uiState.update { it.copy(showChapters = false) }
            is SmartExtractAction.FileDropped -> when (DroppedFile.of(action.location)) {
                DroppedFile.Pdf -> {
                    _uiState.update { it.copy(sourceKind = SourceKind.Pdf) }
                    openPdf(action.location)
                }
                DroppedFile.Text -> {
                    _uiState.update { it.copy(sourceKind = SourceKind.Paste) }
                    read(SourceInput.TextFile(action.location))
                }
                null -> Unit
            }
            SmartExtractAction.StartDictation -> startDictation()
            SmartExtractAction.StopDictation -> stopDictation()
            SmartExtractAction.DictationPermissionDenied ->
                _uiState.update { it.copy(dictation = DictationState.Failed(DictationProblem.NoPermission)) }
            is SmartExtractAction.SelectDeck -> {
                pendingAccept = null
                preferredDeckPath = null
                pinnedDeckId = null
                _uiState.update { it.copy(deckId = action.deckId, nonEmptyDeck = null) }
            }
            SmartExtractAction.ShowDeckDialog -> _uiState.update { it.copy(showDeckDialog = true) }
            SmartExtractAction.DismissDeckDialog -> _uiState.update { it.copy(showDeckDialog = false) }
            SmartExtractAction.ConfirmNonEmptyDeck -> {
                val ids = pendingAccept
                pendingAccept = null
                _uiState.value.deckId?.let { confirmedDecks += it }
                _uiState.update { it.copy(nonEmptyDeck = null) }
                if (ids != null) accept(ids)
            }
            SmartExtractAction.CancelNonEmptyDeck -> {
                pendingAccept = null
                _uiState.update { it.copy(nonEmptyDeck = null) }
            }
            is SmartExtractAction.CreateDeck -> {
                _uiState.update { it.copy(showDeckDialog = false) }
                viewModelScope.launch {
                    val id = deckRepository.saveDeck(action.path, action.description, action.category)
                    preferredDeckPath = null
                    pinnedDeckId = null
                    pendingAccept = null
                    _uiState.update { it.copy(deckId = id, nonEmptyDeck = null) }
                }
            }
            is SmartExtractAction.SetDensity -> updateOptions { it.copy(density = action.density) }
            is SmartExtractAction.ToggleArchetype -> updateOptions {
                it.copy(archetypes = if (action.archetype in it.archetypes) it.archetypes - action.archetype else it.archetypes + action.archetype)
            }
            is SmartExtractAction.SetLanguage -> updateOptions { it.copy(language = action.language) }
            SmartExtractAction.Generate -> generate()
            SmartExtractAction.Stop -> {
                generationJob?.cancel()
                _uiState.update { it.copy(generation = GenerationState.Idle) }
            }
            SmartExtractAction.Retry -> retry()
            SmartExtractAction.AcceptDisclosure -> acceptDisclosure()
            SmartExtractAction.DismissDisclosure -> {
                afterDisclosure = null
                disclosureSteps = emptyList()
                _uiState.update { it.copy(disclosure = null, disclosureStep = DisclosureStep.Text) }
            }
            is SmartExtractAction.Accept -> accept(listOf(action.id))
            SmartExtractAction.AcceptAll -> accept(_uiState.value.acceptable.map { it.card.id })
            is SmartExtractAction.Discard -> _uiState.update { state ->
                state.copy(queue = state.queue.filterNot { it.card.id == action.id }, editingId = state.editingId.takeIf { it != action.id })
            }
            SmartExtractAction.DiscardAll -> _uiState.update { it.copy(queue = emptyList(), editingId = null) }
            is SmartExtractAction.Regenerate -> regenerate(action.id)
            is SmartExtractAction.Edit -> _uiState.update { it.copy(editingId = action.id) }
            is SmartExtractAction.EditFront -> editCard(action.id) { it.copy(front = action.text) }
            is SmartExtractAction.EditBack -> editCard(action.id) { it.copy(back = action.text) }
            SmartExtractAction.DoneEditing -> _uiState.update { it.copy(editingId = null) }
            SmartExtractAction.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun updateOptions(transform: (ExtractOptions) -> ExtractOptions) =
        _uiState.update { it.copy(options = transform(it.options)) }

    private fun read(source: SourceInput) {
        endBatch()
        readJob?.cancel()
        _uiState.update { it.copy(reading = true, sourceProblem = null) }
        readJob = viewModelScope.launch {
            when (val result = sources.read(source)) {
                is SourceResult.Success -> {
                    releasePdf()
                    val fragment = (source as? SourceInput.Link)?.url?.substringAfter('#', "")
                    val text = adoptSections(result.source, fragment)
                    _uiState.update { state ->
                        state.withText(text ?: result.source.text)
                            .copy(
                                reading = false,
                                title = result.source.title,
                                truncated = result.source.truncated,
                                disambiguation = result.source.disambiguation,
                                chapterId = null,
                            )
                            .withSections()
                    }
                }
                is SourceResult.Failure -> _uiState.update { it.copy(reading = false, sourceProblem = result.problem) }
            }
        }
    }

    /** Opens the PDF at [location] and reads the pages the Pages field starts with: all of them, or the first 300. */
    private fun openPdf(location: String) {
        endBatch()
        readJob?.cancel()
        _uiState.update { it.copy(reading = true, sourceProblem = null) }
        readJob = viewModelScope.launch {
            when (val opened = sources.openPdf(location)) {
                is PdfOpenResult.Success -> {
                    val handle = opened.handle
                    releasePdf()
                    pdf = handle
                    pdfText = null
                    val count = handle.info.pageCount
                    val field = if (count > PdfInfo.MAX_PAGES) "1-${PdfInfo.MAX_PAGES}" else ""
                    // The route may still be loading when a PDF is dropped on a screen that was just opened.
                    _uiState.first { !it.isLoading }
                    val canRead = _uiState.value.canReadPages
                    val remembered = userSettings.settings.first()
                    // Auto is the default for a model that reads images; a remembered AI mode needs one, or it falls back to Text.
                    val mode = (remembered.pdfReadMode ?: if (canRead) PdfReadMode.Auto else PdfReadMode.Text).takeIf { canRead || !it.usesAi } ?: PdfReadMode.Text
                    _uiState.update {
                        it.copy(
                            pdf = PdfSummary(
                                title = handle.info.title,
                                pageCount = count,
                                pages = field,
                                printedPages = handle.info.labels?.let { labels -> PageLabels.summary(labels) },
                                chapters = chapterOptions(handle.info),
                                mode = mode,
                                quality = remembered.pdfQuality,
                            ),
                        )
                    }
                    // The first read is always the text layer: it costs nothing, and says which pages have none.
                    loadPdfPages(handle, choosePages(field, count).first)
                }
                is PdfOpenResult.Failure -> _uiState.update { it.copy(reading = false, sourceProblem = opened.problem) }
            }
        }
    }

    /** Reads the field's pages into the box; a field with an error, or text the user edited and hasn't agreed to lose, waits. */
    private fun applyPdfPages(confirmed: Boolean) {
        val handle = pdf ?: return
        val summary = _uiState.value.pdf ?: return
        val (pages, error) = choosePages(summary.pages, handle.info.pageCount)
        if (pages == null) {
            _uiState.update { it.copy(pdf = summary.copy(error = error, replaceConfirmation = false)) }
            return
        }
        val text = _uiState.value.text
        if (!confirmed && text.isNotBlank() && text != pdfText) {
            _uiState.update { it.copy(pdf = summary.copy(error = null, replaceConfirmation = true)) }
            return
        }
        endBatch()
        readJob?.cancel()
        pdfRun = null
        // An AI mode needs a model that reads images; one that stopped being there reads the text layer instead.
        val mode = summary.mode.takeIf { !it.usesAi || _uiState.value.canReadPages } ?: PdfReadMode.Text
        _uiState.update {
            it.copy(reading = true, sourceProblem = null, pdf = summary.copy(error = null, replaceConfirmation = false, readState = null, readConfirmation = null))
        }
        readJob = viewModelScope.launch {
            if (mode.usesAi) planAiRead(handle, pages, mode, summary.quality) else loadPdfPages(handle, pages)
        }
    }

    /** Decides which pages need a request; with none to make, the box is filled at once, and otherwise the user is asked first. */
    private suspend fun planAiRead(handle: PdfHandle, pages: PageRanges, mode: PdfReadMode, quality: PdfQuality) {
        val route = _uiState.value.readRoute
        if (route == null) {
            _uiState.update { it.copy(reading = false) }
            return
        }
        when (val result = readPdfPages.plan(handle, pages.pages, mode, quality, route.modelId, transcriptions)) {
            is PdfReadPlanResult.Failed -> _uiState.update { it.copy(reading = false, sourceProblem = result.problem) }
            is PdfReadPlanResult.Ready -> {
                val plan = result.plan
                when {
                    // A text layer on every page: this is the text read, with its one-piece layout and its limits.
                    plan.isAllLayer -> loadPdfPages(handle, pages)
                    // What was transcribed before covers it: nothing to send, nothing to agree to.
                    plan.needsNoRequests -> startPdfRun(PdfRun(route, plan))
                    else -> {
                        pdfRun = PdfRun(route, plan)
                        _uiState.update { state ->
                            state.copy(
                                reading = false,
                                pdf = state.pdf?.copy(readConfirmation = PdfReadConfirmation(plan.pages.size, plan.requests, route.provider.name, route.modelId, quality)),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun confirmPdfRead() {
        val run = pdfRun ?: return
        _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(readConfirmation = null)) } ?: state }
        // Images carry everything on the page: the provider's own notice first, then the images'.
        withDisclosure(run.route, sendsImages = true) { startPdfRun(run) }
    }

    /**
     * Goes through [run]'s pages from [from], filling the box page by page: after [before] when resuming, from an
     * empty box when starting. The pages already in the box stay when it fails or is cancelled.
     */
    private fun startPdfRun(run: PdfRun, from: Int = 0, before: String = "") {
        val handle = pdf ?: return
        readJob?.cancel()
        pdfRun = run
        val total = run.plan.pages.size
        if (from == 0) {
            clearSections()
            pdfText = ""
        }
        _uiState.update { state ->
            val fresh = if (from == 0) state.withText("").copy(title = handle.info.title, truncated = false, disambiguation = false, chapterId = null) else state
            fresh.copy(
                reading = true,
                sourceProblem = null,
                pdf = fresh.pdf?.copy(readState = PdfReadState.Running(from, total, null), readConfirmation = null, error = null, transcribed = false),
            )
        }
        readJob = viewModelScope.launch {
            readPdfPages.run(run.route, run.plan, transcriptions, from, before).collect { event ->
                when (event) {
                    is ReadPagesEvent.Transcribing -> _uiState.update { state ->
                        state.copy(pdf = state.pdf?.copy(readState = PdfReadState.Running(event.index, event.total, event.page)))
                    }
                    is ReadPagesEvent.Progress -> {
                        pdfText = event.text
                        _uiState.update { state -> state.withText(event.text).copy(pdf = state.pdf?.copy(readState = PdfReadState.Running(event.done, event.total, null))) }
                    }
                    is ReadPagesEvent.Failed -> {
                        run.failedAt = event.index
                        pdfText = event.text
                        _uiState.update { state ->
                            state.withText(event.text).copy(
                                reading = false,
                                pdf = state.pdf?.copy(readState = PdfReadState.Failed(event.failure, event.page, event.index, total), transcribed = event.text.isNotBlank() && run.plan.pages.any { it.source != PageSource.Layer }),
                            )
                        }
                    }
                    is ReadPagesEvent.Finished -> {
                        pdfRun = null
                        pdfText = event.text
                        _uiState.update { state ->
                            state.withText(event.text).copy(
                                reading = false,
                                truncated = event.truncated,
                                pdf = state.pdf?.copy(readState = null, transcribed = event.text.isNotBlank() && run.plan.pages.any { it.source != PageSource.Layer }, pagesWithoutText = 0),
                                message = if (event.blankPages > 0) ExtractMessage.PagesBlank(event.blankPages) else state.message,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Stops an AI read for good: what is in the box stays, and Read pages starts again (what was transcribed is kept). */
    private fun cancelPdfRead() {
        readJob?.cancel()
        val run = pdfRun
        pdfRun = null
        _uiState.update { state ->
            val fromModel = run != null && state.text.isNotBlank() && run.plan.pages.any { it.source != PageSource.Layer }
            state.copy(reading = false, pdf = state.pdf?.copy(readState = null, transcribed = fromModel))
        }
    }

    private fun resumePdfRead() {
        val run = pdfRun ?: return
        if (_uiState.value.pdf?.readState !is PdfReadState.Failed) return
        startPdfRun(run, from = run.failedAt, before = _uiState.value.text)
    }

    private fun setPdfReadMode(mode: PdfReadMode) {
        if (mode.usesAi && !_uiState.value.canReadPages) return
        _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(mode = mode)) } ?: state }
        rememberPdfOptions()
    }

    private fun setPdfQuality(quality: PdfQuality) {
        _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(quality = quality)) } ?: state }
        rememberPdfOptions()
    }

    private fun rememberPdfOptions() {
        val summary = _uiState.value.pdf ?: return
        viewModelScope.launch { userSettings.setPdfReadOptions(summary.mode, summary.quality) }
    }

    private suspend fun loadPdfPages(handle: PdfHandle, pages: PageRanges?) {
        when (val result = sources.readPdf(handle, pages)) {
            is SourceResult.Success -> {
                clearSections()
                pdfText = result.source.text
                _uiState.update { state ->
                    state.withText(result.source.text).copy(
                        reading = false,
                        title = result.source.title,
                        truncated = result.source.truncated,
                        disambiguation = false,
                        chapterId = null,
                        pdf = state.pdf?.copy(pagesWithoutText = 0, transcribed = false, readState = null),
                    )
                }
                countPagesWithoutText(handle, pages)
            }
            is SourceResult.Failure -> _uiState.update { state ->
                // A PDF with no text at all is every page "without text": the offer to read them with AI is the same.
                val without = if (result.problem == SourceProblem.NoText) (pages ?: PageRanges.all(handle.info.pageCount).first(PdfInfo.MAX_PAGES)).count else 0
                state.copy(reading = false, sourceProblem = result.problem, pdf = state.pdf?.copy(pagesWithoutText = without, transcribed = false, readState = null))
            }
        }
    }

    /** After a text read: how many of its pages are only a picture, so the box can offer to read them with AI. */
    private suspend fun countPagesWithoutText(handle: PdfHandle, pages: PageRanges?) {
        val chosen = (pages ?: PageRanges.all(handle.info.pageCount).first(PdfInfo.MAX_PAGES)).pages
        val texts = (sources.pageTexts(handle, chosen) as? PdfPageTextsResult.Success)?.texts ?: return
        val without = texts.count { !PdfPageText.hasText(it.value) }
        // The PDF may have been replaced or closed while the layers were read.
        if (pdf != handle) return
        _uiState.update { state -> state.pdf?.let { state.copy(pdf = it.copy(pagesWithoutText = without)) } ?: state }
    }

    /** The bookmarks as picker rows; none when there are fewer than two, as one bookmark is no choice. */
    private fun chapterOptions(info: PdfInfo): List<PdfChapterOption> {
        if (info.outline.size < 2) return emptyList()
        return info.outline.mapIndexed { index, item ->
            val range = info.chapterPages(index)
            PdfChapterOption(index, item.title, item.level, range.first, info.label(range.first), range.last - range.first + 1)
        }
    }

    /** The chapters whose pages are all among the ones [field] names; a blank or unreadable field has none ticked. */
    private fun chaptersIn(field: String, info: PdfInfo): Set<Int> {
        val chosen = (PageRanges.parse(field, info.pageCount) as? PageRangesResult.Valid)?.ranges ?: return emptySet()
        return info.outline.indices.filterTo(mutableSetOf()) { chosen.containsAll(PageRanges.of(info.chapterPages(it))) }
    }

    /** Ticking adds the chapter's pages to the field, unticking takes them out; the field stays the one thing that decides. */
    private fun toggleChapter(handle: PdfHandle, id: Int) {
        val info = handle.info
        if (id !in info.outline.indices) return
        _uiState.update { state ->
            val summary = state.pdf ?: return@update state
            val chosen = (PageRanges.parse(summary.pages, info.pageCount) as? PageRangesResult.Valid)?.ranges ?: PageRanges.Empty
            val chapter = PageRanges.of(info.chapterPages(id))
            val next = if (id in summary.selectedChapters) chosen - chapter else chosen + chapter
            val field = next.format()
            state.copy(pdf = summary.copy(pages = field, error = choosePages(field, info.pageCount).second, selectedChapters = chaptersIn(field, info)))
        }
    }

    /** Forgets the open PDF and has its copy deleted. */
    private fun releasePdf() {
        val handle = pdf ?: return
        pdf = null
        pdfText = null
        pdfRun = null
        transcriptions.clear(handle)
        _uiState.update { it.copy(pdf = null) }
        viewModelScope.launch { sources.closePdf(handle) }
    }

    /**
     * The pages [field] names in a PDF of [pageCount] pages, or why it can't be read. An empty field is every
     * page, which one read takes only up to [PdfInfo.MAX_PAGES]: more is an error, not a silent cut.
     */
    private fun choosePages(field: String, pageCount: Int): Pair<PageRanges?, PdfPagesError?> {
        val pages = if (field.isBlank()) {
            PageRanges.all(pageCount)
        } else {
            when (val parsed = PageRanges.parse(field, pageCount)) {
                is PageRangesResult.Valid -> parsed.ranges
                is PageRangesResult.Invalid -> return null to PdfPagesError.Invalid(parsed.error)
            }
        }
        if (pages.count > PdfInfo.MAX_PAGES) return null to PdfPagesError.TooMany(pages.count, PdfInfo.MAX_PAGES)
        return pages to null
    }

    /**
     * Makes [source] the page whose sections can be chosen, if it has more than one, with all of them chosen or the
     * ones [fragment] names; returns the text they make, or null (and forgets any earlier page) for a source with
     * nothing to choose.
     */
    private fun adoptSections(source: SourceText, fragment: String?): String? {
        clearSections()
        if (source.sections.size < 2) return null
        val chosen = fragment?.let { SourceSections.forFragment(source.sections, it) } ?: source.sections.map { it.id }.toSet()
        sectionSource = source
        selectedSections = chosen
        return SourceSections.join(source, chosen).also { sectionText = it }
    }

    private fun clearSections() {
        sectionSource = null
        sectionText = null
        pendingSections = null
        selectedSections = emptySet()
        _uiState.update { if (it.sections != null || it.showSections || it.sectionsConfirmation) it.copy(sections = null, showSections = false, sectionsConfirmation = false) else it }
    }

    private fun SmartExtractUiState.withSections(): SmartExtractUiState {
        val source = sectionSource ?: return this
        return copy(
            sections = SectionsSummary(
                options = source.sections.map { SectionOption(it.id, it.title, it.level, SourceText.countWords(source.textOf(it))) },
                selected = selectedSections,
            ),
            showSections = false,
            sectionsConfirmation = false,
        )
    }

    /** Changes which sections are in the box; text the user has edited is theirs, so they are asked before it goes. */
    private fun chooseSections(ids: Set<Int>) {
        if (_uiState.value.text != sectionText) {
            pendingSections = ids
            _uiState.update { it.copy(sectionsConfirmation = true) }
        } else {
            applySections(ids)
        }
    }

    private fun applySections(ids: Set<Int>) {
        val source = sectionSource ?: return
        pendingSections = null
        selectedSections = ids
        val text = SourceSections.join(source, ids)
        sectionText = text
        _uiState.update { it.withText(text).withSections().copy(showSections = true) }
    }

    private fun readBook(location: String) {
        endBatch()
        readJob?.cancel()
        _uiState.update { it.copy(reading = true, sourceProblem = null) }
        readJob = viewModelScope.launch {
            when (val result = sources.readBook(SourceInput.Epub(location))) {
                is BookResult.Success -> adopt(result.book, name = null, chapterId = null)
                is BookResult.Failure -> _uiState.update { it.copy(reading = false, sourceProblem = result.problem) }
            }
        }
    }

    /**
     * Makes [book] the one chapters are chosen from: the picked one, or the one the book import handed over
     * under [name], the name the user gave its deck. [chapterId] chooses a chapter at once; without one the
     * chapter list opens.
     */
    private suspend fun adopt(book: BookSource, name: String?, chapterId: Int?, openChapters: Boolean = chapterId == null) {
        clearSections()
        releasePdf()
        val titled = book.withDefaultTitles()
        this.book = titled
        bookName = name ?: titled.defaultName()
        batchDecks = emptyMap()
        pendingBatch = null
        _uiState.update {
            it.copy(
                reading = false,
                sourceKind = SourceKind.Epub,
                sourceProblem = null,
                book = titled.summary(),
                chapterId = null,
                showChapters = openChapters,
                batch = null,
                batchConfirmation = null,
            )
        }
        chapterId?.let { selectChapter(it) }
        adoptedBooks.trySend(Unit)
    }

    private fun BookSource.summary() = BookSummary(
        title = title.ifBlank { bookName },
        author = author?.takeIf { it.isNotBlank() },
        chapters = chapters.map { it.option() },
        truncated = truncated,
    )

    private fun BookChapter.option() = ChapterOption(id, title, wordCount, kind, truncated)

    /**
     * Puts the chapter's text in the box, titled "Book — Chapter", and selects its deck if the import made one.
     * A [deckId] the book import handed over (a book run) is used as it is, with no looking up by name.
     */
    private fun selectChapter(id: Int, deckId: String? = null) {
        val book = book ?: return
        val chapter = book.chapters.firstOrNull { it.id == id } ?: return
        val title = "${book.title.ifBlank { bookName }} — ${chapter.title}"
        val preferred = if (deckId != null) {
            preferredDeckPath = null
            pinnedDeckId = deckId
            null
        } else {
            preferredDeckPath = BookDeckNames.path(BookDeckNames.book(bookName), BookDeckNames.chapter(chapter.id + 1, book.deckCount(), chapter.title))
            preferredDeck(_uiState.value.decks).also {
                // Only a list that hasn't loaded yet can still bring the deck; later on a missing deck stays missing.
                if (it == null && !_uiState.value.isLoading) preferredDeckPath = null
            }
        }
        _uiState.update {
            it.withText(chapter.text).copy(
                title = title,
                truncated = chapter.truncated,
                chapterId = id,
                showChapters = false,
                deckId = deckId ?: preferred?.id ?: it.deckId,
            )
        }
    }

    /**
     * Starts a run over [chapterIds] (book order, chapters without text left out) with their decks [deckIds].
     * A queue that still has cards, or a generation under way, is the user's: they are asked before it is discarded.
     */
    private fun startBatch(chapterIds: List<Int>, deckIds: Map<Int, String>) {
        val book = book ?: return
        val ids = chapterIds.distinct().filter { id -> book.chapters.any { it.id == id && it.text.isNotBlank() } }
        if (ids.isEmpty()) return
        val plan = PendingBatch(ids, deckIds)
        val state = _uiState.value
        if (state.queue.isNotEmpty() || state.isGenerating) {
            pendingBatch = plan
            _uiState.update { it.copy(batchConfirmation = BatchConfirmation.Start) }
        } else {
            beginBatch(plan)
        }
    }

    private fun beginBatch(plan: PendingBatch) {
        val book = book ?: return
        pendingBatch = null
        generationJob?.cancel()
        run = null
        batchDecks = plan.deckIds
        val chapters = plan.chapterIds.mapNotNull { id -> book.chapters.firstOrNull { it.id == id }?.option() }
        _uiState.update { it.withoutQueue().copy(batch = BookBatch(chapters, position = 0), batchConfirmation = null) }
        enterBatchChapter()
    }

    /** Puts the run's current chapter in the box and generates it; the provider notice still comes first. */
    private fun enterBatchChapter() {
        val batch = _uiState.value.batch ?: return
        val id = batch.current.id
        selectChapter(id, batchDecks[id])
        generate()
    }

    /** Goes on to the next chapter of the run, or ends it after the last. Never called by a failure or a finished part. */
    private fun nextChapter(confirmed: Boolean) {
        val state = _uiState.value
        val batch = state.batch ?: return
        if (!confirmed && state.queue.isNotEmpty()) {
            _uiState.update { it.copy(batchConfirmation = BatchConfirmation.Advance) }
            return
        }
        generationJob?.cancel()
        run = null
        if (batch.isLast) {
            batchDecks = emptyMap()
            _uiState.update { it.withoutQueue().copy(batch = null, batchConfirmation = null, message = ExtractMessage.BatchFinished(batch.total)) }
        } else {
            _uiState.update { it.withoutQueue().copy(batch = batch.copy(position = batch.position + 1), batchConfirmation = null) }
            enterBatchChapter()
        }
    }

    private fun endBatch() {
        pendingBatch = null
        batchDecks = emptyMap()
        _uiState.update { if (it.batch != null || it.batchConfirmation != null) it.copy(batch = null, batchConfirmation = null) else it }
    }

    /** The review queue emptied for the next chapter of a run: nothing of the last one carries over. */
    private fun SmartExtractUiState.withoutQueue() = copy(
        queue = emptyList(),
        editingId = null,
        duplicatesSkipped = 0,
        invalidSkipped = 0,
        generation = GenerationState.Idle,
    )

    /** The deck waiting to be selected, if [decks] has it now; asking for it uses it up. */
    private fun preferredDeck(decks: List<DeckOption>): DeckOption? {
        val path = preferredDeckPath ?: return null
        val deck = decks.firstOrNull { it.path.equals(path, ignoreCase = true) } ?: return null
        preferredDeckPath = null
        return deck
    }

    /** Sets the text and how many requests it takes (one per part), which the screen says when there are several. */
    private fun SmartExtractUiState.withText(text: String) =
        copy(text = text, requests = if (text.isBlank()) 0 else generateCards.split(text).size)

    private fun startDictation() {
        if (dictationJob?.isActive == true) return
        if (!sources.isDictationAvailable()) {
            _uiState.update { it.copy(dictation = DictationState.Failed(DictationProblem.Unavailable)) }
            return
        }
        _uiState.update { it.copy(dictation = DictationState.Listening()) }
        dictationJob = viewModelScope.launch {
            sources.dictate().collect { event ->
                when (event) {
                    DictationEvent.Listening -> _uiState.update { it.copy(dictation = DictationState.Listening()) }
                    is DictationEvent.Partial -> _uiState.update { it.copy(dictation = DictationState.Listening(event.text)) }
                    is DictationEvent.Final -> _uiState.update {
                        it.withText(appendPhrase(it.text, event.text)).copy(dictation = DictationState.Listening())
                    }
                    is DictationEvent.Failed -> {
                        _uiState.update { it.copy(dictation = DictationState.Failed(event.problem)) }
                        dictationJob?.cancel()
                    }
                }
            }
        }
    }

    /** Stops listening, keeping the words of the phrase in progress. */
    private fun stopDictation() {
        dictationJob?.cancel()
        dictationJob = null
        _uiState.update { state ->
            val partial = (state.dictation as? DictationState.Listening)?.partial.orEmpty()
            val text = if (partial.isNotBlank()) appendPhrase(state.text, partial) else state.text
            state.withText(text).copy(dictation = if (state.dictation is DictationState.Failed) state.dictation else DictationState.Off)
        }
    }

    private fun generate() {
        val state = _uiState.value
        val route = state.route ?: return
        if (!state.canGenerate) return
        stopDictation()
        withDisclosure(route) {
            val current = _uiState.value
            val parts = generateCards.split(current.text)
            if (parts.isEmpty()) return@withDisclosure
            run = Run(parts, current.options, current.title, current.deckId)
            launchGeneration(route, fromPart = 0)
        }
    }

    private fun retry() {
        val failed = _uiState.value.generation as? GenerationState.Failed ?: return
        val route = _uiState.value.route ?: return
        launchGeneration(route, failed.part)
    }

    private fun launchGeneration(route: AiRoute, fromPart: Int) {
        val run = run ?: return
        generationJob?.cancel()
        _uiState.update { it.copy(generation = GenerationState.Running(fromPart, run.parts.size)) }
        generationJob = viewModelScope.launch {
            var added = 0
            var part = fromPart
            val request = ExtractRequest(
                parts = run.parts,
                options = run.options,
                deckId = run.deckId,
                title = run.title,
                fromPart = fromPart,
                known = _uiState.value.queue.map { it.card },
            )
            generateCards(route, request).collect { event ->
                when (event) {
                    is ExtractEvent.PartStarted -> {
                        part = event.part
                        _uiState.update { it.copy(generation = GenerationState.Running(event.part, event.parts)) }
                    }
                    is ExtractEvent.Card -> {
                        added++
                        _uiState.update { it.copy(queue = it.queue + QueueItem(event.card, run.parts[part])) }
                    }
                    is ExtractEvent.Skipped -> _uiState.update {
                        when (event.reason) {
                            SkipReason.Duplicate -> it.copy(duplicatesSkipped = it.duplicatesSkipped + 1)
                            SkipReason.Invalid -> it.copy(invalidSkipped = it.invalidSkipped + 1)
                        }
                    }
                    is ExtractEvent.Failed -> _uiState.update { it.copy(generation = GenerationState.Failed(event.failure, event.part, run.parts.size)) }
                    ExtractEvent.Finished -> _uiState.update { it.copy(generation = GenerationState.Done(added)) }
                }
            }
        }
    }

    private fun regenerate(id: String) {
        val state = _uiState.value
        val route = state.route ?: return
        val item = state.queue.firstOrNull { it.card.id == id }?.takeIf { !it.regenerating } ?: return
        withDisclosure(route) {
            setRegenerating(id, true)
            viewModelScope.launch {
                val current = _uiState.value
                val result = regenerateCard(
                    route = route,
                    card = item.card,
                    source = item.source,
                    options = run?.options ?: current.options,
                    deckId = current.deckId,
                    title = run?.title ?: current.title,
                    known = current.queue.map { it.card },
                )
                _uiState.update { s ->
                    when (result) {
                        is RegenerateResult.Replaced -> s.copy(
                            queue = s.queue.map { if (it.card.id == id) QueueItem(result.card, item.source) else it },
                            editingId = s.editingId.takeIf { it != id },
                        )
                        RegenerateResult.NothingNew -> s.copy(queue = s.queue.withRegenerating(id, false), message = ExtractMessage.NothingNew)
                        is RegenerateResult.Failed -> s.copy(
                            queue = s.queue.withRegenerating(id, false),
                            message = ExtractMessage.RegenerateFailed(result.failure),
                        )
                    }
                }
            }
        }
    }

    private fun setRegenerating(id: String, regenerating: Boolean) =
        _uiState.update { it.copy(queue = it.queue.withRegenerating(id, regenerating)) }

    private fun List<QueueItem>.withRegenerating(id: String, regenerating: Boolean) =
        map { if (it.card.id == id) it.copy(regenerating = regenerating) else it }

    private fun editCard(id: String, transform: (GeneratedCard) -> GeneratedCard) =
        _uiState.update { state -> state.copy(queue = state.queue.map { if (it.card.id == id) it.copy(card = transform(it.card)) else it }) }

    /**
     * Saves [ids] to the chosen deck. They leave the queue at once, so a double tap can't save twice. A deck that
     * already has cards is asked about first (once per deck): a whole queue in the wrong deck is hard to take back.
     */
    private fun accept(ids: List<String>) {
        val state = _uiState.value
        val deckId = state.deckId
        if (deckId == null) {
            _uiState.update { it.copy(showDeckDialog = true) }
            return
        }
        val items = state.queue.filter { it.card.id in ids && it.problem == null && !it.regenerating }
        if (items.isEmpty()) return
        val deck = state.decks.firstOrNull { it.id == deckId }
        if (deck != null && deck.cardCount > 0 && deckId !in confirmedDecks) {
            pendingAccept = ids
            _uiState.update { it.copy(nonEmptyDeck = NonEmptyDeck(deck.path, deck.cardCount)) }
            return
        }
        // This session's own cards make the deck non-empty: no need to ask again before the next one.
        confirmedDecks += deckId
        val removed = items.map { it.card.id }.toSet()
        _uiState.update { s -> s.copy(queue = s.queue.filterNot { it.card.id in removed }, editingId = s.editingId.takeIf { it !in removed }) }
        viewModelScope.launch {
            val result = acceptCards(deckId, items.map { it.card })
            _uiState.update { it.copy(message = ExtractMessage.Accepted(result.cardCount, state.deckPath.orEmpty())) }
        }
    }

    /**
     * Runs [action] once the notices the request needs are accepted: the provider's own, and, for a request that
     * [sendsImages] of PDF pages, the images' (ADR 0014), in that order and each asked once per provider.
     */
    private fun withDisclosure(route: AiRoute, sendsImages: Boolean = false, action: () -> Unit) {
        val textAccepted = route.provider.disclosureAcceptedAt != null || route.provider.id in disclosed
        if (!sendsImages) {
            showDisclosures(route, pendingDisclosures(textAccepted, sendsImages = false, imagesAccepted = false), action)
            return
        }
        viewModelScope.launch {
            val imagesAccepted = route.provider.id in imageDisclosed || userSettings.settings.first().hasAcceptedImages(route.provider.id)
            showDisclosures(route, pendingDisclosures(textAccepted, sendsImages = true, imagesAccepted), action)
        }
    }

    private fun showDisclosures(route: AiRoute, steps: List<DisclosureStep>, action: () -> Unit) {
        if (steps.isEmpty()) {
            action()
            return
        }
        afterDisclosure = action
        disclosureSteps = steps.drop(1)
        _uiState.update { it.copy(disclosure = route, disclosureStep = steps.first()) }
    }

    private fun acceptDisclosure() {
        val route = _uiState.value.disclosure ?: return
        val id = route.provider.id
        when (_uiState.value.disclosureStep) {
            DisclosureStep.Text -> {
                disclosed += id
                viewModelScope.launch { aiProviders.acceptDisclosure(id) }
            }
            DisclosureStep.Images -> {
                imageDisclosed += id
                viewModelScope.launch { userSettings.acceptImageDisclosure(id) }
            }
        }
        val next = disclosureSteps.firstOrNull()
        if (next != null) {
            disclosureSteps = disclosureSteps.drop(1)
            _uiState.update { it.copy(disclosureStep = next) }
            return
        }
        _uiState.update { it.copy(disclosure = null, disclosureStep = DisclosureStep.Text) }
        afterDisclosure?.invoke()
        afterDisclosure = null
    }

    internal companion object {
        /** Adds a dictated phrase to the text, on the same line with a space. */
        fun appendPhrase(text: String, phrase: String): String {
            val trimmed = phrase.trim()
            return when {
                trimmed.isEmpty() -> text
                text.isEmpty() || text.endsWith('\n') -> text + trimmed
                text.endsWith(' ') -> text + trimmed
                else -> "$text $trimmed"
            }
        }
    }
}
