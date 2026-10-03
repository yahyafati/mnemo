package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.entity.SyncFieldClockEntity
import com.yahyafati.mnemo.core.database.entity.SyncSeqEntity
import com.yahyafati.mnemo.core.database.sync.SYNCED_TABLES
import com.yahyafati.mnemo.core.database.sync.SyncRows
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import com.yahyafati.mnemo.core.sync.format.SnapshotMeta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream

/**
 * One line of a snapshot, which is a text of JSON lines: a header, then what the device that wrote it had applied
 * ([Seq]), the scheduling settings, the media files that nothing referenced, the stamps ([Stamp]) and the rows
 * ([Row]). Lines, not one document, so that a collection of a hundred thousand reviews is read and written a row at a
 * time and never as one tree.
 */
@Serializable
internal sealed class SnapshotLine {
    @Serializable
    @SerialName("header")
    data class Header(val version: Int, val deviceId: String, val clock: Long) : SnapshotLine()

    /** A [SyncSeqEntity]: the newest change file applied from [device] and the late ones still awaited. */
    @Serializable
    @SerialName("seq")
    data class Seq(val device: String, val seq: Long, val gaps: String = "") : SnapshotLine()

    /** The scheduling settings last synced, with their stamp. */
    @Serializable
    @SerialName("settings")
    data class Settings(val clock: Long, val device: String, val record: JsonObject) : SnapshotLine()

    /** A media file in the location that no live row referenced when [since] (epoch milliseconds) was first noted. */
    @Serializable
    @SerialName("unreferenced")
    data class Unreferenced(val hash: String, val since: Long) : SnapshotLine()

    /** A [SyncFieldClockEntity]. */
    @Serializable
    @SerialName("stamp")
    data class Stamp(
        val tbl: String,
        val rowId: String,
        val field: String,
        val clock: Long,
        val device: String,
        val value: String? = null,
        val base: String? = null,
    ) : SnapshotLine()

    /** A row of a synced table: [id] is its key (parts joined with `/`), [values] the other columns. */
    @Serializable
    @SerialName("row")
    data class Row(val tbl: String, val id: String, val values: JsonObject) : SnapshotLine()
}

/** What the top of a snapshot says; enough to know what it covers without reading the rest. */
internal class SnapshotHead(
    val deviceId: String,
    val clock: Long,
    val seqs: List<SyncSeqEntity>,
    val unreferenced: Map<String, Long>,
) {
    fun meta() = SnapshotMeta(
        deviceId = deviceId,
        clock = clock,
        applied = seqs.associate { it.deviceId to it.seq },
        gaps = seqs.filter { it.gaps.isNotBlank() }.associate { it.deviceId to parseGaps(it.gaps) },
    )

    companion object {
        fun parseGaps(text: String): List<Long> = text.split(',').mapNotNull { it.toLongOrNull() }
    }
}

internal class BuiltSnapshot(val bytes: ByteArray, val head: SnapshotHead)

/**
 * The whole collection as a file (docs/sync/ROADMAP.md S4, ADR 0013 "As built (S4)"): every row of the synced tables
 * with the stamps this device keeps for them, and where it stands in each device's change files. A device that joins
 * loads it and is then in the state of the one that wrote it: it applies the files after that, and every later
 * merge decides as it would there.
 *
 * Taken right after a sync round, by the one device that has the sync mutex, so the stamps match the rows. Rows
 * written since and not sent yet are in it with their new values and the old stamps, and their change files, which
 * come after, replace them with newer stamps, so nothing is lost or reordered.
 */
internal class SyncSnapshots(
    private val database: MnemoDatabase,
    private val transaction: TransactionRunner,
    private val syncClock: SyncClock,
    userSettings: UserSettingsRepository,
) {
    private val dao = database.syncDao()
    private val rows = SyncRows(database)
    private val settings = SchedulingSettings(userSettings)

    /** The snapshot of this device's collection. [unreferenced] is the media clean-up's memory (see `SyncMaintenance`). */
    suspend fun build(deviceId: String, unreferenced: Map<String, Long>): BuiltSnapshot {
        val clock = syncClock.now()
        val out = ByteArrayOutputStream()
        fun write(line: SnapshotLine) {
            out.write(json.encodeToString(SnapshotLine.serializer(), line).toByteArray(Charsets.UTF_8))
            out.write(NEWLINE)
        }

        val seqs = dao.getSeqs()
        write(SnapshotLine.Header(VERSION, deviceId, clock))
        seqs.forEach { write(SnapshotLine.Seq(it.deviceId, it.seq, it.gaps)) }
        dao.getFieldClocks(SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW)
            .firstOrNull { it.field == SyncSchema.SETTINGS_FIELD && it.value != null }
            ?.let { write(SnapshotLine.Settings(it.clock, it.device, SchedulingSettings.parse(it.value!!))) }
        unreferenced.forEach { (hash, since) -> write(SnapshotLine.Unreferenced(hash, since)) }

        var offset = 0
        while (true) {
            val page = dao.getFieldClocksPage(SyncSchema.SETTINGS, PAGE, offset)
            page.forEach { write(SnapshotLine.Stamp(it.tbl, it.rowId, it.field, it.clock, it.device, it.value, it.base)) }
            if (page.size < PAGE) break
            offset += page.size
        }
        for (table in SYNCED_TABLES) {
            var after = 0L
            while (true) {
                val (page, next) = rows.page(table.name, after, PAGE)
                page.forEach { write(SnapshotLine.Row(table.name, it.id, it.values)) }
                if (page.size < PAGE) break
                after = next
            }
        }
        val bytes = out.toByteArray()
        return BuiltSnapshot(bytes, head(bytes))
    }

    /** The header, the positions and the unreferenced media of [bytes]; the stamps and rows are not read. */
    fun head(bytes: ByteArray): SnapshotHead {
        var header: SnapshotLine.Header? = null
        val seqs = ArrayList<SyncSeqEntity>()
        val unreferenced = LinkedHashMap<String, Long>()
        for (text in lines(bytes)) {
            when (val line = decode(text)) {
                is SnapshotLine.Header -> {
                    if (line.version > VERSION) throw SyncUnsupportedVersionException(line.version, VERSION)
                    header = line
                }
                is SnapshotLine.Seq -> seqs += SyncSeqEntity(line.device, line.seq, line.gaps)
                is SnapshotLine.Unreferenced -> unreferenced[line.hash] = line.since
                is SnapshotLine.Settings -> Unit
                is SnapshotLine.Stamp, is SnapshotLine.Row -> break
            }
            if (header == null) throw SyncCorruptException("A snapshot starts with its header")
        }
        val top = header ?: throw SyncCorruptException("A snapshot without a header")
        return SnapshotHead(top.deviceId, top.clock, seqs, unreferenced)
    }

    /**
     * Makes this device's collection the one in [bytes]: every synced row is replaced, the stamps and positions become
     * the writer's, the clock moves past everything in it and the scheduling settings are adopted. The database part is
     * one transaction (a snapshot that fails halfway changes nothing). Returns the number of rows loaded.
     */
    suspend fun load(bytes: ByteArray): Int {
        head(bytes) // Refuses a newer version before anything is touched.
        var maxClock = 0L
        var loaded = 0
        var settingsLine: SnapshotLine.Settings? = null
        transaction {
            dao.setApplying(true)
            for (table in SYNCED_TABLES) rows.deleteAll(table.name)
            dao.clearChanges()
            dao.clearFieldClocks()
            dao.clearSeqs()

            val seqs = ArrayList<SyncSeqEntity>()
            val stamps = ArrayList<SyncFieldClockEntity>()
            for (text in lines(bytes)) {
                when (val line = decode(text)) {
                    is SnapshotLine.Header -> maxClock = maxOf(maxClock, line.clock)
                    is SnapshotLine.Seq -> seqs += SyncSeqEntity(line.device, line.seq, line.gaps)
                    is SnapshotLine.Settings -> settingsLine = line
                    is SnapshotLine.Unreferenced -> Unit
                    is SnapshotLine.Stamp -> {
                        maxClock = maxOf(maxClock, line.clock)
                        stamps += SyncFieldClockEntity(line.tbl, line.rowId, line.field, line.clock, line.device, line.value, line.base)
                        if (stamps.size >= PAGE) {
                            dao.putFieldClocks(stamps.toList())
                            stamps.clear()
                        }
                    }
                    is SnapshotLine.Row -> {
                        val table = SyncSchema.table(line.tbl) ?: continue // A table a newer version added.
                        val values = line.values.filterKeys {
                            it in table.columns || it == SyncSchema.CREATED_AT || it == SyncSchema.UPDATED_AT
                        }
                        if (!SyncSchema.isFull(table, values.keys)) throw SyncCorruptException("A ${line.tbl} row misses columns")
                        rows.insert(line.tbl, line.id, values)
                        loaded++
                    }
                }
            }
            if (stamps.isNotEmpty()) dao.putFieldClocks(stamps)
            dao.putSeqs(seqs)
            dao.setApplying(false)
        }
        syncClock.observe(maxClock)

        settingsLine?.let { line ->
            settings.write(line.record)
            dao.putFieldClocks(
                listOf(
                    SyncFieldClockEntity(
                        SyncSchema.SETTINGS, SyncSchema.SETTINGS_ROW, SyncSchema.SETTINGS_FIELD, line.clock, line.device,
                        SchedulingSettings.text(settings.current()),
                    ),
                ),
            )
        }
        return loaded
    }

    private fun decode(text: String): SnapshotLine = try {
        json.decodeFromString(SnapshotLine.serializer(), text)
    } catch (e: SerializationException) {
        throw SyncCorruptException("A snapshot line isn't readable: ${e.message}")
    } catch (e: IllegalArgumentException) {
        throw SyncCorruptException("A snapshot line isn't readable: ${e.message}")
    }

    /** The lines of [bytes], decoded one at a time. A line has no raw newline: JSON escapes the ones in strings. */
    private fun lines(bytes: ByteArray): Sequence<String> = sequence {
        var start = 0
        while (start < bytes.size) {
            var end = start
            while (end < bytes.size && bytes[end] != NEWLINE_BYTE) end++
            if (end > start) yield(String(bytes, start, end - start, Charsets.UTF_8))
            start = end + 1
        }
    }

    private companion object {
        const val VERSION = 1
        const val PAGE = 1_000
        const val NEWLINE = '\n'.code
        const val NEWLINE_BYTE = '\n'.code.toByte()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    }
}
