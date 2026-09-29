package com.yahyafati.mnemo.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.StudyAssistRepository
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.Cloze
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StudyAssistUiState(
    /** Route for "Explain this" and "Give me an example"; null hides them. */
    val explainRoute: AiRoute? = null,
    /** Route for "Rewrite this card"; null hides it. */
    val rewriteRoute: AiRoute? = null,
    val sheet: AssistSheet? = null,
    /** Waiting for the provider notice to be accepted. */
    val disclosure: AiRoute? = null,
) {
    /** Study-time AI shows only when a provider is configured (ROADMAP Phase 4). */
    val available: Boolean get() = explainRoute != null || rewriteRoute != null

    fun routeFor(assist: StudyAssist): AiRoute? = if (assist.task == AiTask.Rewrite) rewriteRoute else explainRoute
}

/** The open AI sheet for [card]: a menu while [assist] is null, then one answer. */
data class AssistSheet(
    val card: StudyCard,
    val assist: StudyAssist? = null,
    /** Explain/Example: the Markdown so far. */
    val text: String = "",
    val running: Boolean = false,
    val failure: AiFailure? = null,
    /** Rewrite: the proposed note fields. */
    val proposal: List<String>? = null,
    val proposalProblem: RewriteProblem? = null,
    /** The rewrite was saved; the session reloads the card. */
    val applied: Boolean = false,
)

enum class RewriteProblem {
    /** A side came back empty. */
    Empty,

    /** The cloze numbers changed, which would delete or add study cards and lose their history. */
    ClozeChanged,
}

sealed interface AssistAction {
    data class Open(val card: StudyCard) : AssistAction

    data object Close : AssistAction

    data class Run(val assist: StudyAssist) : AssistAction

    /** Back to the menu. */
    data object Back : AssistAction

    data object ApplyRewrite : AssistAction

    data object AcceptDisclosure : AssistAction

    data object DismissDisclosure : AssistAction
}

/**
 * Study-time AI (PROJECT_OVERVIEW §4): explain, give an example, or rewrite the current card.
 * Answers stream into a sheet; a rewrite is only a proposal until applied, and applying keeps the
 * card's schedule (cloze numbers must not change).
 */
@HiltViewModel
class StudyAssistViewModel @Inject constructor(
    private val aiProviders: AiProviderRepository,
    private val assistRepository: StudyAssistRepository,
    private val cardRepository: CardRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StudyAssistUiState())
    val uiState: StateFlow<StudyAssistUiState> = _uiState.asStateFlow()

    private var job: Job? = null
    private var afterDisclosure: (() -> Unit)? = null
    private val disclosed = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            aiProviders.observeEffectiveRoutes().collect { routes ->
                _uiState.update { it.copy(explainRoute = routes[AiTask.Explain], rewriteRoute = routes[AiTask.Rewrite]) }
            }
        }
    }

    fun onAction(action: AssistAction) {
        when (action) {
            is AssistAction.Open -> {
                job?.cancel()
                _uiState.update { it.copy(sheet = AssistSheet(action.card)) }
            }
            AssistAction.Close -> {
                job?.cancel()
                _uiState.update { it.copy(sheet = null) }
            }
            is AssistAction.Run -> run(action.assist)
            AssistAction.Back -> {
                job?.cancel()
                _uiState.update { state -> state.copy(sheet = state.sheet?.let { AssistSheet(it.card) }) }
            }
            AssistAction.ApplyRewrite -> applyRewrite()
            AssistAction.AcceptDisclosure -> {
                val route = _uiState.value.disclosure ?: return
                disclosed += route.provider.id
                _uiState.update { it.copy(disclosure = null) }
                viewModelScope.launch { aiProviders.acceptDisclosure(route.provider.id) }
                afterDisclosure?.invoke()
                afterDisclosure = null
            }
            AssistAction.DismissDisclosure -> {
                afterDisclosure = null
                _uiState.update { it.copy(disclosure = null) }
            }
        }
    }

    private fun run(assist: StudyAssist) {
        val state = _uiState.value
        val sheet = state.sheet ?: return
        val route = state.routeFor(assist) ?: return
        val start = {
            job?.cancel()
            _uiState.update { it.copy(sheet = AssistSheet(sheet.card, assist, running = true)) }
            job = viewModelScope.launch {
                if (assist == StudyAssist.Rewrite) rewrite(route, sheet.card) else explain(route, assist, sheet.card)
            }
        }
        if (route.provider.disclosureAcceptedAt != null || route.provider.id in disclosed) {
            start()
        } else {
            afterDisclosure = start
            _uiState.update { it.copy(disclosure = route) }
        }
    }

    private suspend fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard) {
        assistRepository.explain(route, assist, card).collect { update ->
            when (update) {
                is AssistUpdate.Text -> updateSheet { it.copy(text = it.text + update.delta) }
                is AssistUpdate.Done -> updateSheet { it.copy(running = false, failure = update.failure) }
            }
        }
    }

    private suspend fun rewrite(route: AiRoute, card: StudyCard) {
        when (val outcome = assistRepository.rewrite(route, card)) {
            is RewriteOutcome.Proposed -> updateSheet {
                it.copy(running = false, proposal = outcome.fields, proposalProblem = problem(card, outcome.fields))
            }
            is RewriteOutcome.Failed -> updateSheet { it.copy(running = false, failure = outcome.failure) }
        }
    }

    private fun problem(card: StudyCard, fields: List<String>): RewriteProblem? {
        val front = fields.getOrElse(0) { "" }
        val back = fields.getOrElse(1) { "" }
        return when {
            front.isBlank() -> RewriteProblem.Empty
            card.kind == NoteKind.Cloze -> RewriteProblem.ClozeChanged.takeIf { Cloze.ordinals(front) != Cloze.ordinals(card.note.field(0)) }
            back.isBlank() -> RewriteProblem.Empty
            else -> null
        }
    }

    private fun applyRewrite() {
        val sheet = _uiState.value.sheet ?: return
        val fields = sheet.proposal?.takeIf { sheet.proposalProblem == null && !sheet.applied && !sheet.running } ?: return
        val note = sheet.card.note
        updateSheet { it.copy(running = true) }
        viewModelScope.launch {
            // Only the fields change: deck and tags stay, and so does every card's schedule.
            cardRepository.updateNote(note.id, note.deckId, fields.map { it.trim() }, note.tags)
            // Set once saved, so the session reloads the new text.
            updateSheet { it.copy(running = false, applied = true) }
        }
    }

    private fun updateSheet(transform: (AssistSheet) -> AssistSheet) =
        _uiState.update { state -> state.copy(sheet = state.sheet?.let(transform)) }
}
