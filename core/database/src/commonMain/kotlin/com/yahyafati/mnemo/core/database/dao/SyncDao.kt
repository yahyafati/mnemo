package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.yahyafati.mnemo.core.database.entity.SyncChangeEntity
import com.yahyafati.mnemo.core.database.entity.SyncStateEntity

/** This device's sync state and the outbox (docs/sync/ROADMAP.md S1). */
@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getState(): SyncStateEntity?

    @Query("UPDATE sync_state SET enabled = :enabled WHERE id = 1")
    suspend fun setEnabled(enabled: Boolean)

    /** While true, writes are not recorded: the merge engine sets it around applying remote changes. */
    @Query("UPDATE sync_state SET applying = :applying WHERE id = 1")
    suspend fun setApplying(applying: Boolean)

    @Query("SELECT clock FROM sync_state WHERE id = 1")
    suspend fun getClock(): Long?

    /** Moves the logical clock to `max(wall clock, clock + 1)`; read the result with [getClock] in the same transaction. */
    @Query("UPDATE sync_state SET clock = MAX(:wallClock, clock + 1) WHERE id = 1")
    suspend fun advanceClock(wallClock: Long)

    /** Moves the logical clock past a value seen on another device. */
    @Query("UPDATE sync_state SET clock = MAX(clock, :remote) WHERE id = 1")
    suspend fun raiseClock(remote: Long)

    /** The oldest unsent changes, in the order they were made. */
    @Query("SELECT * FROM sync_changes ORDER BY seq LIMIT :limit")
    suspend fun getChanges(limit: Int): List<SyncChangeEntity>

    @Query("SELECT COUNT(*) FROM sync_changes")
    suspend fun countChanges(): Int

    /** Clears the changes that were sent. */
    @Query("DELETE FROM sync_changes WHERE seq <= :seq")
    suspend fun deleteChangesUpTo(seq: Long)
}
