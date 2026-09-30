package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import kotlinx.coroutines.flow.Flow

/**
 * Smart Extract's AI requests (ARCHITECTURE §5.2, steps 2–4). Nothing here touches the database
 * except the token-usage log: cards are only proposals until the user accepts them.
 */
interface CardGenerationRepository {
    /** [text] split into the parts sent one request each, in order. */
    fun split(text: String): List<String>

    /**
     * Asks [route] for cards from one part of a source. Emits each card as soon as the reply
     * completes it, then exactly one [GenerationUpdate.Done]. Cards emitted before a failure are
     * still good. Tokens used are logged against the provider and task.
     */
    fun generate(route: AiRoute, request: GenerationRequest): Flow<GenerationUpdate>
}

data class GenerationRequest(
    /** One part of the source, from [CardGenerationRepository.split]. */
    val text: String,
    val options: ExtractOptions,
    /** 0-based part, and how many parts the source has. */
    val part: Int = 0,
    val parts: Int = 1,
    val title: String? = null,
    /** Fronts the model shouldn't repeat: cards already in the queue. */
    val avoid: List<String> = emptyList(),
    /** Regenerating: the card to replace. The reply is one card. */
    val replacing: GeneratedCard? = null,
)

sealed interface GenerationUpdate {
    /** A card as the model wrote it; not yet validated. */
    data class Card(val card: GeneratedCard) : GenerationUpdate

    /** Always last. [failure] is set if the request failed (possibly after some cards). */
    data class Done(val failure: AiFailure? = null) : GenerationUpdate
}
