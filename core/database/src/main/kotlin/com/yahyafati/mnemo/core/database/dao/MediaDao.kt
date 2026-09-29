package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yahyafati.mnemo.core.database.entity.MediaEntity

@Dao
interface MediaDao {
    /** Adds media, or brings back media that was garbage-collected (same content, same id). */
    @Upsert
    suspend fun upsert(media: List<MediaEntity>)

    @Query("SELECT * FROM media WHERE deletedAt IS NULL")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun get(ids: List<String>): List<MediaEntity>

    /** Live media created before [before], the candidates for garbage collection. */
    @Query("SELECT id FROM media WHERE deletedAt IS NULL AND createdAt < :before")
    suspend fun getIdsCreatedBefore(before: Long): List<String>

    @Query("UPDATE media SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<String>, now: Long)
}
