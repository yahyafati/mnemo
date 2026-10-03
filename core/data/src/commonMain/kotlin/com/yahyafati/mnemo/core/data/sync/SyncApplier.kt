package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.entity.SyncFieldClockEntity
import com.yahyafati.mnemo.core.database.entity.SyncSeqEntity
import com.yahyafati.mnemo.core.database.sync.SyncRows
import com.yahyafati.mnemo.core.database.sync.SyncedTable
import com.yahyafati.mnemo.core.sync.format.Change
import com.yahyafati.mnemo.core.sync.format.ChangeBatch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A change file read from another device. */
internal class Received(val deviceId: String, val batch: ChangeBatch)

/**
 * What the run read: the files, in the order to apply them, and for each device the numbers its files had in the
 * location when they were listed (to find the ones that haven't arrived yet).
 */
internal class ReceivedFiles(val files: List<Received>, val present: Map<String, Set<Long>>) {
    companion object {
        val NONE = ReceivedFiles(emptyList(), emptyMap())
    }
}

internal class Applied(val changes: Int, val files: Int, val replayedCards: Int) {
    companion object {
        val NONE = Applied(0, 0, 0)
    }
}

/**
 * Applies other devices' change files (docs/sync/ROADMAP.md S3, ADR 0013), all in one transaction with
 * `sync_state.applying` set so that nothing is echoed back. Applying a file twice changes nothing, and the result
 * does not depend on the order the files arrive in: every rule compares stamps (or takes a minimum or maximum), never
 * "what I have now" against "what just came".
 *
 * Per table (see [SyncSchema]):
 *
 * - A field is replaced when the change's stamp is newer than the stamp of what is there (the field's own, or the
 *   row's insert). A card's schedule is one such field.
 * - `createdAt` keeps the earliest value, `updatedAt` the latest, and a row that any device deleted stays deleted
 *   (with the earliest time): a delete wins over an edit.
 * - A row that doesn't exist yet takes the whole row when the insert comes. An edit that arrives before it (the files
 *   of two devices may reach a folder in any order) is kept aside, in `sync_field_clocks`, and applied with the insert.
 * - A field with a local change that hasn't been sent yet isn't touched: that change will be stamped after everything
 *   seen here, so it wins anyway, and the user's edit isn't undone in the meantime.
 *
 * When all changes are in, the cards that got new reviews are reconciled ([CardReconciler]) and [SyncFixups] run, and
 * their writes are recorded for the next run to send.
 */
internal class SyncApplier(
    database: MnemoDatabase,
    private val transaction: TransactionRunner,
    private val syncClock: SyncClock,
    private val clock: Clock,
    private val settings: SchedulingSettings,
    private val replayer: ScheduleReplayer,
) {
    private val dao = database.syncDao()
    private val rows = SyncRows(database)
    private val reconciler = CardReconciler(database.cardDao(), database.reviewLogDao())
    private val fixups = SyncFixups(database.deckDao(), database.syncMergeDao())

    /** Applies [files] (already in the order to apply them). */
    suspend fun apply(files: ReceivedFiles): Applied {
        val received = files.files
        if (received.isEmpty()) return Applied.NONE
        // Settings first, so the replays below use the settings the other devices have.
        applySettings(received)

        var replayed = 0
        val changes = received.sumOf { it.batch.changes.size }
        transaction {
            val context = Context(OutboxSnapshot.read(dao).rows)
            dao.setApplying(true)
            for (file in received) {
                for (change in file.batch.changes) {
                    context.maxClock = maxOf(context.maxClock, change.clock)
                    if (change is Change.Put) applyPut(file.deviceId, change, context)
                }
            }
            syncClock.observe(context.maxClock)
            val before = dao.getSeqs().associateBy { it.deviceId }
            dao.putSeqs(
                received.groupBy { it.deviceId }.map { (device, applied) ->
                    advance(device, before[device], applied.map { it.batch.seq }.toSet(), files.present[device].orEmpty())
                },
            )
            dao.setApplying(false)

            // What follows is this device's own merge result: recorded, so the others get it.
            val answerer = replayer.answerer(settings.userSettings())
            for (card in context.cards) if (reconciler.reconcile(card, answerer)) replayed++
            fixups.run(clock.now().toEpochMilli())
        }
        return Applied(changes, received.size, replayed)
    }

    /**
     * The device's position after [applied] files: the newest number, and the numbers below it that were not in the
     * location (and not applied), so a file that a sync tool delivers late is still read. Only a window of recent numbers
     * is kept; a gap further back is a file that was taken away (compaction), not one that is late.
     */
    private fun advance(device: String, before: SyncSeqEntity?, applied: Set<Long>, present: Set<Long>): SyncSeqEntity {
        val oldSeq = before?.seq ?: 0
        val newest = maxOf(oldSeq, applied.max())
        val floor = newest - GAP_WINDOW
        val gaps = HashSet<Long>()
        before?.gaps?.split(',')?.mapNotNullTo(gaps) { it.toLongOrNull() }
        for (seq in maxOf(oldSeq, floor) + 1 until newest) if (seq !in present) gaps += seq
        gaps.removeAll(applied)
        return SyncSeqEntity(device, newest, gaps.filter { it > floor }.sorted().joinToString(","))
    }

    private class Context(val pending: Map<Pair<String, String>, ChangedFields>) {
        var maxClock = 0L

        /** Cards whose reviews or schedule changed. */
        val cards = LinkedHashSet<String>()
    }

    private companion object {
        const val GAP_WINDOW = 200L
    }

    // --- scheduling settings -------------------------------------------------------------------------------

    private suspend fun applySettings(received: List<Received>) {
        val newest = received
            .flatMap { file ->
                file.batch.changes.filterIsInstance<Change.Put>()
                    .filter { it.table == SyncSchema.SETTINGS }
                    .map { SyncStamp(it.clock, file.deviceId) to it }
            }
            .maxByOrNull { it.first } ?: return
        val stored = dao.getFieldClocks(SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW).firstOrNull { it.field == SyncSchema.SETTINGS_FIELD }
        if (newest.first <= (stored?.stamp() ?: SyncStamp.NONE)) return
        // A setting changed here since the last run isn't sent yet and will be stamped after this one: it wins.
        if (stored?.value != null && stored.value != SchedulingSettings.text(settings.current())) return

        settings.write(JsonObject(newest.second.fields))
        dao.putFieldClocks(
            listOf(
                SyncFieldClockEntity(
                    SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW, SyncSchema.SETTINGS_FIELD, newest.first.clock, newest.first.device,
                    SchedulingSettings.text(settings.current()),
                ),
            ),
        )
    }

    // --- one change ---------------------------------------------------------------------------------------

    private suspend fun applyPut(device: String, put: Change.Put, context: Context) {
        val table = SyncSchema.table(put.table) ?: return // A table a newer version added, or the settings.
        val stamp = SyncStamp(put.clock, device)
        val clocks = dao.getFieldClocks(table.name, put.id)
        val existing = rows.get(table.name, put.id)
        if (existing == null) applyNew(table, put, stamp, clocks, context) else applyExisting(table, put, stamp, clocks, existing, context)
    }

    /** The row isn't here: take it whole, or keep what arrived for it until its insert does. */
    private suspend fun applyNew(table: SyncedTable, put: Change.Put, stamp: SyncStamp, clocks: List<SyncFieldClockEntity>, context: Context) {
        val known = put.fields.filterKeys { it in table.columns || it == SyncSchema.CREATED_AT || it == SyncSchema.UPDATED_AT }
        if (!SyncSchema.isFull(table, known.keys)) {
            stash(table, put, stamp, clocks)
            return
        }

        val values = LinkedHashMap(known)
        val earlier = clocks.filter { it.value != null }
        val wonOverInsert = ArrayList<SyncFieldClockEntity>()
        val isNote = table.name == SyncSchema.NOTES
        val texts = if (isNote) NoteFields.parse(values[SyncSchema.NOTE_FIELDS]).toMutableList() else null
        val positionStamps = HashMap<Int, SyncStamp>()
        for (pending in earlier) {
            val kept = Json.parseToJsonElement(pending.value!!)
            when {
                texts != null && pending.field.startsWith(SyncSchema.POSITION_PREFIX) -> {
                    val index = pending.field.removePrefix(SyncSchema.POSITION_PREFIX).toInt()
                    if (pending.stamp() > stamp) {
                        while (texts.size <= index) texts += ""
                        texts[index] = kept.jsonPrimitive.content
                        positionStamps[index] = pending.stamp()
                    }
                }
                pending.field == SyncSchema.DELETED_AT -> kept.jsonObject[SyncSchema.DELETED_AT].asLong()?.let { values.keepEarliestDeletion(it) }
                pending.stamp() > stamp -> {
                    kept.jsonObject.filterKeys { it in table.columns || it == SyncSchema.UPDATED_AT }.forEach { (field, value) -> values[field] = value }
                    wonOverInsert += SyncFieldClockEntity(table.name, put.id, pending.field, pending.clock, pending.device)
                }
            }
        }
        if (texts != null) {
            values[SyncSchema.NOTE_FIELDS] = NoteFields.toColumn(texts)
            texts.forEachIndexed { index, text ->
                val at = positionStamps[index] ?: stamp
                wonOverInsert += SyncFieldClockEntity(table.name, put.id, SyncSchema.position(index), at.clock, at.device, base = NoteFields.hash(text))
            }
        }
        rows.insert(table.name, put.id, values)
        if (earlier.isNotEmpty()) dao.deleteFieldClocks(table.name, put.id, earlier.map { it.field })
        dao.putFieldClocks(listOf(SyncFieldClockEntity(table.name, put.id, SyncSchema.ALL, stamp.clock, stamp.device)) + wonOverInsert)

        when (table.name) {
            SyncSchema.CARDS -> context.cards += put.id
            SyncSchema.REVIEW_LOGS -> values["cardId"]?.jsonPrimitive?.content?.let { context.cards += it }
        }
    }

    /** An edit of a row this device doesn't have yet. Only the newest value of each unit is kept. */
    private suspend fun stash(table: SyncedTable, put: Change.Put, stamp: SyncStamp, clocks: List<SyncFieldClockEntity>) {
        val kept = clocks.filter { it.value != null }.associateBy { it.field }
        val keep = ArrayList<SyncFieldClockEntity>()
        val seen = HashSet<String>()
        for ((field, value) in put.fields) {
            if (SyncSchema.isPositional(table, field)) {
                val texts = NoteFields.parse(value)
                val positions = NoteFields.positions(put.fields[SyncSchema.FIELDS_CHANGED]) ?: texts.indices.toList()
                for (index in positions) {
                    val text = texts.getOrNull(index) ?: continue
                    val before = kept[SyncSchema.position(index)]?.stamp() ?: SyncStamp.NONE
                    if (stamp > before) keep += pendingRow(table, put, SyncSchema.position(index), stamp, JsonPrimitive(text))
                }
                continue
            }
            when (field) {
                SyncSchema.DELETED_AT -> {
                    val incoming = value.asLong() ?: continue
                    val earlier = kept[field]?.value?.let { Json.parseToJsonElement(it).jsonObject[field].asLong() }
                    if (earlier == null || incoming < earlier) keep += pendingRow(table, put, field, stamp, JsonObject(mapOf(field to value)))
                }
                else -> {
                    val group = SyncSchema.groupOf(table, field) ?: continue
                    if (!seen.add(group)) continue
                    val before = kept[group]?.stamp() ?: SyncStamp.NONE
                    if (stamp <= before) continue
                    val fields = SyncSchema.fieldsOf(table, group).filter { it in put.fields }.associateWith { put.fields.getValue(it) }
                    keep += pendingRow(table, put, group, stamp, JsonObject(fields))
                }
            }
        }
        if (keep.isNotEmpty()) dao.putFieldClocks(keep)
    }

    private fun pendingRow(table: SyncedTable, put: Change.Put, field: String, stamp: SyncStamp, value: JsonElement) =
        SyncFieldClockEntity(table.name, put.id, field, stamp.clock, stamp.device, Json.encodeToString(JsonElement.serializer(), value))

    private suspend fun applyExisting(
        table: SyncedTable,
        put: Change.Put,
        stamp: SyncStamp,
        clocks: List<SyncFieldClockEntity>,
        existing: JsonObject,
        context: Context,
    ) {
        val stamped = clocks.filter { it.value == null }.associateBy { it.field }
        val wholeRow = stamped[SyncSchema.ALL]?.stamp() ?: SyncStamp.NONE
        fun stampOf(group: String) = maxOf(stamped[group]?.stamp() ?: SyncStamp.NONE, wholeRow)
        val unsent = context.pending[table.name to put.id]

        val updates = LinkedHashMap<String, JsonElement>()
        val won = LinkedHashSet<String>()
        val seen = HashSet<String>()
        val positionStamps = ArrayList<SyncFieldClockEntity>()
        for ((field, value) in put.fields) {
            if (SyncSchema.isPositional(table, field)) {
                mergePositions(put, stamp, value, stamped, wholeRow, existing, updates, positionStamps)
                continue
            }
            when (field) {
                SyncSchema.CREATED_AT -> value.asLong()?.let { if (it < existing.getValue(field).asLong()!!) updates[field] = JsonPrimitive(it) }
                SyncSchema.DELETED_AT -> value.asLong()?.let { deletedAt ->
                    val mine = existing[field].asLong()
                    if (mine == null || deletedAt < mine) updates[field] = JsonPrimitive(deletedAt)
                }
                else -> {
                    val group = SyncSchema.groupOf(table, field) ?: continue
                    if (!seen.add(group)) continue
                    val columns = SyncSchema.fieldsOf(table, group)
                    if (unsent != null && (unsent.all || group == SyncSchema.UPDATED_AT || columns.any { it in unsent.fields })) continue
                    if (stamp <= stampOf(group)) continue
                    for (column in columns) put.fields[column]?.let { updates[column] = it }
                    won += group
                }
            }
        }

        rows.update(table.name, put.id, updates)
        if (positionStamps.isNotEmpty()) dao.putFieldClocks(positionStamps)
        if (won.isNotEmpty()) {
            // A note keeps a stamp per field text, so its row stamp can't stand for them all.
            if (table.name != SyncSchema.NOTES && SyncSchema.isFull(table, put.fields.keys) && won == SyncSchema.groupsOf(table)) {
                dao.deleteFieldSpecificClocks(table.name, put.id)
                dao.putFieldClocks(listOf(SyncFieldClockEntity(table.name, put.id, SyncSchema.ALL, stamp.clock, stamp.device)))
            } else {
                dao.putFieldClocks(won.map { SyncFieldClockEntity(table.name, put.id, it, stamp.clock, stamp.device) })
            }
        }

        when (table.name) {
            SyncSchema.CARDS -> if (SyncSchema.SCHEDULE in won) context.cards += put.id
            SyncSchema.REVIEW_LOGS -> if (updates.isNotEmpty()) {
                (existing["cardId"] ?: put.fields["cardId"])?.jsonPrimitive?.content?.let { context.cards += it }
            }
        }
    }

    /**
     * Merges a note's field texts one position at a time: each text the change made replaces the local one if its stamp
     * is newer, unless the text was edited here since the last sync (it differs from its baseline).
     */
    private fun mergePositions(
        put: Change.Put,
        stamp: SyncStamp,
        value: JsonElement,
        stamped: Map<String, SyncFieldClockEntity>,
        wholeRow: SyncStamp,
        existing: JsonObject,
        updates: MutableMap<String, JsonElement>,
        positionStamps: MutableList<SyncFieldClockEntity>,
    ) {
        val incoming = NoteFields.parse(value)
        val positions = NoteFields.positions(put.fields[SyncSchema.FIELDS_CHANGED]) ?: incoming.indices.toList()
        val texts = NoteFields.parse(existing[SyncSchema.NOTE_FIELDS]).toMutableList()
        var changed = false
        for (index in positions) {
            val text = incoming.getOrNull(index) ?: continue
            val key = SyncSchema.position(index)
            val row = stamped[key]
            val here = texts.getOrNull(index)
            if (here != null && row?.base != null && NoteFields.hash(here) != row.base) continue
            if (stamp <= maxOf(row?.stamp() ?: SyncStamp.NONE, wholeRow)) continue
            while (texts.size <= index) texts += ""
            texts[index] = text
            changed = true
            positionStamps += SyncFieldClockEntity(put.table, put.id, key, stamp.clock, stamp.device, base = NoteFields.hash(text))
        }
        if (changed) updates[SyncSchema.NOTE_FIELDS] = NoteFields.toColumn(texts)
    }

    private fun MutableMap<String, JsonElement>.keepEarliestDeletion(deletedAt: Long) {
        val mine = this[SyncSchema.DELETED_AT].asLong()
        if (mine == null || deletedAt < mine) this[SyncSchema.DELETED_AT] = JsonPrimitive(deletedAt)
    }

    private fun JsonElement?.asLong(): Long? = if (this == null || this is JsonNull) null else (this as? JsonPrimitive)?.longOrNull
}
