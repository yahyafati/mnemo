package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yahyafati.mnemo.core.database.entity.SyncChangeEntity
import com.yahyafati.mnemo.core.database.entity.SyncFieldClockEntity
import com.yahyafati.mnemo.core.database.entity.SyncSeqEntity
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

    /**
     * Moves the logical clock to `max(wall clock, clock + 1) + count - 1`, reserving [count] values; the first one is
     * `getClock() - count + 1`. Read the result with [getClock] in the same transaction.
     */
    @Query("UPDATE sync_state SET clock = MAX(:wallClock, clock + 1) + :count - 1 WHERE id = 1")
    suspend fun advanceClockBy(wallClock: Long, count: Long)

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

    /** The unsent changes after [seq], oldest first. */
    @Query("SELECT * FROM sync_changes WHERE seq > :seq ORDER BY seq LIMIT :limit")
    suspend fun getChangesAfter(seq: Long, limit: Int): List<SyncChangeEntity>

    /** Every stamp of one row: its fields', the row's `*`, and values that arrived before the row. */
    @Query("SELECT * FROM sync_field_clocks WHERE tbl = :tbl AND rowId = :rowId")
    suspend fun getFieldClocks(tbl: String, rowId: String): List<SyncFieldClockEntity>

    @Upsert
    suspend fun putFieldClocks(clocks: List<SyncFieldClockEntity>)

    /** The stamps of [fields] of one row. */
    @Query("DELETE FROM sync_field_clocks WHERE tbl = :tbl AND rowId = :rowId AND field IN (:fields)")
    suspend fun deleteFieldClocks(tbl: String, rowId: String, fields: List<String>)

    /** Every stamp of a row but its `*`: they are all older once the whole row has been stamped. */
    @Query("DELETE FROM sync_field_clocks WHERE tbl = :tbl AND rowId = :rowId AND field != '*'")
    suspend fun deleteFieldSpecificClocks(tbl: String, rowId: String)

    @Query("SELECT * FROM sync_seqs")
    suspend fun getSeqs(): List<SyncSeqEntity>

    @Upsert
    suspend fun putSeqs(seqs: List<SyncSeqEntity>)

    /** Forgets everything about the sync location: for leaving it, or joining another (S4). The outbox stays. */
    @Query("DELETE FROM sync_field_clocks")
    suspend fun clearFieldClocks()

    @Query("DELETE FROM sync_seqs")
    suspend fun clearSeqs()
}
