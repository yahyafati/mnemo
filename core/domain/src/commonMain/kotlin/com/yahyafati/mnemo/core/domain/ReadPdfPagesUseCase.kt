package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.PageReadFailure
import com.yahyafati.mnemo.core.data.repository.PageTranscription
import com.yahyafati.mnemo.core.data.repository.PdfReadRepository
import com.yahyafati.mnemo.core.data.repository.SourceRepository
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfPageText
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * What a page is read from (docs/pdf/ROADMAP.md, P5): its text layer, a transcription made earlier in this session,
 * or a new transcription (one request).
 */
enum class PageSource { Layer, Cached, Transcribe }

/** One page of a [PdfReadPlan]. [text] is the layer's text or the cached transcription, null for a page to transcribe. */
data class PlannedPage(val page: Int, val source: PageSource, val text: String? = null)

/**
 * Which pages a read takes from where, decided before anything is sent (so the user can be told how many requests it
 * costs). [pages] are in document order.
 */
data class PdfReadPlan(val handle: PdfHandle, val mode: PdfReadMode, val quality: PdfQuality, val modelId: String, val pages: List<PlannedPage>) {
    /** The pages that need a request: the cost the user confirms. */
    val requests: Int get() = pages.count { it.source == PageSource.Transcribe }

    /** Nothing needs the model: the text layer, or what was transcribed before, covers every page. */
    val needsNoRequests: Boolean get() = requests == 0

    /** Every page has text of its own: the read is the same as a Text read. */
    val isAllLayer: Boolean get() = pages.all { it.source == PageSource.Layer }
}

sealed interface PdfReadPlanResult {
    data class Ready(val plan: PdfReadPlan) : PdfReadPlanResult

    data class Failed(val problem: SourceProblem, val detail: String? = null) : PdfReadPlanResult
}

sealed interface ReadPagesEvent {
    /** A request for [page], number [index] (0-based) of the plan's [total] pages, is going out. */
    data class Transcribing(val index: Int, val total: Int, val page: Int) : ReadPagesEvent

    /** [done] of [total] pages are in; [text] is all of the text so far. */
    data class Progress(val done: Int, val total: Int, val text: String) : ReadPagesEvent

    /** The page at [index] couldn't be read. [text] is everything before it; Resume goes on at [index]. */
    data class Failed(val index: Int, val page: Int, val failure: PageReadFailure, val text: String) : ReadPagesEvent

    /** Every page is in. [blankPages] came out empty and were left out, and [truncated] says the text hit the limit. */
    data class Finished(val text: String, val transcribed: Int, val blankPages: Int, val truncated: Boolean) : ReadPagesEvent
}

/**
 * Transcriptions made in one Smart Extract session, so changing the page range or switching back from Text doesn't pay
 * again. Keyed by the PDF, page, model and resolution; a blank page is kept as an empty string. Not thread-safe: one
 * ViewModel owns one.
 */
class PageTranscriptionCache {
    private data class Key(val pdf: String, val page: Int, val model: String, val quality: PdfQuality)

    private val texts = HashMap<Key, String>()

    operator fun get(handle: PdfHandle, page: Int, model: String, quality: PdfQuality): String? = texts[Key(handle.id, page, model, quality)]

    operator fun set(handle: PdfHandle, page: Int, model: String, quality: PdfQuality, text: String) {
        texts[Key(handle.id, page, model, quality)] = text
    }

    fun clear(handle: PdfHandle) {
        texts.keys.removeAll { it.pdf == handle.id }
    }

    fun clear() = texts.clear()
}

/**
 * Reading the pages of a PDF into Smart Extract's box when the text layer isn't enough (docs/pdf/ROADMAP.md, P5;
 * ADR 0014). [plan] says which pages come from where; [run] then goes through them in order, one request per page
 * that needs one, and keeps what it has when one fails so Resume can go on from it. Nothing is saved.
 *
 * In **Auto** a page with at least [PdfPageText.MIN_CHARS] characters of text uses it and the others are
 * transcribed; in **Read pages with AI** every page is. A page that renders blank is an empty page, not an error: scans
 * are full of them.
 */
class ReadPdfPagesUseCase(
    private val sources: SourceRepository,
    private val reading: PdfReadRepository,
) {
    suspend fun plan(handle: PdfHandle, pages: List<Int>, mode: PdfReadMode, quality: PdfQuality, modelId: String, cache: PageTranscriptionCache): PdfReadPlanResult {
        require(mode.usesAi) { "Text mode reads the text layer as one piece: use SourceRepository.readPdf" }
        val layers = if (mode == PdfReadMode.Auto) {
            when (val result = sources.pageTexts(handle, pages)) {
                is PdfPageTextsResult.Success -> result.texts
                is PdfPageTextsResult.Failure -> return PdfReadPlanResult.Failed(result.problem, result.detail)
            }
        } else {
            emptyMap()
        }
        val planned = pages.distinct().sorted().map { page ->
            val layer = layers[page]
            val cached = cache[handle, page, modelId, quality]
            when {
                layer != null && PdfPageText.hasText(layer) -> PlannedPage(page, PageSource.Layer, layer)
                cached != null -> PlannedPage(page, PageSource.Cached, cached)
                else -> PlannedPage(page, PageSource.Transcribe)
            }
        }
        return PdfReadPlanResult.Ready(PdfReadPlan(handle, mode, quality, modelId, planned))
    }

    /**
     * Goes through [plan] from page index [from] (0-based; to resume after a [ReadPagesEvent.Failed]), joining the
     * pages' text after [before] (what the box already holds when resuming) with blank lines. Pages are read one at a
     * time, so a cancelled collector stops the requests.
     */
    fun run(route: AiRoute, plan: PdfReadPlan, cache: PageTranscriptionCache, from: Int = 0, before: String = ""): Flow<ReadPagesEvent> = flow {
        val total = plan.pages.size
        val text = StringBuilder(before)
        var transcribed = 0
        var blank = 0
        var truncated = false

        fun append(page: String) {
            if (page.isBlank()) return
            if (text.isNotEmpty()) text.append("\n\n")
            text.append(page)
        }

        for (index in from until total) {
            val planned = plan.pages[index]
            when (planned.source) {
                PageSource.Layer -> append(planned.text.orEmpty())
                PageSource.Cached -> if (planned.text.isNullOrBlank()) blank++ else append(planned.text)
                PageSource.Transcribe -> {
                    emit(ReadPagesEvent.Transcribing(index, total, planned.page))
                    when (val result = reading.transcribe(route, plan.handle, planned.page, plan.quality)) {
                        is PageTranscription.Success -> {
                            cache[plan.handle, planned.page, plan.modelId, plan.quality] = result.text
                            transcribed++
                            append(result.text)
                        }
                        is PageTranscription.Failure -> {
                            val failure = result.failure
                            if (failure is PageReadFailure.Page && failure.problem == SourceProblem.BlankPage) {
                                cache[plan.handle, planned.page, plan.modelId, plan.quality] = ""
                                blank++
                            } else {
                                emit(ReadPagesEvent.Failed(index, planned.page, failure, text.toString()))
                                return@flow
                            }
                        }
                    }
                }
            }
            if (text.length >= SourceText.MAX_CHARS) {
                // The box holds this much: more pages would cost requests for text that is cut off.
                truncated = index < total - 1 || text.length > SourceText.MAX_CHARS
                text.setLength(SourceText.MAX_CHARS)
                emit(ReadPagesEvent.Progress(index + 1, total, text.toString()))
                break
            }
            emit(ReadPagesEvent.Progress(index + 1, total, text.toString()))
        }
        emit(ReadPagesEvent.Finished(text.toString(), transcribed, blank, truncated))
    }
}
