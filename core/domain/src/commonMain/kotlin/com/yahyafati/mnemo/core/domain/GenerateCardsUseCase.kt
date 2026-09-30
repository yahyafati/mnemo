package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.CardGenerationRepository
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.GenerationRequest
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.GeneratedCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** A Smart Extract run: a source split into [parts], generated from [fromPart] on. */
data class ExtractRequest(
    val parts: List<String>,
    val options: ExtractOptions,
    /** The destination deck, whose notes aren't repeated; null skips that check. */
    val deckId: String?,
    val title: String? = null,
    /** Resume after a failure: the parts before this one are done. */
    val fromPart: Int = 0,
    /** Cards already in the review queue: not repeated, and named to the model so it avoids them. */
    val known: List<GeneratedCard> = emptyList(),
)

sealed interface ExtractEvent {
    data class PartStarted(val part: Int, val parts: Int) : ExtractEvent

    /** A valid, new card for the review queue. */
    data class Card(val card: GeneratedCard) : ExtractEvent

    /** The model proposed a card that was dropped. */
    data class Skipped(val reason: SkipReason) : ExtractEvent

    /** The request for [part] failed. Cards already emitted stay; a retry can resume at [part]. */
    data class Failed(val part: Int, val failure: AiFailure) : ExtractEvent

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
) {
    /** [text] split into the parts sent one request each. */
    fun split(text: String): List<String> = generation.split(text)

    operator fun invoke(route: AiRoute, request: ExtractRequest): Flow<ExtractEvent> = flow {
        val seen = HashSet<String>()
        request.deckId?.let { deckId -> cardRepository.getNoteFields(deckId).forEach { seen += GeneratedCardValidator.key(it.firstOrNull().orEmpty()) } }
        request.known.forEach { seen += GeneratedCardValidator.key(it.front) }
        val avoid = request.known.map { it.front }.toMutableList()

        for (part in request.fromPart until request.parts.size) {
            emit(ExtractEvent.PartStarted(part, request.parts.size))
            var failure: AiFailure? = null
            val generationRequest = GenerationRequest(
                text = request.parts[part],
                options = request.options,
                part = part,
                parts = request.parts.size,
                title = request.title,
                avoid = avoid.takeLast(MAX_AVOID),
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
                    is GenerationUpdate.Done -> failure = update.failure
                }
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
}

/**
 * "Regenerate" on one queued card: one new card from [source], the part of the source the card
 * came from. The queue keeps each card's part, since the text box may have changed since.
 */
class RegenerateCardUseCase(
    private val generation: CardGenerationRepository,
    private val cardRepository: CardRepository,
) {
    suspend operator fun invoke(
        route: AiRoute,
        card: GeneratedCard,
        source: String,
        options: ExtractOptions,
        deckId: String?,
        title: String? = null,
        known: List<GeneratedCard> = emptyList(),
    ): RegenerateResult {
        val seen = HashSet<String>()
        deckId?.let { id -> cardRepository.getNoteFields(id).forEach { seen += GeneratedCardValidator.key(it.firstOrNull().orEmpty()) } }
        known.forEach { seen += GeneratedCardValidator.key(it.front) }
        seen += GeneratedCardValidator.key(card.front)

        var replacement: GeneratedCard? = null
        var failure: AiFailure? = null
        val request = GenerationRequest(
            text = source,
            options = options,
            part = card.chunkIndex,
            title = title,
            avoid = known.map { it.front }.takeLast(MAX_AVOID),
            replacing = card,
        )
        generation.generate(route, request).collect { update ->
            when (update) {
                is GenerationUpdate.Card -> if (replacement == null) {
                    GeneratedCardValidator.validate(update.card)
                        ?.takeIf { GeneratedCardValidator.key(it.front) !in seen }
                        ?.let { replacement = it }
                }
                is GenerationUpdate.Done -> failure = update.failure
            }
        }
        return replacement?.let { RegenerateResult.Replaced(it) }
            ?: failure?.let { RegenerateResult.Failed(it) }
            ?: RegenerateResult.NothingNew
    }

    private companion object {
        const val MAX_AVOID = 60
    }
}
