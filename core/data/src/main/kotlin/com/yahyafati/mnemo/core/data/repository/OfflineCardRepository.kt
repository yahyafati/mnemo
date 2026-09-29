package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.CardDao
import com.yahyafati.mnemo.core.database.dao.DeckDao
import com.yahyafati.mnemo.core.database.dao.NoteDao
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.model.cardOrdinals
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

internal class OfflineCardRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val cardDao: CardDao,
    private val deckDao: DeckDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
) : CardRepository {
    override fun observeTotalCardCount(): Flow<Int> = cardDao.observeTotalCount()

    override suspend fun addNote(deckId: String, kind: NoteKind, fields: List<String>, tags: List<String>, hint: String?): Note =
        addNotes(deckId, listOf(NewNote(kind, fields, tags, hint)), NoteSource.Manual).single()

    override suspend fun addNotes(deckId: String, notes: List<NewNote>, source: NoteSource): List<Note> {
        val now = clock.now()
        val created = notes.map { new ->
            val ordinals = new.kind.cardOrdinals(new.fields)
            require(ordinals.isNotEmpty()) { "A ${new.kind} note with these fields makes no cards" }
            val note = Note(
                id = UUID.randomUUID().toString(),
                deckId = deckId,
                noteTypeId = NoteType.builtIn(new.kind).id,
                fields = new.fields,
                tags = new.tags,
                source = source,
                hint = new.hint?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            )
            note to ordinals.map { newCard(note, it, now) }
        }
        transaction {
            noteDao.insertAll(created.map { it.first.toEntity() })
            cardDao.insert(created.flatMap { it.second })
        }
        return created.map { it.first }
    }

    override suspend fun getNoteFields(deckId: String): List<List<String>> =
        noteDao.getFieldsJsonInDeck(deckId).map { Json.decodeFromString<List<String>>(it.json) }

    override suspend fun updateNote(noteId: String, deckId: String, fields: List<String>, tags: List<String>, hint: String?) {
        transaction {
            val note = checkNotNull(noteDao.getNote(noteId)) { "No note $noteId" }.toModel()
            val kind = checkNotNull(NoteType.byId(note.noteTypeId)) { "Unknown note type" }.kind
            val ordinals = kind.cardOrdinals(fields)
            require(ordinals.isNotEmpty()) { "A $kind note with these fields makes no cards" }
            val now = clock.now()
            val updated = note.copy(
                deckId = deckId, fields = fields, tags = tags, hint = hint?.trim()?.takeIf { it.isNotEmpty() }, updatedAt = now,
            )
            noteDao.update(updated.toEntity())

            val cards = cardDao.getCardsForNote(noteId)
            val existing = cards.map { it.templateOrd }.toSet()
            cardDao.insert(ordinals.filterNot { it in existing }.map { newCard(updated, it, now) })
            cardDao.softDelete(cards.filter { it.templateOrd !in ordinals }.map { it.id }, now.toEpochMilli())
            if (deckId != note.deckId) cardDao.moveNoteCards(noteId, deckId, now.toEpochMilli())
        }
    }

    override suspend fun getNote(id: String): Note? = noteDao.getNote(id)?.toModel()

    override suspend fun getNotesInDecks(deckIds: List<String>, limit: Int): List<Note> =
        deckIds.chunked(SQL_VARIABLE_CHUNK).flatMap { noteDao.getNotesInDecks(it, limit) }
            .sortedBy { it.createdAt }.take(limit).map { it.toModel() }

    override suspend fun getMostLapsed(deckIds: List<String>, minLapses: Int, limit: Int): List<StudyCard> =
        toStudyCards(cardDao.getMostLapsed(deckIds, minLapses, limit))

    override suspend fun deleteNote(noteId: String) = transaction {
        val now = clock.now().toEpochMilli()
        noteDao.softDelete(listOf(noteId), now)
        cardDao.softDeleteForNotes(listOf(noteId), now)
    }

    override suspend fun getCard(id: String): Card? = cardDao.getCard(id)?.toModel()

    override suspend fun getStudyCards(cardIds: List<String>): List<StudyCard> {
        val cards = cardIds.mapNotNull { cardDao.getCard(it) }
        return toStudyCards(cards)
    }

    override suspend fun getQueueCandidates(
        deckIds: List<String>,
        now: Instant,
        dayEnd: Instant,
        reviewLimit: Int,
        newLimit: Int,
    ): QueueCandidates {
        val nowMs = now.toEpochMilli()
        val dayEndMs = dayEnd.toEpochMilli()
        return QueueCandidates(
            learning = toStudyCards(cardDao.getLearningCards(deckIds, nowMs, dayEndMs)),
            review = if (reviewLimit > 0) {
                toStudyCards(cardDao.getReviewCards(deckIds, nowMs, dayEndMs, reviewLimit))
            } else {
                emptyList()
            },
            new = if (newLimit > 0) toStudyCards(cardDao.getNewCards(deckIds, nowMs, newLimit)) else emptyList(),
        )
    }

    override suspend fun setStarred(cardId: String, starred: Boolean) =
        cardDao.setStarred(cardId, starred, clock.now().toEpochMilli())

    override suspend fun setFlagged(cardId: String, flagged: Boolean) =
        cardDao.setFlagged(cardId, flagged, clock.now().toEpochMilli())

    override suspend fun setSuspended(cardId: String, suspended: Boolean) =
        cardDao.setSuspended(cardId, suspended, clock.now().toEpochMilli())

    override suspend fun bury(cardId: String, until: Instant) =
        cardDao.setBuriedUntil(cardId, until.toEpochMilli(), clock.now().toEpochMilli())

    private suspend fun toStudyCards(cards: List<CardEntity>): List<StudyCard> {
        if (cards.isEmpty()) return emptyList()
        val notes = cards.map { it.noteId }.distinct().chunked(SQL_VARIABLE_CHUNK)
            .flatMap { noteDao.getNotes(it) }
            .associate { it.id to it.toModel() }
        val deckNames = deckDao.getDecks().associate { it.id to it.name }
        return cards.mapNotNull { entity ->
            val note = notes[entity.noteId] ?: return@mapNotNull null
            val kind = NoteType.byId(note.noteTypeId)?.kind ?: return@mapNotNull null
            StudyCard(entity.toModel(), note, kind, deckNames[entity.deckId].orEmpty())
        }
    }

    private fun newCard(note: Note, templateOrd: Int, now: Instant) = Card(
        id = UUID.randomUUID().toString(),
        noteId = note.id,
        deckId = note.deckId,
        templateOrd = templateOrd,
        due = now,
        createdAt = now,
        updatedAt = now,
    ).toEntity()

    private companion object {
        const val SQL_VARIABLE_CHUNK = 500
    }
}
