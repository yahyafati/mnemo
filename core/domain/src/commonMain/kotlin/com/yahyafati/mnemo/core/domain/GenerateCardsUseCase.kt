package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.GenerationRequest
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.PageImages
import com.yahyafati.mnemo.core.data.repository.SummaryResult
import com.yahyafati.mnemo.core.data.repository.UnreadablePage
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.Overlap
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourcePart
import com.yahyafati.mnemo.core.model.SourceProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Cards from page images (ADR 0014, P6): the PDF's pages in groups, one request each. Part `i` of the run is
 * sent with the images of `batches[i]`, rendered at [quality]; its text is only the pages' text layer.
 */
data class PageBatches(val handle: PdfHandle, val quality: PdfQuality, val batches: List<List<Int>>) {
    fun images(part: Int) = PageImages(handle, quality, batches[part])
}

/** A Smart Extract run: a source split into [parts] (each with its path), generated from [fromPart] on. */
data class ExtractRequest(
    val parts: List<SourcePart>,
    val options: ExtractOptions,
    /** The destination deck, whose notes aren't repeated; null skips that check. */
    val deckId: String?,
    val title: String? = null,
    /** Resume after a failure: the parts before this one are done. */
    val fromPart: Int = 0,
    /** Cards already in the review queue: not repeated, and named to the model so it avoids them. */
    val known: List<GeneratedCard> = emptyList(),
    /** Cards from page images: each of [parts] goes with its group of pages; null for a text source. */
    val pages: PageBatches? = null,
    /** The summary of the whole source, sent as context with every part (ADR 0015, C4); blank for none. */
    val context: String = "",
    /**
     * The whole source, to summarise before the first part; null asks for no summary. The summary then replaces
     * [context], and a failed one ends the run before any part is sent.
     */
    val summarize: String? = null,
) {
    init {
        require(pages == null || pages.batches.size == parts.size) { "Every part needs its group of pages" }
    }
}

sealed interface ExtractEvent {
    /** The context summary is being written; the first part follows it. */
    data object SummaryStarted : ExtractEvent

    /** The context summary is written: [text] goes with every part. */
    data class SummaryReady(val text: String) : ExtractEvent

    /** The summary request failed, so no part was sent; a retry starts again, or goes on without a summary. */
    data class SummaryFailed(val failure: AiFailure) : ExtractEvent

    data class PartStarted(val part: Int, val parts: Int) : ExtractEvent

    /** A valid, new card for the review queue. */
    data class Card(val card: GeneratedCard) : ExtractEvent

    /** The model proposed a card that was dropped. */
    data class Skipped(val reason: SkipReason) : ExtractEvent

    /** The request for [part] failed. Cards already emitted stay; a retry can resume at [part]. */
    data class Failed(val part: Int, val failure: AiFailure) : ExtractEvent

    /** A page of [part] couldn't be drawn, so its request was not made. Cards already emitted stay; a retry can resume at [part]. */
    data class PageUnreadable(val part: Int, val page: Int, val problem: SourceProblem) : ExtractEvent

    /** Every part is done. */
    data object Finished : ExtractEvent
}

enum class SkipReason { Invalid, Duplicate }

/**
 * Source → parts → AI → validated cards (ARCHITECTURE §5.2). Parts go one at a time, in order,
 * so cards arrive in the source's order. Each card is validated ([GeneratedCardValidator]) and
 * dropped if the deck or the queue already has it. Nothing is saved.
 *
 * Only the source text and the fronts of queued cards are sent; the deck's own notes never leave
 * the device (they are compared locally).
 */
class GenerateCardsUseCase(
    private val generation: CardGenerationRepository,
    private val cardRepository: CardRepository,
    private val summarizeSource: SummarizeSourceUseCase,
) {
    /** [text] split into the parts sent one request each; [root] begins their paths and [overlap] gives some the end of the part before (see [CardGenerationRepository.split]). */
    fun split(text: String, root: List<String> = emptyList(), overlap: Overlap = Overlap.WithinSections): List<SourcePart> = generation.split(text, root, overlap)

    /** How many requests a source of [parts] parts takes with [options], the summary's included. */
    fun requests(parts: Int, options: ExtractOptions): Int = options.requests(parts)

    operator fun invoke(route: AiRoute, request: ExtractRequest): Flow<ExtractEvent> = flow {
        var context = request.context
        request.summarize?.let { source ->
            emit(ExtractEvent.SummaryStarted)
            when (val result = summarizeSource(route, source, request.title, request.options.language)) {
                is SummaryResult.Success -> {
                    context = result.text
                    emit(ExtractEvent.SummaryReady(result.text))
                }
                is SummaryResult.Failure -> {
                    emit(ExtractEvent.SummaryFailed(result.failure))
                    return@flow
                }
            }
        }
        val seen = HashSet<String>()
        request.deckId?.let { deckId -> cardRepository.getNoteFields(deckId).forEach { seen += GeneratedCardValidator.key(it.firstOrNull().orEmpty()) } }
        request.known.forEach { seen += GeneratedCardValidator.key(it.front) }
        val avoid = request.known.map { it.front }.toMutableList()

        for (part in request.fromPart until request.parts.size) {
            emit(ExtractEvent.PartStarted(part, request.parts.size))
            var failure: AiFailure? = null
            var unreadable: UnreadablePage? = null
            val generationRequest = GenerationRequest(
                source = request.parts[part],
                options = request.options,
                part = part,
                parts = request.parts.size,
                title = request.title,
                avoid = avoid.takeLast(MAX_AVOID),
                pages = request.pages?.images(part),
                context = context,
            )
            generation.generate(route, generationRequest).collect { update ->
                when (update) {
                    is GenerationUpdate.Card -> {
                        val card = GeneratedCardValidator.validate(update.card)
                        when {
                            card == null -> emit(ExtractEvent.Skipped(SkipReason.Invalid))
                            !seen.add(GeneratedCardValidator.key(card.front)) -> emit(ExtractEvent.Skipped(SkipReason.Duplicate))
                            else -> {
                                avoid += card.front
                                emit(ExtractEvent.Card(card))
                            }
                        }
                    }
                    is GenerationUpdate.Done -> {
                        failure = update.failure
                        unreadable = update.unreadablePage
                    }
                }
            }
            unreadable?.let {
                emit(ExtractEvent.PageUnreadable(part, it.page, it.problem))
                return@flow
            }
            failure?.let {
                emit(ExtractEvent.Failed(part, it))
                return@flow
            }
        }
        emit(ExtractEvent.Finished)
    }

    private companion object {
        const val MAX_AVOID = 60
    }
}

sealed interface RegenerateResult {
    data class Replaced(val card: GeneratedCard) : RegenerateResult

    /** The model only repeated the card, or cards that exist already. */
    data object NothingNew : RegenerateResult

    data class Failed(val failure: AiFailure) : RegenerateResult

    /** A page of the card's group couldn't be drawn, so nothing was sent. */
    data class PageUnreadable(val page: Int, val problem: SourceProblem) : RegenerateResult
}

/**
 * "Regenerate" on one queued card: one new card from [source], the part of the source the card
 * came from, with its path. The queue keeps each card's part, since the text box may have changed since. A card made
 * from page images keeps its group of pages ([pages]) too, and the same images are sent again.
 */
class RegenerateCardUseCase(
    private val generation: CardGenerationRepository,
    private val cardRepository: CardRepository,
) {
    suspend operator fun invoke(
        route: AiRoute,
        card: GeneratedCard,
        source: SourcePart,
        options: ExtractOptions,
        deckId: String?,
        title: String? = null,
        known: List<GeneratedCard> = emptyList(),
        pages: PageImages? = null,
        context: String = "",
    ): RegenerateResult {
        val seen = HashSet<String>()
        deckId?.let { id -> cardRepository.getNoteFields(id).forEach { seen += GeneratedCardValidator.key(it.firstOrNull().orEmpty()) } }
        known.forEach { seen += GeneratedCardValidator.key(it.front) }
        seen += GeneratedCardValidator.key(card.front)

        var replacement: GeneratedCard? = null
        var failure: AiFailure? = null
        var unreadable: UnreadablePage? = null
        val request = GenerationRequest(
            source = source,
            options = options,
            part = card.chunkIndex,
            title = title,
            avoid = known.map { it.front }.takeLast(MAX_AVOID),
            replacing = card,
            pages = pages,
            context = context,
        )
        generation.generate(route, request).collect { update ->
            when (update) {
                is GenerationUpdate.Card -> if (replacement == null) {
                    GeneratedCardValidator.validate(update.card)
                        ?.takeIf { GeneratedCardValidator.key(it.front) !in seen }
                        ?.let { replacement = it }
                }
                is GenerationUpdate.Done -> {
                    failure = update.failure
                    unreadable = update.unreadablePage
                }
            }
        }
        return replacement?.let { RegenerateResult.Replaced(it) }
            ?: unreadable?.let { RegenerateResult.PageUnreadable(it.page, it.problem) }
            ?: failure?.let { RegenerateResult.Failed(it) }
            ?: RegenerateResult.NothingNew
    }

    private companion object {
        const val MAX_AVOID = 60
    }
}
