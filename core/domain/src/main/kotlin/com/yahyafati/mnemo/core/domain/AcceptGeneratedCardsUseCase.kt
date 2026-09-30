package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.NewNote
import com.yahyafati.mnemo.core.model.GeneratedCard
import com.yahyafati.mnemo.core.model.NoteSource

/** What was saved: the queue ids of the accepted cards, and how many study cards they made. */
data class AcceptResult(val acceptedIds: List<String>, val cardCount: Int)

/**
 * Saves reviewed cards (ARCHITECTURE §5.2, step 7): one note each, `source = AI`, all in one
 * transaction. Cards the user edited into something invalid are left in the queue.
 */
class AcceptGeneratedCardsUseCase(
    private val cardRepository: CardRepository,
) {
    suspend operator fun invoke(deckId: String, cards: List<GeneratedCard>): AcceptResult {
        val valid = cards.mapNotNull { card -> GeneratedCardValidator.validate(card)?.let { card.id to it } }
        if (valid.isEmpty()) return AcceptResult(emptyList(), 0)
        cardRepository.addNotes(deckId, valid.map { (_, card) -> NewNote(card.kind, card.fields, card.tags) }, NoteSource.Ai)
        return AcceptResult(valid.map { it.first }, valid.sumOf { it.second.cardCount })
    }
}
