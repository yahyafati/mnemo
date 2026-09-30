package com.yahyafati.mnemo.core.testing.repository

import androidx.paging.PagingData
import com.yahyafati.mnemo.core.data.repository.CardBrowserRepository
import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.CardStatus
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CardBrowserRepository] over [cards]: text matches fields and tags, the deck filter is
 * exact (no subdecks), and bulk edits change the cards in place.
 */
class FakeCardBrowserRepository(initial: List<StudyCard> = emptyList()) : CardBrowserRepository {
    val cards = MutableStateFlow(initial)

    /** The last query [browse] was asked for. */
    var lastQuery: CardQuery? = null
        private set

    fun matching(query: CardQuery): List<StudyCard> = cards.value.filter { c ->
        val text = query.text.trim()
        (text.isEmpty() || c.note.fields.any { it.contains(text, ignoreCase = true) } || c.note.tags.any { it.contains(text, ignoreCase = true) }) &&
            (query.deckId == null || c.card.deckId == query.deckId) &&
            (query.tag == null || c.note.tags.any { it.equals(query.tag, ignoreCase = true) }) &&
            when (query.status) {
                null -> true
                CardStatus.New -> c.card.state == CardState.New
                CardStatus.Learning -> c.card.state == CardState.Learning || c.card.state == CardState.Relearning
                CardStatus.Review, CardStatus.Due -> c.card.state == CardState.Review
                CardStatus.Suspended -> c.card.suspended
                CardStatus.Flagged -> c.card.flagged
                CardStatus.Starred -> c.card.starred
            }
    }

    override fun browse(query: CardQuery): Flow<PagingData<StudyCard>> {
        lastQuery = query
        return cards.map { PagingData.from(matching(query)) }
    }

    override fun count(query: CardQuery): Flow<Int> = cards.map { matching(query).size }

    override suspend fun cardIds(query: CardQuery): List<String> = matching(query).map { it.card.id }

    override suspend fun tags(): List<String> = cards.value.flatMap { it.note.tags }.distinct().sorted()

    override suspend fun setSuspended(cardIds: Collection<String>, suspended: Boolean) =
        edit(cardIds) { it.copy(card = it.card.copy(suspended = suspended)) }

    override suspend fun setFlagged(cardIds: Collection<String>, flagged: Boolean) =
        edit(cardIds) { it.copy(card = it.card.copy(flagged = flagged)) }

    override suspend fun moveToDeck(cardIds: Collection<String>, deckId: String) =
        edit(cardIds) { it.copy(card = it.card.copy(deckId = deckId)) }

    override suspend fun addTag(cardIds: Collection<String>, tag: String) =
        edit(cardIds) { if (tag in it.note.tags) it else it.copy(note = it.note.copy(tags = it.note.tags + tag)) }

    override suspend fun removeTag(cardIds: Collection<String>, tag: String) =
        edit(cardIds) { it.copy(note = it.note.copy(tags = it.note.tags - tag)) }

    override suspend fun deleteNotes(cardIds: Collection<String>) {
        val notes = cards.value.filter { it.card.id in cardIds }.map { it.note.id }.toSet()
        cards.update { list -> list.filterNot { it.note.id in notes } }
    }

    private fun edit(ids: Collection<String>, block: (StudyCard) -> StudyCard) =
        cards.update { list -> list.map { if (it.card.id in ids) block(it) else it } }
}
