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

/**
 * The newest stamp of a field, for the per-field last-writer-wins merge (docs/sync/ROADMAP.md S3, ADR 0013).
 * [field] is a column name, `schedule` for a card's eight schedule columns (they merge as one unit), or `*`
 * for "every field of the row, stamped when it was created", which keeps a 12,000-card import to one row per
 * row instead of one per column. The stamp is ([clock], [device]); the device id breaks ties.
 *
 * A note's `fields` merge by position: `fields#0`, `fields#1` … are stamps of the single fields, and their [base] is a
 * hash of the text as of the last sync, which is how an edit made here is told from one that arrived.
 *
 * A row with a [value] is a change that arrived for a row this device doesn't have yet; it is applied when the
 * row's insert arrives. The scheduling settings are one pseudo-row (`settings`/`scheduling`/`record`) whose
 * [value] is the last record synced, which is how a local change is noticed. Never synced itself.
 */
@Entity(tableName = "sync_field_clocks", primaryKeys = ["tbl", "rowId", "field"])
data class SyncFieldClockEntity(
    val tbl: String,
    val rowId: String,
    val field: String,
    val clock: Long,
    val device: String,
    val value: String? = null,
    val base: String? = null,
)

/**
 * The newest change file of each device: applied from the others, written by this one (its own `deviceId`).
 * A folder that a sync tool fills can deliver a device's files out of order, so [gaps] lists the numbers below
 * [seq] that were missing when a newer file was applied (comma-separated, a window of recent ones), and are applied
 * if they turn up. Never synced.
 */
@Entity(tableName = "sync_seqs")
data class SyncSeqEntity(
    @PrimaryKey val deviceId: String,
    val seq: Long,
    val gaps: String = "",
)
