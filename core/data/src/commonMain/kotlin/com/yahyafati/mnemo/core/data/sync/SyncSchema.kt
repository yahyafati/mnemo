package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.database.sync.SYNCED_TABLES
import com.yahyafati.mnemo.core.database.sync.SyncedTable
import com.yahyafati.mnemo.core.database.entity.SyncFieldClockEntity

/**
 * A change's version of "newer": the logical clock, then the device id, so two devices that stamped different
 * values with the same clock still agree on which one wins.
 */
internal data class SyncStamp(val clock: Long, val device: String) : Comparable<SyncStamp> {
    override fun compareTo(other: SyncStamp): Int = compareValuesBy(this, other, SyncStamp::clock, SyncStamp::device)

    companion object {
        /** Older than every real stamp. */
        val NONE = SyncStamp(0, "")
    }
}

internal fun SyncFieldClockEntity.stamp() = SyncStamp(clock, device)

/**
 * How the engine merges each synced table (ADR 0013). A field merges on its own by last writer, except a card's
 * schedule, whose eight columns only make sense together and so travel and win as the one unit [SCHEDULE], and a
 * note's `fields`, which merge by position. `updatedAt` is stamped like a field (a device that edits a row after
 * seeing a newer `updatedAt` writes an older one, so "the latest" would not converge). Two columns follow their own
 * rule and have no stamp: `createdAt` (the earliest) and `deletedAt` (once any device deleted a row it stays
 * deleted, with the earliest time).
 */
internal object SyncSchema {
    /** The stamp of a whole row, set when it is created: every field of it. */
    const val ALL = "*"

    /** The unit a card's schedule merges as. */
    const val SCHEDULE = "schedule"

    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
    const val DELETED_AT = "deletedAt"

    val CARD_SCHEDULE = listOf("state", "due", "stability", "difficulty", "step", "lastReview", "reps", "lapses")

    const val DECKS = "decks"
    const val NOTES = "notes"
    const val CARDS = "cards"
    const val REVIEW_LOGS = "review_logs"
    const val MEDIA = "media"

    /** The pseudo-table the scheduling settings travel in. */
    const val SETTINGS = "settings"
    const val SETTINGS_ROW = "scheduling"
    const val SETTINGS_FIELD = "record"

    /** A note's field texts merge by position: stamps of `fields#0`, `fields#1` … */
    const val NOTE_FIELDS = "fields"

    /** The put key that says which positions of a note's `fields` this change made. */
    const val FIELDS_CHANGED = "fields.changed"

    const val POSITION_PREFIX = "fields#"

    fun isPositional(table: SyncedTable, field: String) = table.name == NOTES && field == NOTE_FIELDS

    fun position(index: Int) = "$POSITION_PREFIX$index"

    fun table(name: String): SyncedTable? = SYNCED_TABLES.firstOrNull { it.name == name }

    /** The unit [field] of [table] merges as, or null for the key, `createdAt`, `deletedAt`, a note's fields and unknown names. */
    fun groupOf(table: SyncedTable, field: String): String? = when {
        field == UPDATED_AT -> UPDATED_AT
        field == DELETED_AT || field !in table.columns || isPositional(table, field) -> null
        table.name == CARDS && field in CARD_SCHEDULE -> SCHEDULE
        else -> field
    }

    /** The columns of [group] in [table]. */
    fun fieldsOf(table: SyncedTable, group: String): List<String> =
        if (table.name == CARDS && group == SCHEDULE) CARD_SCHEDULE else listOf(group)

    /** Every unit of [table] that merges by stamp. */
    fun groupsOf(table: SyncedTable): Set<String> = (table.columns + UPDATED_AT).mapNotNull { groupOf(table, it) }.toSet()

    /** A change carries every column: the row's insert, as opposed to an edit of some fields. */
    fun isFull(table: SyncedTable, fields: Set<String>): Boolean =
        fields.containsAll(table.columns) && CREATED_AT in fields && UPDATED_AT in fields

    /**
     * The columns to send for a row whose outbox entries named [changed] (null: it was inserted, send everything).
     * `updatedAt` always goes; a unit goes whole.
     */
    fun fieldsToSend(table: SyncedTable, changed: Set<String>?): Set<String> {
        if (changed == null) return (table.columns + CREATED_AT + UPDATED_AT).toSet()
        val send = LinkedHashSet<String>()
        for (field in changed) {
            val group = groupOf(table, field)
            when {
                field !in table.columns -> Unit
                group == null -> send += field
                else -> send += fieldsOf(table, group)
            }
        }
        send += UPDATED_AT
        return send
    }

    /** The primary key columns' values, from a row id. */
    fun keyParts(table: SyncedTable, rowId: String): List<String> = rowId.split('/', limit = table.keyColumns.size)
}
