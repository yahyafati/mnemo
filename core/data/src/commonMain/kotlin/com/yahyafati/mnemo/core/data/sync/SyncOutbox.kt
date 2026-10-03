package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.database.dao.SyncDao
import com.yahyafati.mnemo.core.database.entity.SyncChangeEntity

/** What the outbox says changed in one row: all of it (an insert) or some columns. */
internal class ChangedFields {
    var all = false
        private set
    val fields = LinkedHashSet<String>()

    fun add(entry: SyncChangeEntity) {
        if (entry.fields == SyncChangeEntity.ALL_FIELDS) all = true else fields += entry.fields.split(',')
    }

    /** The columns, or null for the whole row. */
    fun orNull(): Set<String>? = if (all) null else fields
}

/** The rows with unsent changes, oldest first, coalesced: ten edits of one row are one entry. */
internal class OutboxSnapshot(val rows: LinkedHashMap<Pair<String, String>, ChangedFields>, val lastSeq: Long) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        suspend fun read(dao: SyncDao, pageSize: Int = 2_000): OutboxSnapshot {
            val rows = LinkedHashMap<Pair<String, String>, ChangedFields>()
            var after = 0L
            while (true) {
                val page = dao.getChangesAfter(after, pageSize)
                if (page.isEmpty()) break
                for (entry in page) rows.getOrPut(entry.tbl to entry.rowId) { ChangedFields() }.add(entry)
                after = page.last().seq
            }
            return OutboxSnapshot(rows, after)
        }
    }
}
