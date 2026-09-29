package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.yahyafati.mnemo.core.database.entity.NoteEntity
import com.yahyafati.mnemo.core.database.entity.NoteTypeEntity

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun getNote(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun getNotes(ids: List<String>): List<NoteEntity>

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE deckId IN (:deckIds) AND deletedAt IS NULL")
    suspend fun softDeleteInDecks(deckIds: List<String>, now: Long)

    @Query("SELECT * FROM note_types WHERE deletedAt IS NULL")
    suspend fun getNoteTypes(): List<NoteTypeEntity>

    @Insert
    suspend fun insertNoteTypes(noteTypes: List<NoteTypeEntity>)

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("UPDATE notes SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)

    @Query("UPDATE notes SET tags = :tags, updatedAt = :now WHERE id = :id")
    suspend fun setTags(id: String, tags: List<String>, now: Long)

    @Query("UPDATE notes SET deckId = :deckId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDeck(ids: List<String>, deckId: String, now: Long)

    /** Live notes with one of [guids]: duplicates when importing. */
    @Query("SELECT guid FROM notes WHERE guid IN (:guids) AND deletedAt IS NULL")
    suspend fun getLiveGuids(guids: List<String>): List<String>

    /** Which of [ids] exist at all, deleted or not (an id can't be reused). */
    @Query("SELECT id, deletedAt IS NOT NULL AS deleted FROM notes WHERE id IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<ExistingId>

    /** Live notes in rowid order, [limit] after [afterRowId]: for exports and scans. */
    @Query("SELECT rowid AS rowId, * FROM notes WHERE deletedAt IS NULL AND rowid > :afterRowId ORDER BY rowid LIMIT :limit")
    suspend fun getPage(afterRowId: Long, limit: Int): List<NoteRow>

    /** Like [getPage], only notes with a live card in [deckIds]. */
    @Query(
        """
        SELECT rowid AS rowId, * FROM notes n WHERE n.deletedAt IS NULL AND n.rowid > :afterRowId
            AND EXISTS (SELECT 1 FROM cards c WHERE c.noteId = n.id AND c.deletedAt IS NULL AND c.deckId IN (:deckIds))
        ORDER BY n.rowid LIMIT :limit
        """,
    )
    suspend fun getPageInDecks(deckIds: List<String>, afterRowId: Long, limit: Int): List<NoteRow>

    /** The fields of every live note in [deckId], as their JSON text: duplicates for Smart Extract. */
    @Query("SELECT fields AS json FROM notes WHERE deckId = :deckId AND deletedAt IS NULL")
    suspend fun getFieldsJsonInDeck(deckId: String): List<JsonColumn>

    /** Every live note's tags, as their JSON text, for the tag list. */
    @Query("SELECT tags AS json FROM notes WHERE deletedAt IS NULL AND tags != '[]'")
    suspend fun getAllTagsJson(): List<JsonColumn>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT MIN(createdAt) FROM notes WHERE deletedAt IS NULL")
    suspend fun getEarliestCreatedAt(): Long?
}

data class ExistingId(val id: String, val deleted: Boolean)

/** A note with its SQLite rowid, for keyset paging. */
data class NoteRow(
    val rowId: Long,
    @androidx.room.Embedded val note: NoteEntity,
)

data class JsonColumn(val json: String)
