package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.domain.GeneratedCardProblem
import com.yahyafati.mnemo.core.domain.GeneratedCardValidator
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText

/** Where the source text comes from. Every kind ends up as editable text in the same box. */
enum class SourceKind { Paste, Pdf, Link, Dictation }

data class SmartExtractUiState(
    val isLoading: Boolean = true,
    /** The provider and model Smart Extract uses; null shows the setup prompt. */
    val route: AiRoute? = null,
    val decks: List<DeckOption> = emptyList(),
    val deckId: String? = null,
    val sourceKind: SourceKind = SourceKind.Paste,
    /** The text that will be sent: pasted, dictated, or read from a PDF or link, then edited. */
    val text: String = "",
    /** The PDF's or page's title, sent along as context. */
    val title: String? = null,
    val link: String = "",
    val reading: Boolean = false,
    val sourceProblem: SourceProblem? = null,
    /** The PDF or page was longer than Mnemo reads. */
    val truncated: Boolean = false,
    val dictation: DictationState = DictationState.Off,
    val options: ExtractOptions = ExtractOptions(),
    val generation: GenerationState = GenerationState.Idle,
    val queue: List<QueueItem> = emptyList(),
    /** Proposed cards dropped this session, as duplicates or as unusable. */
    val duplicatesSkipped: Int = 0,
    val invalidSkipped: Int = 0,
    val editingId: String? = null,
    /** Waiting for the user to accept the provider notice before sending anything. */
    val disclosure: AiRoute? = null,
    val showDeckDialog: Boolean = false,
    /** A one-off message for the snackbar; cleared once shown. */
    val message: ExtractMessage? = null,
) {
    val wordCount: Int get() = SourceText.countWords(text)

    /** About how many cards generating would give (before duplicates are dropped). */
    val estimatedCards: Int
        get() = if (wordCount == 0) 0 else maxOf(options.targetCards(wordCount), wordCount / options.density.wordsPerCard)

    val isGenerating: Boolean get() = generation is GenerationState.Running

    val canGenerate: Boolean
        get() = route != null && text.isNotBlank() && !isGenerating && !reading && options.archetypes.isNotEmpty()

    /** Cards that can be saved now: valid, and not being regenerated. */
    val acceptable: List<QueueItem> get() = queue.filter { it.problem == null && !it.regenerating }

    val deckPath: String? get() = decks.firstOrNull { it.id == deckId }?.path
}

/** A proposed card in the review queue. [source] is the part of the text it came from, for regenerating. */
data class QueueItem(
    val card: GeneratedCard,
    val source: String,
    val regenerating: Boolean = false,
) {
    val problem: GeneratedCardProblem? get() = GeneratedCardValidator.problem(card)
}

sealed interface GenerationState {
    data object Idle : GenerationState

    /** Working on [part] (0-based) of [parts]. */
    data class Running(val part: Int, val parts: Int) : GenerationState

    /** Every part is done; [added] cards joined the queue. */
    data class Done(val added: Int) : GenerationState

    /** Stopped at [part] of [parts]; Retry resumes there. Cards already received stay. */
    data class Failed(val failure: AiFailure, val part: Int, val parts: Int) : GenerationState
}

sealed interface DictationState {
    data object Off : DictationState

    /** Listening; [partial] is the phrase being heard. */
    data class Listening(val partial: String = "") : DictationState

    data class Failed(val problem: DictationProblem) : DictationState
}

sealed interface ExtractMessage {
    data class Accepted(val cards: Int, val deckPath: String) : ExtractMessage

    /** Regenerating gave only the same card, or ones that exist already. */
    data object NothingNew : ExtractMessage

    data class RegenerateFailed(val failure: AiFailure) : ExtractMessage
}

sealed interface SmartExtractAction {
    data class SelectSource(val kind: SourceKind) : SmartExtractAction

    data class TextChanged(val text: String) : SmartExtractAction

    data object ClearText : SmartExtractAction

    data class LinkChanged(val link: String) : SmartExtractAction

    data object FetchLink : SmartExtractAction

    /** A PDF picked through the Storage Access Framework. */
    data class PdfPicked(val uri: String) : SmartExtractAction

    /** The microphone permission was granted (or already held): start listening. */
    data object StartDictation : SmartExtractAction

    data object StopDictation : SmartExtractAction

    data object DictationPermissionDenied : SmartExtractAction

    data class SelectDeck(val deckId: String) : SmartExtractAction

    data object ShowDeckDialog : SmartExtractAction

    data object DismissDeckDialog : SmartExtractAction

    data class CreateDeck(val path: String, val category: String, val description: String) : SmartExtractAction

    data class SetDensity(val density: ExtractDensity) : SmartExtractAction

    data class ToggleArchetype(val archetype: CardArchetype) : SmartExtractAction

    /** Null: the same language as the source. */
    data class SetLanguage(val language: String?) : SmartExtractAction

    data object Generate : SmartExtractAction

    data object Stop : SmartExtractAction

    /** After a failure: continue from the part that failed. */
    data object Retry : SmartExtractAction

    data object AcceptDisclosure : SmartExtractAction

    data object DismissDisclosure : SmartExtractAction

    data class Accept(val id: String) : SmartExtractAction

    data object AcceptAll : SmartExtractAction

    data class Discard(val id: String) : SmartExtractAction

    data object DiscardAll : SmartExtractAction

    data class Regenerate(val id: String) : SmartExtractAction

    data class Edit(val id: String) : SmartExtractAction

    data class EditFront(val id: String, val text: String) : SmartExtractAction

    data class EditBack(val id: String, val text: String) : SmartExtractAction

    data object DoneEditing : SmartExtractAction

    data object MessageShown : SmartExtractAction
}
