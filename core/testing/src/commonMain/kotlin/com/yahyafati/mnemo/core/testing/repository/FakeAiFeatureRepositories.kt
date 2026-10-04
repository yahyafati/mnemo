package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CoAuthorRepository
import com.yahyafati.mnemo.core.data.repository.GenerationRequest
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.PageTranscription
import com.yahyafati.mnemo.core.data.repository.PdfReadRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.data.repository.StudyAssistRepository
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.BookResult
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.CoAuthorDeck
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.SavedAssistAnswer
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.transformWhile
import java.time.Instant

/**
 * [CardGenerationRepository] that answers each request with [respond]. By default a request
 * returns no cards. Use [streamed] to feed a request's updates by hand, one at a time.
 */
class FakeCardGenerationRepository : CardGenerationRepository {
    val requests = mutableListOf<GenerationRequest>()

    /** The route of each request in [requests], in the same order. */
    val routes = mutableListOf<AiRoute>()
    var respond: (GenerationRequest) -> Flow<GenerationUpdate> = { flowOf(GenerationUpdate.Done()) }

    /** Splits on "---" lines, so tests can make multi-part sources. */
    override fun split(text: String): List<String> = text.split("\n---\n").map { it.trim() }.filter { it.isNotEmpty() }

    override fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate> {
        requests += request
        routes += route
        return respond(request)
    }

    /** Every request answers with [cards] (as copies with fresh ids), then Done. */
    fun answer(vararg cards: GeneratedCard) {
        respond = { request ->
            flowOf(*cards.map { GenerationUpdate.Card(it.copy(id = "gen-${nextId++}", chunkIndex = request.part)) }.toTypedArray(), GenerationUpdate.Done())
        }
    }

    /** Requests read from a channel the test writes to, each ending after its [GenerationUpdate.Done]. */
    fun streamed(): Channel<GenerationUpdate> {
        val channel = Channel<GenerationUpdate>(Channel.UNLIMITED)
        respond = {
            channel.receiveAsFlow().transformWhile { update ->
                emit(update)
                update !is GenerationUpdate.Done
            }
        }
        return channel
    }

    private var nextId = 1

    companion object {
        fun card(front: String, back: String = "", kind: NoteKind = NoteKind.Basic, tags: List<String> = emptyList()) =
            GeneratedCard(id = "card", kind = kind, front = front, back = back, tags = tags)
    }
}

/**
 * [StudyAssistRepository] with canned answers. Like the real one, a complete explanation is kept
 * in [saved] per note, and read back by [savedAnswers].
 */
class FakeStudyAssistRepository : StudyAssistRepository {
    val explained = mutableListOf<Pair<StudyAssist, StudyCard>>()
    val rewritten = mutableListOf<StudyCard>()
    val saved = mutableMapOf<Pair<String, StudyAssist>, SavedAssistAnswer>()
    var explanation: (StudyAssist) -> Flow<AssistUpdate> = { flowOf(AssistUpdate.Text("An explanation."), AssistUpdate.Done()) }
    var rewrite: RewriteOutcome = RewriteOutcome.Proposed(listOf("Rewritten front", "Rewritten back"))

    override fun explain(route: AiRoute, assist: StudyAssist, card: StudyCard): Flow<AssistUpdate> {
        explained += assist to card
        val text = StringBuilder()
        return explanation(assist).onEach { update ->
            when (update) {
                is AssistUpdate.Text -> text.append(update.delta)
                is AssistUpdate.Done -> if (update.failure == null && text.isNotBlank()) {
                    saved[card.note.id to assist] = SavedAssistAnswer(text.toString(), route.provider.name, route.modelId, Instant.EPOCH)
                }
            }
        }
    }

    override suspend fun savedAnswers(card: StudyCard): Map<StudyAssist, SavedAssistAnswer> =
        saved.filterKeys { it.first == card.note.id }.mapKeys { it.key.second }

    override suspend fun rewrite(route: AiRoute, card: StudyCard): RewriteOutcome {
        rewritten += card
        return rewrite
    }
}

/**
 * [SourceRepository] with [results] per source and [books] per EPUB; dictation is driven through [dictation].
 * A PDF opens with the [PdfOpenResult] in [pdfs] for its location and is read with [pdfText], which records what
 * it was asked for in [pdfReads] and [closedPdfs].
 */
class FakeSourceRepository : SourceRepository {
    val results = mutableMapOf<SourceInput, SourceResult>()
    val books = mutableMapOf<SourceInput.Epub, BookResult>()
    val pdfs = mutableMapOf<String, PdfOpenResult>()
    val pdfReads = mutableListOf<Pair<PdfHandle, PageRanges?>>()
    val closedPdfs = mutableListOf<PdfHandle>()

    /** The text of a read of a PDF: by default the pages asked for, written out. */
    var pdfText: (PdfHandle, PageRanges?) -> SourceResult = { handle, pages ->
        SourceResult.Success(SourceText("Text of ${(pages ?: PageRanges.all(handle.info.pageCount)).format()}.", handle.info.title))
    }
    val dictation = MutableSharedFlow<DictationEvent>(extraBufferCapacity = 16)
    var dictationAvailable = true

    override suspend fun read(source: SourceInput): SourceResult =
        results[source] ?: SourceResult.Failure(SourceProblem.FileUnavailable)

    override suspend fun readBook(source: SourceInput.Epub): BookResult =
        books[source] ?: BookResult.Failure(SourceProblem.FileUnavailable)

    override suspend fun openPdf(uri: String): PdfOpenResult = pdfs[uri] ?: PdfOpenResult.Failure(SourceProblem.FileUnavailable)

    override suspend fun readPdf(handle: PdfHandle, pages: PageRanges?): SourceResult {
        pdfReads += handle to pages
        return pdfText(handle, pages)
    }

    /** Every ask of the text layers, and what it answers: by default every page has text, naming its number. */
    val pageTextRequests = mutableListOf<Pair<PdfHandle, List<Int>>>()
    var pageTextLayers: (PdfHandle, List<Int>) -> PdfPageTextsResult = { _, pages ->
        PdfPageTextsResult.Success(pages.filter { it >= 1 }.associateWith { "Text layer of page $it, which has enough letters to count as text." })
    }

    override suspend fun pageTexts(handle: PdfHandle, pages: List<Int>): PdfPageTextsResult {
        pageTextRequests += handle to pages
        return pageTextLayers(handle, pages)
    }

    override suspend fun closePdf(handle: PdfHandle) {
        closedPdfs += handle
    }

    /** Every page asked for as `(handle, page, quality)`; `quality` is null for a thumbnail. */
    val renderedPages = mutableListOf<Triple<PdfHandle, Int, PdfQuality?>>()

    /** What a page renders to: by default a failure, as no file exists. */
    var pdfPage: (PdfHandle, Int) -> PdfPageResult = { _, _ -> PdfPageResult.Failure(SourceProblem.FileUnavailable) }

    override suspend fun renderPdfPage(handle: PdfHandle, page: Int, quality: PdfQuality): PdfPageResult {
        renderedPages += Triple(handle, page, quality)
        return pdfPage(handle, page)
    }

    override suspend fun pdfThumbnail(handle: PdfHandle, page: Int): PdfPageResult {
        renderedPages += Triple(handle, page, null)
        return pdfPage(handle, page)
    }

    override fun isDictationAvailable(): Boolean = dictationAvailable

    override fun dictate(languageTag: String?): Flow<DictationEvent> = dictation
}

/** [CoAuthorRepository] with canned replies; records what each request was given. */
class FakeCoAuthorRepository : CoAuthorRepository {
    val chats = mutableListOf<Pair<CoAuthorDeck, List<ChatTurn>>>()
    val suggestions = mutableListOf<Pair<CoAuthorDeck, String?>>()
    val improved = mutableListOf<StudyCard>()
    var reply: (List<ChatTurn>) -> Flow<AssistUpdate> = { flowOf(AssistUpdate.Text("A reply."), AssistUpdate.Done()) }
    var suggest: () -> Flow<GenerationUpdate> = { flowOf(GenerationUpdate.Done()) }
    var improve: (StudyCard) -> RewriteOutcome = { RewriteOutcome.Proposed(listOf("Clearer ${it.note.field(0)}", it.note.field(1))) }

    override fun chat(route: AiRoute, deck: CoAuthorDeck, history: List<ChatTurn>): Flow<AssistUpdate> {
        chats += deck to history
        return reply(history)
    }

    override fun suggest(route: AiRoute, deck: CoAuthorDeck, focus: String?): Flow<GenerationUpdate> {
        suggestions += deck to focus
        return suggest()
    }

    override suspend fun improve(route: AiRoute, card: StudyCard): RewriteOutcome {
        improved += card
        return improve(card)
    }
}

/**
 * [PdfReadRepository] that answers each page with [respond]: by default a transcription naming its page. [transcribed]
 * records every request as `(route, handle, page, quality)`.
 */
class FakePdfReadRepository : PdfReadRepository {
    val transcribed = mutableListOf<TranscriptionRequest>()
    var respond: suspend (page: Int) -> PageTranscription = { page -> PageTranscription.Success("Transcription of page $page.") }

    data class TranscriptionRequest(val route: AiRoute, val handle: PdfHandle, val page: Int, val quality: PdfQuality)

    override suspend fun transcribe(route: AiRoute, handle: PdfHandle, page: Int, quality: PdfQuality): PageTranscription {
        transcribed += TranscriptionRequest(route, handle, page, quality)
        return respond(page)
    }
}
