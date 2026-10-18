package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourcePart
import com.yahyafati.mnemo.core.model.SourceProblem
import kotlinx.coroutines.flow.Flow

/**
 * Smart Extract's AI requests (ARCHITECTURE §5.2, steps 2–4). Nothing here touches the database
 * except the token-usage log: cards are only proposals until the user accepts them.
 */
interface CardGenerationRepository {
    /**
     * [text] split into the parts sent one request each, in order, each with its path (ADR 0015, C2). [root] is where
     * the text is: the book and the chapter, or the page's or document's title; empty for pasted or dictated text.
     */
    fun split(text: String, root: List<String> = emptyList()): List<SourcePart>

    /**
     * Asks [route] for cards from one part of a source. Emits each card as soon as the reply
     * completes it, then exactly one [GenerationUpdate.Done]. Cards emitted before a failure are
     * still good. Tokens used are logged against the provider and task.
     */
    fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate>
}

/**
 * Pages of an opened PDF sent as images with a request (ADR 0014, P6): [pages] (1-based, in order) are
 * rendered at [quality] and attached. A page that renders blank is left out; the request is not made if none is left.
 */
data class PageImages(val handle: PdfHandle, val quality: PdfQuality, val pages: List<Int>)

/** A page that could not be drawn for a request, and why. A blank page isn't one: it is skipped. */
data class UnreadablePage(val page: Int, val problem: SourceProblem)

data class GenerationRequest(
    /**
     * One part of the source, from [CardGenerationRepository.split], with its path. With [pages] its text is only their
     * text layer, which may be empty: the images are the source.
     */
    val source: SourcePart,
    val options: ExtractOptions,
    /** 0-based part, and how many parts the source has. */
    val part: Int = 0,
    val parts: Int = 1,
    val title: String? = null,
    /** Fronts the model shouldn't repeat: cards already in the queue. */
    val avoid: List<String> = emptyList(),
    /** Regenerating: the card to replace. The reply is one card. */
    val replacing: GeneratedCard? = null,
    /** Cards from page images: the pages sent as images; null for a text source. */
    val pages: PageImages? = null,
) {
    val text: String get() = source.text
}

sealed interface GenerationUpdate {
    /** A card as the model wrote it; not yet validated. */
    data class Card(val card: GeneratedCard) : GenerationUpdate

    /**
     * Always last. [failure] is set if the request failed (possibly after some cards); [unreadablePage] if a page
     * image could not be made, in which case nothing was sent.
     */
    data class Done(val failure: AiFailure? = null, val unreadablePage: UnreadablePage? = null) : GenerationUpdate
}
