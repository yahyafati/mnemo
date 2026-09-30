package com.yahyafati.mnemo.feature.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.domain.AnswerCardUseCase
import com.yahyafati.mnemo.core.domain.BuildStudyQueueUseCase
import com.yahyafati.mnemo.core.domain.CardAnswer
import com.yahyafati.mnemo.core.domain.StudyQueue
import com.yahyafati.mnemo.core.domain.StudySession
import com.yahyafati.mnemo.core.domain.UndoLastAnswerUseCase
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.TypedAnswer
import com.yahyafati.mnemo.core.ui.card.CardResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/**
 * Runs a study session (ARCHITECTURE §5.1). The queue lives in memory; answering moves to the
 * next card at once and saves in the background (optimistic UI), so there is never a loading
 * state between cards. Saves run one at a time, in order, so an undo can't overtake its answer.
 *
 * `deckId` in the [SavedStateHandle] picks one deck (and its subdecks); without it, this is the
 * Daily Mix across all decks.
 */
class StudyViewModel(
    savedStateHandle: SavedStateHandle,
    private val buildStudyQueue: BuildStudyQueueUseCase,
    private val answerCard: AnswerCardUseCase,
    private val undoLastAnswer: UndoLastAnswerUseCase,
    private val cardRepository: CardRepository,
    private val clock: Clock,
) : ViewModel() {
    private val deckId: String? = savedStateHandle[DECK_ID_KEY]

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var queue: StudyQueue? = null
    private var session: StudySession? = null
    private var current: StudyCard? = null
    private var preview: Map<Rating, CardAnswer> = emptyMap()
    private var shownAt: Instant = Instant.EPOCH
    private var revealed = false
    private var response = CardResponse()
    private val undoStack = ArrayDeque<UndoEntry>()
    private val ratingCounts = mutableMapOf<Rating, Int>()
    private var totalTimeMs = 0L

    /** Cards buried or suspended this session; an undo must not bring them back. */
    private val removed = mutableSetOf<String>()
    private val writes = Mutex()
    private var loadJob: Job? = null

    private class UndoEntry(val answer: CardAnswer, val cardBefore: StudyCard, val sessionBefore: StudySession, val durationMs: Long)

    init {
        load()
    }

    /**
     * Call whenever the screen comes back into view. Mid-session, this reloads card content
     * (after edit in place). Otherwise it looks for cards that became available meanwhile.
     */
    fun onScreenShown() {
        when (_uiState.value.phase) {
            StudyPhase.Loading -> Unit
            is StudyPhase.Reviewing -> refreshContent()
            is StudyPhase.Empty, is StudyPhase.Finished -> load(keepSummaryIfEmpty = true)
        }
    }

    fun onAction(action: StudyAction) {
        when (action) {
            StudyAction.Flip -> if (current != null) {
                revealed = !revealed
                render()
            }
            is StudyAction.Rate -> rate(action.rating)
            is StudyAction.TypeAnswer -> if (current != null && !revealed) {
                response = response.copy(typed = action.text)
                render()
            }
            is StudyAction.Choose -> if (current != null && !revealed) {
                response = response.copy(chosen = action.index)
                revealed = true
                render()
            }
            StudyAction.ShowHint -> if (current != null) {
                response = response.copy(hintShown = true)
                render()
            }
            StudyAction.Undo -> undo()
            StudyAction.ToggleStar -> updateCurrent { card ->
                val starred = !card.card.starred
                write { cardRepository.setStarred(card.card.id, starred) }
                card.copy(card = card.card.copy(starred = starred))
            }
            StudyAction.ToggleFlag -> updateCurrent { card ->
                val flagged = !card.card.flagged
                write { cardRepository.setFlagged(card.card.id, flagged) }
                card.copy(card = card.card.copy(flagged = flagged))
            }
            StudyAction.Bury -> removeCurrent { id, s -> cardRepository.bury(id, s.dayEnd) }
            StudyAction.Suspend -> removeCurrent { id, _ -> cardRepository.setSuspended(id, true) }
            StudyAction.Continue -> showNext()
        }
    }

    private fun load(keepSummaryIfEmpty: Boolean = false) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val built = buildStudyQueue(deckId)
            val finished = _uiState.value.phase as? StudyPhase.Finished
            if (keepSummaryIfEmpty && finished != null && built.session.next(clock.now()) == null) {
                _uiState.value = _uiState.value.copy(
                    phase = finished.copy(laterCount = built.session.laterCount(clock.now())),
                )
                return@launch
            }
            queue = built
            session = built.session
            undoStack.clear()
            ratingCounts.clear()
            totalTimeMs = 0
            removed.clear()
            _uiState.value = StudyUiState(deckName = built.deckName)
            showNext()
        }
    }

    private fun rate(rating: Rating) {
        val card = current ?: return
        val scheduler = queue?.scheduler ?: return
        val before = session ?: return
        if (!revealed) return
        val now = clock.now()
        val durationMs = Duration.between(shownAt, now).toMillis().coerceIn(0, MAX_ANSWER_MS)
        val answer = scheduler.answer(card.card, rating, now, durationMs)

        undoStack.addLast(UndoEntry(answer, card, before, durationMs))
        session = before.afterAnswer(answer)
        ratingCounts[rating] = (ratingCounts[rating] ?: 0) + 1
        totalTimeMs += durationMs
        showNext(now)
        write { answerCard(answer) }
    }

    private fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        session = removed.fold(entry.sessionBefore) { s, id -> s.without(id) }
        ratingCounts[entry.answer.rating] = (ratingCounts[entry.answer.rating] ?: 1) - 1
        totalTimeMs -= entry.durationMs
        show(entry.cardBefore, clock.now())
        write { undoLastAnswer(entry.answer) }
    }

    private fun updateCurrent(transform: (StudyCard) -> StudyCard) {
        val card = current ?: return
        current = transform(card)
        render()
    }

    private fun removeCurrent(persist: suspend (cardId: String, session: StudySession) -> Unit) {
        val card = current ?: return
        val s = session ?: return
        removed += card.card.id
        session = s.without(card.card.id)
        write { persist(card.card.id, s) }
        showNext()
    }

    private fun showNext(now: Instant = clock.now()) {
        val s = session ?: return
        val next = s.next(now)
        if (next != null) {
            show(next, now)
        } else {
            current = null
            revealed = false
            response = CardResponse()
            val laterCount = s.laterCount(now)
            val phase = if (s.answeredCount == 0) {
                StudyPhase.Empty(laterCount)
            } else {
                StudyPhase.Finished(SessionSummary(ratingCounts.toMap(), totalTimeMs), laterCount, undoStack.isNotEmpty())
            }
            _uiState.value = _uiState.value.copy(phase = phase)
        }
    }

    private fun show(card: StudyCard, now: Instant) {
        current = card
        revealed = false
        response = CardResponse()
        shownAt = now
        preview = queue?.scheduler?.preview(card.card, now).orEmpty()
        render()
    }

    private fun render() {
        val card = current ?: return
        val s = session ?: return
        _uiState.value = _uiState.value.copy(
            phase = StudyPhase.Reviewing(
                card = card,
                revealed = revealed,
                intervals = preview.mapValues { it.value.interval },
                position = s.answeredCount + 1,
                total = s.answeredCount + s.remainingCount,
                canUndo = undoStack.isNotEmpty(),
                response = response,
                suggestedRating = if (revealed) suggestedRating(card) else null,
            ),
        )
    }

    /** Good or Again for an answer the app can check itself: a typed answer or a picked option. */
    private fun suggestedRating(card: StudyCard): Rating? {
        val sides = card.sides
        val correct = when {
            sides.choices != null -> response.chosen?.let { it == sides.correctChoice }
            sides.typeIn -> TypedAnswer.check(response.typed, sides.back).correct
            else -> null
        } ?: return null
        return if (correct) Rating.Good else Rating.Again
    }

    /** Reloads note content for the session's cards, keeping their in-session schedule. */
    private fun refreshContent() {
        val s = session ?: return
        val ids = (s.queue + s.learning).map { it.card.id } + listOfNotNull(current?.card?.id)
        viewModelScope.launch {
            val fresh = cardRepository.getStudyCards(ids.distinct())
            val freshIds = fresh.map { it.card.id }.toSet()
            val latest = session ?: return@launch
            session = ids.filterNot { it in freshIds }.fold(latest.refreshed(fresh)) { acc, id -> acc.without(id) }
            val shown = current
            when {
                shown == null -> Unit
                shown.card.id !in freshIds -> showNext() // deleted while editing
                else -> {
                    val updated = fresh.first { it.card.id == shown.card.id }
                    current = updated.copy(card = shown.card)
                    render()
                }
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }

    companion object {
        const val DECK_ID_KEY = "deckId"

        /** Answer times are capped, as in Anki, so a card left open doesn't skew the stats. */
        private const val MAX_ANSWER_MS = 60_000L
    }
}
