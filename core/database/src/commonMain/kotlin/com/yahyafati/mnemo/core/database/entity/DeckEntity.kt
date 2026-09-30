package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Every table: UUID string ids, epoch-millis timestamps, and a `deletedAt` soft delete, so a
// future sync can merge rows (ARCHITECTURE §6).

@Entity(tableName = "decks", indices = [Index("parentId")])
data class DeckEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val description: String,
    val category: String?,
    val starred: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** Exam day as an epoch day (`LocalDate.toEpochDay`), for the deck's countdown (schema v4). */
    val examDate: Long? = null,
)
