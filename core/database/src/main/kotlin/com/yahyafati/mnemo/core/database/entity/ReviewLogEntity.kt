package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "review_logs",
    indices = [Index("cardId", "reviewedAt"), Index("reviewedAt")],
)
data class ReviewLogEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    /** `Rating.value`: 1 again … 4 easy. */
    val rating: Int,
    /** `CardState.value` before the answer. */
    val stateBefore: Int,
    val reviewedAt: Long,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val durationMs: Long,
    val stabilityAfter: Double,
    val difficultyAfter: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
