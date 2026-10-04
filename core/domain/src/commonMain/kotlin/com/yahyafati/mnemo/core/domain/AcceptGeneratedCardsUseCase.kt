package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.NewNote
import com.yahyafati.mnemo.core.model.CardFigure
import com.yahyafati.mnemo.core.model.FigureSide
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteSource
import java.io.IOException

/**
 * What was saved: the queue ids of the accepted cards, and how many study cards they made. [figuresMissing] counts figures
 * that couldn't be stored (the file was gone, or the card has no place for a picture): those cards were saved without them.
 */
data class AcceptResult(val acceptedIds: List<String>, val cardCount: Int, val figuresMissing: Int = 0)

/**
 * Saves reviewed cards (ARCHITECTURE §5.2, step 7): one note each, `source = AI`, all in one
 * transaction. Cards the user edited into something invalid are left in the queue.
 *
 * A card from PDF pages can come with a [CardFigure] (docs/pdf/ROADMAP.md, P7), a crop of its page: it is stored as media
 * and `![](media:<sha256>)` is added to the figure's side of the card. Only cards that are saved store anything; the
 * media is stored before the notes, so a save that fails leaves a file no note refers to, which the media clean-up
 * collects after a day (ADR 0004).
 */
class AcceptGeneratedCardsUseCase(
    private val cardRepository: CardRepository,
    private val mediaRepository: MediaRepository,
) {
    /** [figures] are by queue id. */
    suspend operator fun invoke(deckId: String, cards: List<GeneratedCard>, figures: Map<String, CardFigure> = emptyMap()): AcceptResult {
        val valid = cards.mapNotNull { card -> GeneratedCardValidator.validate(card)?.let { card.id to it } }
        if (valid.isEmpty()) return AcceptResult(emptyList(), 0)
        var missing = 0
        val notes = valid.map { (id, card) ->
            val figure = figures[id]
            val fields = if (figure == null) card.fields else withFigure(card, figure) ?: card.fields.also { missing++ }
            NewNote(card.kind, fields, card.tags)
        }
        cardRepository.addNotes(deckId, notes, NoteSource.Ai)
        return AcceptResult(valid.map { it.first }, valid.sumOf { it.second.cardCount }, missing)
    }

    /** The fields of [card] with [figure] on its side, or null when it can't be added. */
    private suspend fun withFigure(card: GeneratedCard, figure: CardFigure): List<String>? {
        if (figure.side !in FigureSide.allowedFor(card.kind)) return null
        val media = try {
            figure.file.inputStream().use { mediaRepository.store(it, figure.file.name) }
        } catch (e: IOException) {
            return null
        }
        val reference = "![](${MediaRef.of(media.id)})"
        val index = if (figure.side == FigureSide.Front) 0 else 1
        return card.fields.mapIndexed { i, field ->
            when {
                i != index -> field
                field.isBlank() -> reference
                else -> "$field\n\n$reference"
            }
        }
    }
}
