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
    /** Set by Undo: an undone answer never happened, but it is kept so the undo can sync. */
    val deletedAt: Long? = null,
    // The schedule this answer produced, so a replay (sync, S3) can start from any review. Null for
    // reviews imported from Anki and for rows made before schema v6.
    /** `CardState.value` after the answer. */
    val stateAfter: Int? = null,
    val stepAfter: Int? = null,
    val dueAfter: Long? = null,
    val repsAfter: Int? = null,
    val lapsesAfter: Int? = null,
)
