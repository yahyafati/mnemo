package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.QueueCandidates
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.cardOrdinals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory [CardRepository] with the same queue rules as the Room one, minus SQL. */
class FakeCardRepository(
    private val now: () -> Instant = { Instant.EPOCH },
) : CardRepository {
    val notes = MutableStateFlow<Map<String, Note>>(emptyMap())
    val cards = MutableStateFlow<Map<String, Card>>(emptyMap())
    private var nextId = 1

    /** Deck names for [StudyCard.deckName]. */
    var deckNames: Map<String, String> = emptyMap()

    fun putCard(card: Card) = cards.update { it + (card.id to card) }

    override fun observeTotalCardCount(): Flow<Int> = cards.map { it.size }

    override suspend fun addNote(deckId: String, kind: NoteKind, fields: List<String>, tags: List<String>): Note {
        val ordinals = kind.cardOrdinals(fields)
        require(ordinals.isNotEmpty())
        val time = now()
        val note = Note("note-${nextId++}", deckId, NoteType.builtIn(kind).id, fields, tags, createdAt = time, updatedAt = time)
        notes.update { it + (note.id to note) }
        ordinals.forEach { ord ->
            putCard(Card("card-${nextId++}", note.id, deckId, ord, due = time, createdAt = time, updatedAt = time))
        }
        return note
    }

    override suspend fun updateNote(noteId: String, deckId: String, fields: List<String>, tags: List<String>) {
        val note = notes.value.getValue(noteId)
        val kind = NoteType.byId(note.noteTypeId)!!.kind
        val ordinals = kind.cardOrdinals(fields)
        require(ordinals.isNotEmpty())
        notes.update { it + (noteId to note.copy(deckId = deckId, fields = fields, tags = tags)) }
        val existing = cards.value.values.filter { it.noteId == noteId }
        cards.update { map ->
            val kept = map.filterValues { it.noteId != noteId || it.templateOrd in ordinals }
                .mapValues { (_, c) -> if (c.noteId == noteId) c.copy(deckId = deckId) else c }
            val added = ordinals.filter { ord -> existing.none { it.templateOrd == ord } }.map { ord ->
                Card("card-${nextId++}", noteId, deckId, ord, due = now(), createdAt = now(), updatedAt = now())
            }
            kept + added.associateBy { it.id }
        }
    }

    override suspend fun getNote(id: String): Note? = notes.value[id]

    override suspend fun getCard(id: String): Card? = cards.value[id]

    override suspend fun getStudyCards(cardIds: List<String>): List<StudyCard> =
        cardIds.mapNotNull { cards.value[it] }.mapNotNull(::toStudyCard)

    override suspend fun getQueueCandidates(
        deckIds: List<String>,
        now: Instant,
        dayEnd: Instant,
        reviewLimit: Int,
        newLimit: Int,
    ): QueueCandidates {
        val studyable = cards.value.values.filter { card ->
            card.deckId in deckIds && !card.suspended && (card.buriedUntil == null || !card.buriedUntil!!.isAfter(now))
        }
        return QueueCandidates(
            learning = studyable.filter { it.state == CardState.Learning || it.state == CardState.Relearning }
                .filter { it.due.isBefore(dayEnd) }.sortedBy { it.due }.mapNotNull(::toStudyCard),
            review = studyable.filter { it.state == CardState.Review && it.due.isBefore(dayEnd) }
                .sortedBy { it.due }.take(reviewLimit).mapNotNull(::toStudyCard),
            new = studyable.filter { it.state == CardState.New }
                .sortedWith(compareBy({ it.createdAt }, { it.id })).take(newLimit).mapNotNull(::toStudyCard),
        )
    }

    override suspend fun setStarred(cardId: String, starred: Boolean) = edit(cardId) { it.copy(starred = starred) }

    override suspend fun setFlagged(cardId: String, flagged: Boolean) = edit(cardId) { it.copy(flagged = flagged) }

    override suspend fun setSuspended(cardId: String, suspended: Boolean) = edit(cardId) { it.copy(suspended = suspended) }

    override suspend fun bury(cardId: String, until: Instant) = edit(cardId) { it.copy(buriedUntil = until) }

    private fun edit(cardId: String, block: (Card) -> Card) =
        cards.update { map -> map[cardId]?.let { map + (cardId to block(it)) } ?: map }

    private fun toStudyCard(card: Card): StudyCard? {
        val note = notes.value[card.noteId] ?: return null
        val kind = NoteType.byId(note.noteTypeId)?.kind ?: return null
        return StudyCard(card, note, kind, deckNames[card.deckId].orEmpty())
    }
}
