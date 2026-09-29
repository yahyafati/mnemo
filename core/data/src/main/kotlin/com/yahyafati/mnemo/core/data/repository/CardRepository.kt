package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.StudyCard
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A note to create with [CardRepository.addNotes]. */
data class NewNote(
    val kind: NoteKind,
    val fields: List<String>,
    val tags: List<String> = emptyList(),
)

/** Candidate cards for a study queue, split by kind so the domain can apply limits and order. */
data class QueueCandidates(
    val learning: List<StudyCard>,
    val review: List<StudyCard>,
    val new: List<StudyCard>,
)

interface CardRepository {
    fun observeTotalCardCount(): Flow<Int>

    /**
     * Adds a note and its cards in one transaction. The cards are derived from [kind] and
     * [fields] (one per cloze number for cloze notes).
     */
    suspend fun addNote(deckId: String, kind: NoteKind, fields: List<String>, tags: List<String>): Note

    /** Adds several notes and all their cards in one transaction: all of them or none. */
    suspend fun addNotes(deckId: String, notes: List<NewNote>, source: NoteSource): List<Note>

    /** The fields of every live note in [deckId] (not its subdecks), for spotting duplicates. */
    suspend fun getNoteFields(deckId: String): List<List<String>>

    /**
     * Updates a note's content. Cards follow: moved with the note, created for new cloze numbers,
     * deleted for removed ones. Existing cards keep their schedule.
     */
    suspend fun updateNote(noteId: String, deckId: String, fields: List<String>, tags: List<String>)

    suspend fun getNote(id: String): Note?

    suspend fun getStudyCards(cardIds: List<String>): List<StudyCard>

    /**
     * Cards that can be studied from [deckIds] at [now]: learning cards due before [dayEnd],
     * at most [reviewLimit] review cards due before [dayEnd], and at most [newLimit] new cards.
     */
    suspend fun getQueueCandidates(
        deckIds: List<String>,
        now: Instant,
        dayEnd: Instant,
        reviewLimit: Int,
        newLimit: Int,
    ): QueueCandidates

    suspend fun setStarred(cardId: String, starred: Boolean)

    suspend fun setFlagged(cardId: String, flagged: Boolean)

    suspend fun setSuspended(cardId: String, suspended: Boolean)

    /** Hides the card until [until] (the next study day). */
    suspend fun bury(cardId: String, until: Instant)

    suspend fun getCard(id: String): Card?
}
