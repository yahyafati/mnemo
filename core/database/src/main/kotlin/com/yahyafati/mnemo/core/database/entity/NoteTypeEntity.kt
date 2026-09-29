package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_types")
data class NoteTypeEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** `NoteKind` name: Basic, Reversed, Cloze, TypeIn, MultipleChoice. */
    val kind: String,
    /** Field names, in order. */
    val fields: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
