package com.yahyafati.mnemo.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.ExtractEvent
import com.yahyafati.mnemo.core.domain.ExtractRequest
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.domain.RegenerateResult
import com.yahyafati.mnemo.core.domain.SkipReason
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Smart Extract (ARCHITECTURE §5.2): source → text → streamed cards in a review queue → accepted
 * notes. Nothing is saved until a card is accepted; cards that arrived before a failure stay in
 * the queue, and Retry resumes at the part that failed.
 */
class SmartExtractViewModel(
    private val aiProviders: AiProviderRepository,
    private val deckRepository: DeckRepository,
    private val sources: SourceRepository,
    private val generateCards: GenerateCardsUseCase,
    private val regenerateCard: RegenerateCardUseCase,
    private val acceptCards: AcceptGeneratedCardsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SmartExtractUiState())
    val uiState: StateFlow<SmartExtractUiState> = _uiState.asStateFlow()

    /** The source being generated from, kept so Retry can resume it. */
    private class Run(val parts: List<String>, val options: ExtractOptions, val title: String?, val deckId: String?)

    private var run: Run? = null
    private var generationJob: Job? = null
    private var dictationJob: Job? = null
    private var readJob: Job? = null

    /** What to do once the provider notice is accepted. */
    private var afterDisclosure: (() -> Unit)? = null

    /** Providers whose notice was accepted here; the repository's flow may not have caught up yet. */
    private val disclosed = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(aiProviders.observeEffectiveRoutes(), deckRepository.observeDeckSummaries()) { routes, decks ->
                routes[AiTask.Extract] to decks.map { DeckOption(it.deck.id, it.path) }.sortedBy { it.path.lowercase() }
            }.collect { (route, decks) ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        route = route,
                        decks = decks,
                        deckId = state.deckId?.takeIf { id -> decks.any { it.id == id } } ?: decks.firstOrNull()?.id,
                    )
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
                if (action.text.isBlank()) it.copy(text = action.text, title = null, truncated = false) else it.copy(text = action.text)
            }
            SmartExtractAction.ClearText -> _uiState.update { it.copy(text = "", title = null, truncated = false, sourceProblem = null) }
            is SmartExtractAction.LinkChanged -> _uiState.update { it.copy(link = action.link, sourceProblem = null) }
            SmartExtractAction.FetchLink -> _uiState.value.link.takeIf { it.isNotBlank() }?.let { read(SourceInput.Link(it.trim())) }
            is SmartExtractAction.PdfPicked -> read(SourceInput.Pdf(action.uri))
            SmartExtractAction.StartDictation -> startDictation()
            SmartExtractAction.StopDictation -> stopDictation()
            SmartExtractAction.DictationPermissionDenied ->
                _uiState.update { it.copy(dictation = DictationState.Failed(DictationProblem.NoPermission)) }
            is SmartExtractAction.SelectDeck -> _uiState.update { it.copy(deckId = action.deckId) }
            SmartExtractAction.ShowDeckDialog -> _uiState.update { it.copy(showDeckDialog = true) }
            SmartExtractAction.DismissDeckDialog -> _uiState.update { it.copy(showDeckDialog = false) }
            is SmartExtractAction.CreateDeck -> {
                _uiState.update { it.copy(showDeckDialog = false) }
                viewModelScope.launch {
                    val id = deckRepository.saveDeck(action.path, action.description, action.category)
                    _uiState.update { it.copy(deckId = id) }
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
                _uiState.update { it.copy(disclosure = null) }
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
        readJob?.cancel()
        _uiState.update { it.copy(reading = true, sourceProblem = null) }
        readJob = viewModelScope.launch {
            when (val result = sources.read(source)) {
                is SourceResult.Success -> _uiState.update {
                    it.copy(reading = false, text = result.source.text, title = result.source.title, truncated = result.source.truncated)
                }
                is SourceResult.Failure -> _uiState.update { it.copy(reading = false, sourceProblem = result.problem) }
            }
        }
    }

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
                        it.copy(text = appendPhrase(it.text, event.text), dictation = DictationState.Listening())
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
            state.copy(text = text, dictation = if (state.dictation is DictationState.Failed) state.dictation else DictationState.Off)
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

    /** Saves [ids] to the chosen deck. They leave the queue at once, so a double tap can't save twice. */
    private fun accept(ids: List<String>) {
        val state = _uiState.value
        val deckId = state.deckId
        if (deckId == null) {
            _uiState.update { it.copy(showDeckDialog = true) }
            return
        }
        val items = state.queue.filter { it.card.id in ids && it.problem == null && !it.regenerating }
        if (items.isEmpty()) return
        val removed = items.map { it.card.id }.toSet()
        _uiState.update { s -> s.copy(queue = s.queue.filterNot { it.card.id in removed }, editingId = s.editingId.takeIf { it !in removed }) }
        viewModelScope.launch {
            val result = acceptCards(deckId, items.map { it.card })
            _uiState.update { it.copy(message = ExtractMessage.Accepted(result.cardCount, state.deckPath.orEmpty())) }
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

    private fun acceptDisclosure() {
        val route = _uiState.value.disclosure ?: return
        disclosed += route.provider.id
        _uiState.update { it.copy(disclosure = null) }
        viewModelScope.launch { aiProviders.acceptDisclosure(route.provider.id) }
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
