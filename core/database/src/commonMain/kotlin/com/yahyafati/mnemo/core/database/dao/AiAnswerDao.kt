package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yahyafati.mnemo.core.database.entity.AiAnswerEntity

/** Study-time AI answers kept per note, so they are not asked for again. */
@Dao
interface AiAnswerDao {
    @Query("SELECT * FROM ai_answers WHERE noteId = :noteId AND deletedAt IS NULL")
    suspend fun getForNote(noteId: String): List<AiAnswerEntity>

    @Upsert
    suspend fun upsert(answer: AiAnswerEntity)
}
