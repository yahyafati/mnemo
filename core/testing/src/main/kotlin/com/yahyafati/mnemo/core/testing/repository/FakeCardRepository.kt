package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.NewNote
import com.yahyafati.mnemo.core.data.repository.QueueCandidates
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
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

    /** How many times [addNotes] ran: one per transaction. */
    var addNotesCalls = 0
        private set

    override suspend fun addNote(deckId: String, kind: NoteKind, fields: List<String>, tags: List<String>, hint: String?): Note =
        addNotes(deckId, listOf(NewNote(kind, fields, tags, hint)), NoteSource.Manual).single()

    override suspend fun addNotes(deckId: String, notes: List<NewNote>, source: NoteSource): List<Note> {
        // Check everything first: all or nothing, like the transaction.
        notes.forEach { require(it.kind.cardOrdinals(it.fields).isNotEmpty()) }
        addNotesCalls++
        val time = now()
        return notes.map { new ->
            val note = Note(
                "note-${nextId++}", deckId, NoteType.builtIn(new.kind).id, new.fields, new.tags, source,
                createdAt = time, updatedAt = time, hint = new.hint?.takeIf { it.isNotBlank() },
            )
            this.notes.update { it + (note.id to note) }
            new.kind.cardOrdinals(new.fields).forEach { ord ->
                putCard(Card("card-${nextId++}", note.id, deckId, ord, due = time, createdAt = time, updatedAt = time))
            }
            note
        }
    }

    override suspend fun getNoteFields(deckId: String): List<List<String>> =
        notes.value.values.filter { it.deckId == deckId }.map { it.fields }

    override suspend fun updateNote(noteId: String, deckId: String, fields: List<String>, tags: List<String>, hint: String?) {
        val note = notes.value.getValue(noteId)
        val kind = NoteType.byId(note.noteTypeId)!!.kind
        val ordinals = kind.cardOrdinals(fields)
        require(ordinals.isNotEmpty())
        notes.update { it + (noteId to note.copy(deckId = deckId, fields = fields, tags = tags, hint = hint?.takeIf { h -> h.isNotBlank() })) }
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

    override suspend fun getNotesInDecks(deckIds: List<String>, limit: Int): List<Note> =
        notes.value.values.filter { it.deckId in deckIds }.sortedBy { it.createdAt }.take(limit)

    override suspend fun getMostLapsed(deckIds: List<String>, minLapses: Int, limit: Int): List<StudyCard> =
        cards.value.values.filter { it.deckId in deckIds && it.lapses >= minLapses && !it.suspended }
            .sortedByDescending { it.lapses }.take(limit).mapNotNull(::toStudyCard)

    override suspend fun deleteNote(noteId: String) {
        notes.update { it - noteId }
        cards.update { map -> map.filterValues { it.noteId != noteId } }
    }

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
