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
}
