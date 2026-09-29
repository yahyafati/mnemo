package com.yahyafati.mnemo.core.anki

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import com.squareup.zstd.okio.zstdDecompress
import com.yahyafati.mnemo.core.anki.internal.ProtoReader
import com.yahyafati.mnemo.core.anki.internal.allBytes
import com.yahyafati.mnemo.core.anki.internal.bytes
import com.yahyafati.mnemo.core.anki.internal.long
import com.yahyafati.mnemo.core.anki.internal.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okio.buffer
import okio.source
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Opens Anki packages (`.apkg` decks and `.colpkg` collections) in all three formats: the legacy
 * `collection.anki2` / `collection.anki21` (schema 11, JSON note types) and the current zstd
 * `collection.anki21b` (schema 18, protobuf note types, zstd media).
 *
 * SQLite goes through [driver], so this runs on Android (framework driver) and on the JVM
 * (bundled driver) alike.
 */
class ApkgReader(private val driver: SQLiteDriver) {
    /**
     * Opens [file]. [workDir] receives the extracted collection and is deleted again when the
     * returned package is closed.
     *
     * @throws AnkiFormatException if [file] is not an Anki package or can't be read.
     */
    fun open(file: File, workDir: File): AnkiPackage {
        val zip = try {
            ZipFile(file)
        } catch (e: ZipException) {
            throw AnkiFormatException("Not a zip file", e)
        }
        try {
            val version = zip.getEntry(META)?.let { entry ->
                ProtoReader.parse(zip.getInputStream(entry).use { it.readBytes() }).long(1)?.toInt()
            }
            if (version != null && version > VERSION_LATEST) throw AnkiFormatException("Package version $version is newer than supported")
            val (format, entryName) = when {
                zip.getEntry(COLLECTION_LATEST) != null -> AnkiFormat.Latest to COLLECTION_LATEST
                version == VERSION_LATEST -> throw AnkiFormatException("Package says it is current but has no $COLLECTION_LATEST")
                zip.getEntry(COLLECTION_LEGACY2) != null -> AnkiFormat.Legacy2 to COLLECTION_LEGACY2
                zip.getEntry(COLLECTION_LEGACY1) != null -> AnkiFormat.Legacy1 to COLLECTION_LEGACY1
                else -> throw AnkiFormatException("No Anki collection in the package")
            }

            workDir.mkdirs()
            val collection = File(workDir, "collection.sqlite")
            decompressedStream(zip.getInputStream(zip.getEntry(entryName))).use { input ->
                collection.outputStream().use { input.copyTo(it) }
            }
            val header = collection.inputStream().use { it.readUpTo(SQLITE_HEADER.size) }
            if (!header.contentEquals(SQLITE_HEADER)) throw AnkiFormatException("The collection is not a SQLite database")

            val connection = try {
                driver.open(collection.path)
            } catch (e: Exception) {
                throw AnkiFormatException("Can't open the collection", e)
            }
            return AnkiPackage(zip, connection, format, workDir)
        } catch (e: AnkiFormatException) {
            zip.close()
            throw e
        } catch (e: IOException) {
            zip.close()
            throw e
        } catch (e: Exception) {
            zip.close()
            throw AnkiFormatException("Can't read the package", e)
        }
    }

    internal companion object {
        const val META = "meta"
        const val MEDIA = "media"
        const val COLLECTION_LATEST = "collection.anki21b"
        const val COLLECTION_LEGACY2 = "collection.anki21"
        const val COLLECTION_LEGACY1 = "collection.anki2"
        const val VERSION_LATEST = 3
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray()
        private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())

        /** [input], decompressed if it starts with the zstd magic number (current-format entries do). */
        fun decompressedStream(input: InputStream): InputStream {
            val buffered = BufferedInputStream(input)
            buffered.mark(ZSTD_MAGIC.size)
            val start = buffered.readUpTo(ZSTD_MAGIC.size)
            buffered.reset()
            return if (start.contentEquals(ZSTD_MAGIC)) {
                buffered.source().zstdDecompress().buffer().inputStream()
            } else {
                buffered
            }
        }
    }
}

/** An open package. Reads are batched so large collections never sit in memory at once. */
class AnkiPackage internal constructor(
    private val zip: ZipFile,
    private val db: SQLiteConnection,
    val format: AnkiFormat,
    private val workDir: File,
) : Closeable {
    private val json = Json { ignoreUnknownKeys = true }

    /** When the source collection was created; review due dates count days from here. */
    val collectionCreated: Instant = Instant.ofEpochSecond(db.query("SELECT crt FROM col") { it.getLong(0) }.first())

    val notetypes: List<AnkiNotetype> = if (format == AnkiFormat.Latest) latestNotetypes() else legacyNotetypes()

    val decks: List<AnkiDeck> = if (format == AnkiFormat.Latest) latestDecks() else legacyDecks()

    val media: List<AnkiMedia> = readMediaList()

    val noteCount: Int get() = count("notes")

    val cardCount: Int get() = count("cards")

    /** Notes in id (creation) order, [batchSize] at a time. */
    fun notes(batchSize: Int = BATCH): Sequence<List<AnkiNote>> = sequence {
        var after = Long.MIN_VALUE
        while (true) {
            val batch = db.query(
                "SELECT id, guid, mid, mod, tags, flds, data FROM notes WHERE id > ? ORDER BY id LIMIT ?",
                bind = {
                    it.bindLong(1, after)
                    it.bindLong(2, batchSize.toLong())
                },
            ) { s ->
                AnkiNote(
                    id = s.getLong(0),
                    guid = s.getText(1),
                    notetypeId = s.getLong(2),
                    modified = s.getLong(3),
                    tags = s.getText(4).split(' ', '\t', '\n').filter { it.isNotBlank() },
                    fields = s.getText(5).split(FIELD_SEPARATOR),
                    data = if (s.isNull(6)) "" else s.getText(6),
                )
            }
            if (batch.isEmpty()) break
            yield(batch)
            after = batch.last().id
        }
    }

    fun cardsForNotes(noteIds: List<Long>): List<AnkiCard> = noteIds.chunked(IN_CHUNK).flatMap { chunk ->
        db.query(
            "SELECT id, nid, did, ord, mod, type, queue, due, ivl, factor, reps, lapses, left, odue, odid, flags, data " +
                "FROM cards WHERE nid IN (${chunk.joinToString(",") { "?" }}) ORDER BY nid, ord",
            bind = { s -> chunk.forEachIndexed { i, id -> s.bindLong(i + 1, id) } },
        ) { s ->
            AnkiCard(
                id = s.getLong(0),
                noteId = s.getLong(1),
                deckId = s.getLong(2),
                ord = s.getLong(3).toInt(),
                modified = s.getLong(4),
                type = s.getLong(5).toInt(),
                queue = s.getLong(6).toInt(),
                due = s.getLong(7),
                interval = s.getLong(8).toInt(),
                factor = s.getLong(9).toInt(),
                reps = s.getLong(10).toInt(),
                lapses = s.getLong(11).toInt(),
                left = s.getLong(12).toInt(),
                originalDue = s.getLong(13),
                originalDeckId = s.getLong(14),
                flags = s.getLong(15).toInt(),
                data = if (s.isNull(16)) "" else s.getText(16),
            )
        }
    }

    /** Review history of [cardIds], oldest first. */
    fun revlogForCards(cardIds: List<Long>): List<AnkiRevlog> = cardIds.chunked(IN_CHUNK).flatMap { chunk ->
        db.query(
            "SELECT id, cid, ease, ivl, lastIvl, factor, time, type FROM revlog " +
                "WHERE cid IN (${chunk.joinToString(",") { "?" }}) ORDER BY id",
            bind = { s -> chunk.forEachIndexed { i, id -> s.bindLong(i + 1, id) } },
        ) { s ->
            AnkiRevlog(
                id = s.getLong(0),
                cardId = s.getLong(1),
                ease = s.getLong(2).toInt(),
                interval = s.getLong(3).toInt(),
                lastInterval = s.getLong(4).toInt(),
                factor = s.getLong(5).toInt(),
                timeMs = s.getLong(6).toInt(),
                type = s.getLong(7).toInt(),
            )
        }
    }.sortedBy { it.id }

    /** The bytes of [media], decompressed. The caller closes the stream. */
    fun openMedia(media: AnkiMedia): InputStream {
        val entry = zip.getEntry(media.entryName) ?: throw AnkiFormatException("Missing media ${media.name}")
        return ApkgReader.decompressedStream(zip.getInputStream(entry))
    }

    override fun close() {
        try {
            db.close()
        } finally {
            zip.close()
            workDir.deleteRecursively()
        }
    }

    private fun count(table: String): Int = db.query("SELECT COUNT(*) FROM $table") { it.getLong(0).toInt() }.first()

    private fun legacyNotetypes(): List<AnkiNotetype> {
        val models = db.query("SELECT models FROM col") { it.getText(0) }.first()
        return json.parseToJsonElement(models).jsonObject.values.map { element ->
            val m = element.jsonObject
            AnkiNotetype(
                id = m.long("id") ?: 0,
                name = m.string("name").orEmpty(),
                isCloze = m.long("type") == 1L,
                fields = m["flds"]?.jsonArray.orEmpty().map { it.jsonObject }
                    .sortedBy { it.long("ord") ?: 0 }.map { it.string("name").orEmpty() },
                templates = m["tmpls"]?.jsonArray.orEmpty().map { it.jsonObject }
                    .sortedBy { it.long("ord") ?: 0 }
                    .mapIndexed { index, t ->
                        AnkiTemplate(
                            ord = t.long("ord")?.toInt() ?: index,
                            name = t.string("name").orEmpty(),
                            front = t.string("qfmt").orEmpty(),
                            back = t.string("afmt").orEmpty(),
                        )
                    },
            )
        }
    }

    private fun legacyDecks(): List<AnkiDeck> {
        val decks = db.query("SELECT decks FROM col") { it.getText(0) }.first()
        return json.parseToJsonElement(decks).jsonObject.values.map { element ->
            val d = element.jsonObject
            AnkiDeck(
                id = d.long("id") ?: 0,
                name = d.string("name").orEmpty(),
                description = d.string("desc").orEmpty(),
                filtered = d["dyn"]?.jsonPrimitive?.let { it.intOrNull == 1 || it.booleanOrNull == true } == true,
                mnemoCategory = d.string(AnkiPackageWriter.KEY_CATEGORY)?.ifBlank { null },
                mnemoStarred = d[AnkiPackageWriter.KEY_STARRED]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
    }

    private fun latestNotetypes(): List<AnkiNotetype> {
        val fields = db.query("SELECT ntid, ord, name FROM fields") { Triple(it.getLong(0), it.getLong(1).toInt(), it.getText(2)) }
            .groupBy({ it.first }) { it.second to it.third }
        val templates = db.query("SELECT ntid, ord, name, config FROM templates") { s ->
            val config = ProtoReader.parse(s.getBlob(3))
            s.getLong(0) to AnkiTemplate(
                ord = s.getLong(1).toInt(),
                name = s.getText(2),
                front = config.string(1).orEmpty(),
                back = config.string(2).orEmpty(),
            )
        }.groupBy({ it.first }) { it.second }
        return db.query("SELECT id, name, config FROM notetypes") { s ->
            val id = s.getLong(0)
            AnkiNotetype(
                id = id,
                name = s.getText(1),
                isCloze = ProtoReader.parse(s.getBlob(2)).long(1) == KIND_CLOZE,
                fields = fields[id].orEmpty().sortedBy { it.first }.map { it.second },
                templates = templates[id].orEmpty().sortedBy { it.ord },
            )
        }
    }

    private fun latestDecks(): List<AnkiDeck> = db.query("SELECT id, name, kind FROM decks") { s ->
        val kind = ProtoReader.parse(s.getBlob(2))
        val normal = kind.bytes(1)
        AnkiDeck(
            id = s.getLong(0),
            name = s.getText(1).replace(LATEST_DECK_SEPARATOR, "::"),
            description = normal?.let { ProtoReader.parse(it).string(4) }.orEmpty(),
            filtered = normal == null && kind.bytes(2) != null,
        )
    }

    private fun readMediaList(): List<AnkiMedia> {
        val entry = zip.getEntry(ApkgReader.MEDIA) ?: return emptyList()
        val bytes = ApkgReader.decompressedStream(zip.getInputStream(entry)).use { it.readBytes() }
        if (bytes.isEmpty()) return emptyList()
        return if (format == AnkiFormat.Latest) {
            // MediaEntries { repeated MediaEntry entries = 1 }; MediaEntry { name = 1; legacy_zip_filename = 255 }
            ProtoReader.parse(bytes).allBytes(1).mapIndexed { index, raw ->
                val fields = ProtoReader.parse(raw)
                AnkiMedia(entryName = (fields.long(255) ?: index.toLong()).toString(), name = fields.string(1).orEmpty())
            }
        } else {
            json.parseToJsonElement(bytes.decodeToString()).jsonObject.map { (entryName, name) ->
                AnkiMedia(entryName, name.jsonPrimitive.content)
            }
        }.filter { it.name.isNotEmpty() }
    }

    private companion object {
        const val BATCH = 500
        const val IN_CHUNK = 500
        const val FIELD_SEPARATOR = '\u001f'
        const val LATEST_DECK_SEPARATOR = "\u001f"
        const val KIND_CLOZE = 1L

        fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }

        fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

/** Up to [count] bytes; fewer only at the end of the stream. (`readNBytes` needs Android 13.) */
internal fun InputStream.readUpTo(count: Int): ByteArray {
    val buffer = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(buffer, read, count - read)
        if (n < 0) break
        read += n
    }
    return if (read == count) buffer else buffer.copyOf(read)
}

internal fun <T> SQLiteConnection.query(
    sql: String,
    bind: (SQLiteStatement) -> Unit = {},
    row: (SQLiteStatement) -> T,
): List<T> = prepare(sql).use { statement ->
    bind(statement)
    buildList { while (statement.step()) add(row(statement)) }
}
