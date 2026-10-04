package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.PageReadFailure
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
import com.yahyafati.mnemo.core.model.DisclosureStep
import com.yahyafati.mnemo.core.model.PageRangeError
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText

/** Where the source text comes from. Every kind ends up as editable text in the same box. */
enum class SourceKind { Paste, Pdf, Epub, Link, Dictation }

data class SmartExtractUiState(
    val isLoading: Boolean = true,
    /** The provider and model Smart Extract uses; null shows the setup prompt. */
    val route: AiRoute? = null,
    /** The provider and model that read PDF pages as images (docs/pdf/ROADMAP.md, P5); null when none is set up. */
    val readRoute: AiRoute? = null,
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
    /** The page read was a disambiguation page: a list of other articles, worth saying before cards are made from it. */
    val disambiguation: Boolean = false,
    /** How many requests [text] is sent in (one per part); 0 for no text. More than one is worth saying. */
    val requests: Int = 0,
    /** The sections of the page the link was read from (W4), null when the page has none to pick from. */
    val sections: SectionsSummary? = null,
    val showSections: Boolean = false,
    /** Asking the user to discard their edits to the text before the chosen sections replace it. */
    val sectionsConfirmation: Boolean = false,
    /** The PDF opened for the Pdf source (docs/pdf/ROADMAP.md, P1), whose pages the box holds; null when none is open. */
    val pdf: PdfSummary? = null,
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
    /** Which notice [disclosure] is showing: what the text of a request holds, or that page images are sent (ADR 0014). */
    val disclosureStep: DisclosureStep = DisclosureStep.Text,
    val showDeckDialog: Boolean = false,
    /** Asking the user to confirm adding cards to a deck that already has some. */
    val nonEmptyDeck: NonEmptyDeck? = null,
    /** A one-off message for the snackbar; cleared once shown. */
    val message: ExtractMessage? = null,
) {
    val wordCount: Int get() = SourceText.countWords(text)

    /** About how many cards generating would give (before duplicates are dropped). */
    val estimatedCards: Int
        get() = if (wordCount == 0) 0 else maxOf(options.targetCards(wordCount), wordCount / options.density.wordsPerCard)

    val isGenerating: Boolean get() = generation is GenerationState.Running

    /** The model PDF pages are read with can see images, so Auto and Read pages with AI can be chosen. */
    val canReadPages: Boolean get() = readRoute?.capabilities?.vision == true

    val canGenerate: Boolean
        get() = route != null && text.isNotBlank() && !isGenerating && !reading && options.archetypes.isNotEmpty()

    /** Cards that can be saved now: valid, and not being regenerated. */
    val acceptable: List<QueueItem> get() = queue.filter { it.problem == null && !it.regenerating }

    val deckPath: String? get() = decks.firstOrNull { it.id == deckId }?.path
}

/**
 * The sections of a page (a Wikipedia article's), whose chosen ones make up the text in the box. The text stays in
 * the ViewModel; the screen needs only this.
 */
data class SectionsSummary(val options: List<SectionOption>, val selected: Set<Int>) {
    val selectedWords: Int get() = options.filter { it.id in selected }.sumOf { it.words }
}

/** [title] is null for the lead, the text above the first heading; [level] is the heading's (2 for `h2`), 0 for the lead. */
data class SectionOption(val id: Int, val title: String?, val level: Int, val words: Int)

/**
 * The PDF the Pdf source has open. The file's copy and its handle stay in the ViewModel; the screen needs only this.
 * [pages] is the Pages field as typed: empty means every page, which is allowed up to [PdfInfo.MAX_PAGES].
 */
data class PdfSummary(
    val title: String?,
    val pageCount: Int,
    val pages: String,
    /** What is wrong with [pages], shown under the field; the text in the box isn't changed until it is fixed and applied. */
    val error: PdfPagesError? = null,
    /** Asking the user to discard their edits to the text before the chosen pages replace it. */
    val replaceConfirmation: Boolean = false,
    /** The printed page numbers, as `i–xii, 1–600`, when they differ from the positions (P2). */
    val printedPages: String? = null,
    /** The bookmarks to choose chapters from; the Chapters button needs two or more (P2). */
    val chapters: List<PdfChapterOption> = emptyList(),
    /** The chapters whose pages are all in [pages]; what the picker shows ticked. */
    val selectedChapters: Set<Int> = emptySet(),
    val showChapters: Boolean = false,
    /** How the pages are read the next time (P5); an AI mode needs [SmartExtractUiState.canReadPages]. */
    val mode: PdfReadMode = PdfReadMode.Text,
    /** The resolution page images are sent at in an AI mode. */
    val quality: PdfQuality = PdfQuality.Standard,
    /** How many of the pages the box was last read from have no text layer (scanned?); 0 when none or not known yet. */
    val pagesWithoutText: Int = 0,
    /** Reading pages with AI is going on or stopped on an error; null otherwise. */
    val readState: PdfReadState? = null,
    /** Waiting for the user to agree to the requests an AI read will make. */
    val readConfirmation: PdfReadConfirmation? = null,
    /** Some of the text in the box was written by a model reading the pages: it can be reported. */
    val transcribed: Boolean = false,
)

/** Reading PDF pages with a vision model (P5). The text so far is in the box either way. */
sealed interface PdfReadState {
    /** [done] of [total] pages are in the box; [page] is the one being sent to the model, null while a page's own text is taken. */
    data class Running(val done: Int, val total: Int, val page: Int?) : PdfReadState

    /** The request for [page] failed after [done] of [total] pages; Resume goes on from it. */
    data class Failed(val failure: PageReadFailure, val page: Int, val done: Int, val total: Int) : PdfReadState
}

/** What an AI read of the PDF's pages is about to do, said before anything is sent: [requests] requests for [pages] pages. */
data class PdfReadConfirmation(
    val pages: Int,
    val requests: Int,
    val providerName: String,
    val modelId: String,
    val quality: PdfQuality,
)

/**
 * A bookmark in the chapter picker. [page] is its position in the file, the number the Pages field takes, and
 * [printedPage] the label printed on it when the PDF has labels; [pages] is how many pages it runs for.
 */
data class PdfChapterOption(val id: Int, val title: String, val level: Int, val page: Int, val printedPage: String?, val pages: Int)

sealed interface PdfPagesError {
    /** The field isn't a page range, or names a page the PDF doesn't have. */
    data class Invalid(val error: PageRangeError) : PdfPagesError

    /** [selected] pages are more than one read takes. */
    data class TooMany(val selected: Int, val limit: Int) : PdfPagesError
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

/** The deck the accepted cards are about to join, which already holds [cards] cards. */
data class NonEmptyDeck(val path: String, val cards: Int)

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

    /** [pages] pages of a PDF came out blank when read with AI and were left out. */
    data class PagesBlank(val pages: Int) : ExtractMessage
}

sealed interface SmartExtractAction {
    data class SelectSource(val kind: SourceKind) : SmartExtractAction

    data class TextChanged(val text: String) : SmartExtractAction

    data object ClearText : SmartExtractAction

    data class LinkChanged(val link: String) : SmartExtractAction

    data object FetchLink : SmartExtractAction

    /** A PDF picked through the Storage Access Framework. */
    data class PdfPicked(val uri: String) : SmartExtractAction

    /** The Pages field of the open PDF was edited; the box keeps its text until [ApplyPdfPages]. */
    data class PdfPagesChanged(val text: String) : SmartExtractAction

    /** Reads the pages in the field into the box. Asks first when the text has been edited. */
    data object ApplyPdfPages : SmartExtractAction

    /** How the pages are read next time. An AI mode is ignored while no model reads images. */
    data class SetPdfReadMode(val mode: PdfReadMode) : SmartExtractAction

    data class SetPdfQuality(val quality: PdfQuality) : SmartExtractAction

    /** "Read them with AI" on the pages that had no text: switches to Auto and reads. */
    data object ReadPdfPagesWithAi : SmartExtractAction

    /** The user agreed to the requests ([PdfReadConfirmation]). */
    data object ConfirmPdfRead : SmartExtractAction

    data object DismissPdfReadConfirmation : SmartExtractAction

    /** Stops an AI read; the pages already read stay in the box. */
    data object CancelPdfRead : SmartExtractAction

    /** After a failed AI read: goes on from the page that failed. */
    data object ResumePdfRead : SmartExtractAction

    /** The user agreed to replace their edits ([PdfSummary.replaceConfirmation]). */
    data object ConfirmPdfReplace : SmartExtractAction

    data object CancelPdfReplace : SmartExtractAction

    /** Opens the open PDF's chapter picker. */
    data object ShowPdfChapters : SmartExtractAction

    /** Ticks or unticks a chapter: its pages are added to the Pages field or taken out of it. */
    data class TogglePdfChapter(val id: Int) : SmartExtractAction

    /** Closes the picker and leaves the field as it is. */
    data object DismissPdfChapters : SmartExtractAction

    /** Closes the picker and reads the pages in the field into the box. */
    data object ApplyPdfChapters : SmartExtractAction

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

    data object ShowSections : SmartExtractAction

    data object DismissSections : SmartExtractAction

    /** Adds the section to the text in the box, or takes it out. Asks first when the text has been edited. */
    data class ToggleSection(val id: Int) : SmartExtractAction

    /** Every section, or none. */
    data class SelectAllSections(val all: Boolean) : SmartExtractAction

    /** The user agreed to replace their edits ([SmartExtractUiState.sectionsConfirmation]). */
    data object ConfirmSectionReplace : SmartExtractAction

    data object CancelSectionReplace : SmartExtractAction

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

    /** The user agreed to add the cards to the deck that already has some ([SmartExtractUiState.nonEmptyDeck]). */
    data object ConfirmNonEmptyDeck : SmartExtractAction

    data object CancelNonEmptyDeck : SmartExtractAction

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
