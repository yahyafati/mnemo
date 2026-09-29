package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notes", indices = [Index("deckId"), Index("noteTypeId"), Index("guid")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val noteTypeId: String,
    val fields: List<String>,
    val tags: List<String>,
    /** `NoteSource` name: Manual, Ai, Import. */
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Anki's note guid, for notes imported from or exported to Anki (schema v2). */
    val guid: String? = null,
)
