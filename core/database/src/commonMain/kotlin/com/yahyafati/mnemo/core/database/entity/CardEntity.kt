package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    indices = [Index("due", "state", "deckId"), Index("noteId"), Index("deckId")],
)
data class CardEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val deckId: String,
    val templateOrd: Int,
    /** `CardState.value`: 0 new, 1 learning, 2 review, 3 relearning. */
    val state: Int,
    val due: Long,
    val stability: Double?,
    val difficulty: Double?,
    val step: Int?,
    val lastReview: Long?,
    val reps: Int,
    val lapses: Int,
    val flagged: Boolean,
    val starred: Boolean,
    val suspended: Boolean,
    val buriedUntil: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
