package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This device's sync bookkeeping: one row, `id = 1` (docs/sync/ROADMAP.md S1, ADR 0013). It is created
 * with the database and again on every open if it is missing, so a restored backup without one still
 * gets an identity. Never synced.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    /** A random UUID that names this device in the sync location. A restore keeps the restoring device's (`PendingRestore`). */
    val deviceId: String,
    /** The last hybrid logical clock value this device issued or observed (`SyncClock`). */
    val clock: Long,
    /** Whether changes are recorded in [SyncChangeEntity]. Off until the user turns sync on. */
    val enabled: Boolean,
    /** Set while remote changes are applied, so the triggers don't send them back. */
    val applying: Boolean,
) {
    companion object {
        const val SINGLE_ROW = 1
    }
}

/**
 * The outbox: one row per local insert or update of a synced table, written by SQLite triggers (see
 * `SyncTriggers`) so that no repository has to remember to. Packed into change files and cleared by
 * the merge engine (S3). Never synced itself.
 */
@Entity(tableName = "sync_changes")
data class SyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** The table the row is in. */
    val tbl: String,
    /** The row's primary key; a composite key (`ai_answers`) joins its parts with `/`. */
    val rowId: String,
    /** [ALL_FIELDS] for an insert, otherwise the changed column names, separated by commas. */
    val fields: String,
    /** The row's `updatedAt` when it was written. */
    val at: Long,
) {
    companion object {
        const val ALL_FIELDS = "*"
    }
}
