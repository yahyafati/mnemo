package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.entity.SyncFieldClockEntity
import com.yahyafati.mnemo.core.database.entity.SyncSeqEntity
import com.yahyafati.mnemo.core.database.sync.SyncRows
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.format.Change
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal class Packed(val changes: Int, val files: Int) {
    companion object {
        val NONE = Packed(0, 0)
    }
}

/**
 * Turns the outbox into change files (docs/sync/ROADMAP.md S3). The file is written before the outbox is cleared,
 * so a crash in between sends the changes again (as a new file with newer stamps, which changes nothing where it
 * arrives) instead of losing them.
 *
 * Every change of one run gets the same logical-clock value: it is taken when the changes are packed, not when
 * they were made (ADR 0013), and a device's runs are numbered in order, so its own values never tie.
 */
internal class SyncPacker(
    database: MnemoDatabase,
    private val transaction: TransactionRunner,
    private val syncClock: SyncClock,
    private val media: SyncMediaFiles,
    private val settings: SchedulingSettings,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val dao = database.syncDao()
    private val rows = SyncRows(database)

    /**
     * Sends what changed on this device since the last time. [announceSettings] sends the scheduling settings even if
     * they haven't changed: the device that creates a location does, so the others get them.
     */
    suspend fun pack(remote: SyncRemote, deviceId: String, announceSettings: Boolean): Packed {
        val outbox = OutboxSnapshot.read(dao)
        val settingsPut = settingsChange(announceSettings)
        if (outbox.isEmpty && settingsPut == null) return Packed.NONE

        val clock = syncClock.now()
        val changes = ArrayList<Change>()
        val stamps = ArrayList<SyncFieldClockEntity>()
        for ((key, changed) in outbox.rows) {
            val (tableName, rowId) = key
            val table = SyncSchema.table(tableName) ?: continue
            val row = rows.get(tableName, rowId) ?: continue
            val send = SyncSchema.fieldsToSend(table, changed.orNull())
            val values = LinkedHashMap<String, JsonElement>()
            for (field in send) values[field] = row[field] ?: JsonNull
            if (SyncSchema.isPositional(table, SyncSchema.NOTE_FIELDS) && SyncSchema.NOTE_FIELDS in send) {
                stampPositions(tableName, rowId, changed, values, clock, deviceId, stamps)
            }
            changes += Change.Put(tableName, rowId, clock, values)

            if (changed.all) {
                stamps += SyncFieldClockEntity(tableName, rowId, SyncSchema.ALL, clock, deviceId)
            } else {
                val groups = send.mapNotNull { SyncSchema.groupOf(table, it) }.toSet()
                groups.mapTo(stamps) { SyncFieldClockEntity(tableName, rowId, it, clock, deviceId) }
            }
        }
        if (settingsPut != null) {
            changes += Change.Put(SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW, clock, settingsPut.fields)
            stamps += SyncFieldClockEntity(
                SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW, SyncSchema.SETTINGS_FIELD, clock, deviceId, settingsPut.text,
            )
        }
        if (changes.isEmpty()) {
            // Nothing but rows that no longer exist.
            transaction { if (outbox.lastSeq > 0) dao.deleteChangesUpTo(outbox.lastSeq) }
            return Packed.NONE
        }

        val seqs = withContext(ioDispatcher) {
            uploadMedia(remote, changes)
            write(remote, deviceId, changes)
        }
        transaction {
            dao.putFieldClocks(stamps)
            if (outbox.lastSeq > 0) dao.deleteChangesUpTo(outbox.lastSeq)
            dao.putSeqs(listOf(SyncSeqEntity(deviceId, seqs.last())))
        }
        return Packed(changes.size, seqs.size)
    }

    /**
     * A note's fields merge by position, so say which ones this change made: those whose text differs from what the
     * last sync left (an insert sends them all). Stamps each of them, with the text's hash as the new baseline.
     */
    private suspend fun stampPositions(
        table: String,
        rowId: String,
        changed: ChangedFields,
        values: MutableMap<String, JsonElement>,
        clock: Long,
        deviceId: String,
        stamps: MutableList<SyncFieldClockEntity>,
    ) {
        val texts = NoteFields.parse(values[SyncSchema.NOTE_FIELDS])
        val positions: List<Int> = if (changed.all) {
            texts.indices.toList()
        } else {
            val bases = dao.getFieldClocks(table, rowId).filter { it.field.startsWith(SyncSchema.POSITION_PREFIX) }.associate { it.field to it.base }
            texts.indices.filter { bases[SyncSchema.position(it)] != NoteFields.hash(texts[it]) }
        }
        if (positions.isEmpty()) {
            values.remove(SyncSchema.NOTE_FIELDS)
            return
        }
        if (!changed.all) values[SyncSchema.FIELDS_CHANGED] = JsonArray(positions.map { JsonPrimitive(it) })
        positions.mapTo(stamps) { SyncFieldClockEntity(table, rowId, SyncSchema.position(it), clock, deviceId, base = NoteFields.hash(texts[it])) }
    }

    private class SettingsChange(val fields: Map<String, JsonElement>, val text: String)

    /** The scheduling settings if they differ from what was last synced (or [announce] says to send them anyway). */
    private suspend fun settingsChange(announce: Boolean): SettingsChange? {
        val record = settings.current()
        val text = SchedulingSettings.text(record)
        val stored = dao.getFieldClocks(SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW).firstOrNull { it.field == SyncSchema.SETTINGS_FIELD }
        if (stored == null && !announce) {
            // A device that joins must not overwrite the settings it is about to receive: note what it has now as the
            // baseline, and only a change from here on is sent.
            dao.putFieldClocks(
                listOf(SyncFieldClockEntity(SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW, SyncSchema.SETTINGS_FIELD, 0, "", text)),
            )
            return null
        }
        return if (stored == null || stored.value != text || announce) SettingsChange(record, text) else null
    }

    /** Media files go up before the change files that mention them, so a card never refers to a file that isn't there. */
    private fun uploadMedia(remote: SyncRemote, changes: List<Change>) {
        val wanted = changes.filterIsInstance<Change.Put>()
            .filter { it.table == SyncSchema.MEDIA && it.fields[SyncSchema.DELETED_AT].let { deleted -> deleted == null || deleted is JsonNull } }
            .map { it.id }
        if (wanted.isEmpty()) return
        val have = remote.listMedia().toSet()
        for (hash in wanted) {
            if (hash in have) continue
            // A file this device doesn't have can't be sent; the row still goes and the others show a placeholder.
            val bytes = media.read(hash) ?: continue
            remote.writeMedia(hash, bytes)
        }
    }

    private suspend fun write(remote: SyncRemote, deviceId: String, changes: List<Change>): List<Long> {
        val own = dao.getSeqs().firstOrNull { it.deviceId == deviceId }?.seq ?: 0
        var first = maxOf(own, remote.listChangeSeqs(deviceId).lastOrNull() ?: 0) + 1
        repeat(MAX_TRIES - 1) {
            try {
                return remote.writeChanges(deviceId, first, changes)
            } catch (_: SyncAlreadyExistsException) {
                // Another file with that number is whole: ours were lost track of (a restore). Take the next free one.
                first = maxOf(first, remote.listChangeSeqs(deviceId).lastOrNull() ?: 0) + 1
            }
        }
        return remote.writeChanges(deviceId, first, changes)
    }

    private companion object {
        const val MAX_TRIES = 5
    }
}
