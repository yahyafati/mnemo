package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.domain.GeneratedCardProblem
import com.yahyafati.mnemo.core.domain.GeneratedCardValidator
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.CardArchetype
import com.yahyafati.mnemo.core.model.DictationProblem
import com.yahyafati.mnemo.core.model.ExtractDensity
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.ChapterKind
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText

/** Where the source text comes from. Every kind ends up as editable text in the same box. */
enum class SourceKind { Paste, Pdf, Epub, Link, Dictation }

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
    /** The PDF, page or chapter was longer than Mnemo reads. */
    val truncated: Boolean = false,
    /** How many requests [text] is sent in (one per part); 0 for no text. More than one is worth saying. */
    val requests: Int = 0,
    /** The EPUB read for the Epub source, and the chapter whose text is in the box. */
    val book: BookSummary? = null,
    val chapterId: Int? = null,
    val showChapters: Boolean = false,
    /** A run over several chapters of the book (B6), at its current chapter; null when none is going. */
    val batch: BookBatch? = null,
    /** Asking the user to discard the queue before the run starts or goes on to the next chapter. */
    val batchConfirmation: BatchConfirmation? = null,
    /** The device has a speech recognizer; without one the Dictation source isn't offered. */
    val dictationAvailable: Boolean = true,
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

/** The book Smart Extract is reading chapters from. The text stays in the ViewModel; the screen needs only this. */
data class BookSummary(
    val title: String,
    val author: String?,
    val chapters: List<ChapterOption>,
    /** The book was longer than Mnemo reads. */
    val truncated: Boolean,
)

data class ChapterOption(val id: Int, val title: String, val words: Int, val kind: ChapterKind, val truncated: Boolean)

/**
 * Generating for several chapters in book order (docs/epub/ROADMAP.md, B6): [chapters] are the ones to run and
 * [position] the one in the box now. One chapter at a time, each with its own review queue; the run never goes
 * on by itself, so a failure, a rate limit or an unfinished review keeps it where it is.
 */
data class BookBatch(val chapters: List<ChapterOption>, val position: Int) {
    val current: ChapterOption get() = chapters[position]
    val total: Int get() = chapters.size
    val isLast: Boolean get() = position == chapters.lastIndex
}

/** What the queue's unreviewed cards are about to be discarded for. */
enum class BatchConfirmation {
    /** A new run starts. */
    Start,

    /** The run goes on to the next chapter, or ends after the last. */
    Advance,
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

    /** The last chapter of a book run was done (or skipped). */
    data class BatchFinished(val chapters: Int) : ExtractMessage
}

sealed interface SmartExtractAction {
    data class SelectSource(val kind: SourceKind) : SmartExtractAction

    data class TextChanged(val text: String) : SmartExtractAction

    data object ClearText : SmartExtractAction

    data class LinkChanged(val link: String) : SmartExtractAction

    data object FetchLink : SmartExtractAction

    /** A PDF picked through the Storage Access Framework. */
    data class PdfPicked(val uri: String) : SmartExtractAction

    /** An EPUB picked: read it, then choose a chapter. */
    data class EpubPicked(val uri: String) : SmartExtractAction

    /** The chapter whose text goes in the box. */
    data class SelectChapter(val id: Int) : SmartExtractAction

    data object ShowChapters : SmartExtractAction

    /**
     * Book run: go on to the next chapter (skipping this one if it isn't done), or end the run after the last.
     * Asks first when the queue still has cards.
     */
    data object BatchNext : SmartExtractAction

    /** Ends the book run here; the queue and the chapter in the box stay. */
    data object BatchStop : SmartExtractAction

    /** The user agreed to discard the queue ([SmartExtractUiState.batchConfirmation]). */
    data object ConfirmBatchDiscard : SmartExtractAction

    data object CancelBatchDiscard : SmartExtractAction

    data object DismissChapters : SmartExtractAction

    /**
     * A file dropped on the screen (desktop): a PDF is read like a picked one, a text or Markdown
     * file goes into the text box. Other kinds are ignored.
     */
    data class FileDropped(val location: String) : SmartExtractAction

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

/** The kinds of file Smart Extract takes when one is dropped on it, told apart by their name. */
enum class DroppedFile {
    Pdf,
    Text,
    ;

    companion object {
        fun of(location: String): DroppedFile? = when (location.substringAfterLast('.', "").lowercase()) {
            "pdf" -> Pdf
            "txt", "text", "md", "markdown" -> Text
            else -> null
        }
    }
}
