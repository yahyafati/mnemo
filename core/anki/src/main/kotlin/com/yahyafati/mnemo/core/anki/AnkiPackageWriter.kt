package com.yahyafati.mnemo.core.anki

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes an `.apkg` in the legacy schema-11 format (`collection.anki2` plus a JSON media map),
 * which every Anki version and AnkiDroid can import.
 *
 * Rows go straight into a temporary SQLite file in [workDir] inside one transaction, so a large
 * export never sits in memory. Call [begin], then add rows in any order, then [finish].
 */
class AnkiPackageWriter(driver: SQLiteDriver, private val workDir: File) : Closeable {
    private val collection = File(workDir.apply { mkdirs() }, ApkgReader.COLLECTION_LEGACY1).apply { delete() }
    private val db: SQLiteConnection = driver.open(collection.path)
    private val media = mutableListOf<Pair<String, () -> InputStream>>()
    private var open = true

    private val insertNote: SQLiteStatement
    private val insertCard: SQLiteStatement
    private val insertRevlog: SQLiteStatement

    init {
        SCHEMA.forEach { db.execSQL(it) }
        db.execSQL("BEGIN")
        insertNote = db.prepare("INSERT INTO notes VALUES (?, ?, ?, ?, -1, ?, ?, ?, ?, 0, ?)")
        insertCard = db.prepare("INSERT INTO cards VALUES (?, ?, ?, ?, ?, -1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
        insertRevlog = db.prepare("INSERT INTO revlog VALUES (?, ?, -1, ?, ?, ?, ?, ?, ?)")
    }

    /** Writes the collection row: when it was [created] (review due days count from there), decks and note types. */
    fun begin(created: Instant, decks: List<AnkiDeck>, notetypes: List<AnkiNotetype>) {
        val now = Instant.now()
        db.prepare("INSERT INTO col VALUES (1, ?, ?, ?, 11, 0, 0, 0, ?, ?, ?, ?, '{}')").use { s ->
            s.bindLong(1, created.epochSecond)
            s.bindLong(2, now.toEpochMilli())
            s.bindLong(3, now.toEpochMilli())
            s.bindText(4, conf(notetypes.firstOrNull()?.id ?: 0).toString())
            s.bindText(5, JsonObject(notetypes.associate { it.id.toString() to model(it, now) }).toString())
            s.bindText(6, decksJson(decks, now).toString())
            s.bindText(7, JsonObject(mapOf("1" to DEFAULT_DECK_CONFIG)).toString())
            s.step()
        }
    }

    fun addNotes(notes: List<AnkiNote>) = notes.forEach { n ->
        insertNote.run {
            bindLong(1, n.id)
            bindText(2, n.guid)
            bindLong(3, n.notetypeId)
            bindLong(4, n.modified)
            bindText(5, if (n.tags.isEmpty()) "" else n.tags.joinToString(" ", prefix = " ", postfix = " "))
            bindText(6, n.fields.joinToString("\u001f"))
            bindText(7, sortField(n.fields.firstOrNull().orEmpty()))
            bindLong(8, checksum(n.fields.firstOrNull().orEmpty()))
            bindText(9, n.data)
            stepAndReset()
        }
    }

    fun addCards(cards: List<AnkiCard>) = cards.forEach { c ->
        insertCard.run {
            bindLong(1, c.id)
            bindLong(2, c.noteId)
            bindLong(3, c.deckId)
            bindLong(4, c.ord.toLong())
            bindLong(5, c.modified)
            bindLong(6, c.type.toLong())
            bindLong(7, c.queue.toLong())
            bindLong(8, c.due)
            bindLong(9, c.interval.toLong())
            bindLong(10, c.factor.toLong())
            bindLong(11, c.reps.toLong())
            bindLong(12, c.lapses.toLong())
            bindLong(13, c.left.toLong())
            bindLong(14, c.originalDue)
            bindLong(15, c.originalDeckId)
            bindLong(16, c.flags.toLong())
            bindText(17, c.data)
            stepAndReset()
        }
    }

    fun addRevlog(entries: List<AnkiRevlog>) = entries.forEach { r ->
        insertRevlog.run {
            bindLong(1, r.id)
            bindLong(2, r.cardId)
            bindLong(3, r.ease.toLong())
            bindLong(4, r.interval.toLong())
            bindLong(5, r.lastInterval.toLong())
            bindLong(6, r.factor.toLong())
            bindLong(7, r.timeMs.toLong())
            bindLong(8, r.type.toLong())
            stepAndReset()
        }
    }

    /** Adds a media file. [name] must be unique: note fields refer to media by name. */
    fun addMedia(name: String, open: () -> InputStream) {
        media += name to open
    }

    /** Commits the collection and writes the package to [output]. The caller closes [output]. */
    fun finish(output: OutputStream) {
        db.execSQL("COMMIT")
        closeDatabase()
        val zip = ZipOutputStream(output)
        zip.putNextEntry(ZipEntry(ApkgReader.META))
        zip.write(byteArrayOf(0x08, 0x01)) // PackageMetadata { version: LEGACY_1 }
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(ApkgReader.COLLECTION_LEGACY1))
        collection.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        media.forEachIndexed { index, (_, open) ->
            zip.putNextEntry(ZipEntry(index.toString()))
            open().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry(ApkgReader.MEDIA))
        val map = JsonObject(media.mapIndexed { index, (name, _) -> index.toString() to JsonPrimitive(name) }.toMap())
        zip.write(map.toString().toByteArray())
        zip.closeEntry()
        zip.finish()
    }

    override fun close() {
        closeDatabase()
        workDir.deleteRecursively()
    }

    private fun closeDatabase() {
        if (!open) return
        open = false
        insertNote.close()
        insertCard.close()
        insertRevlog.close()
        db.close()
    }

    private fun SQLiteStatement.stepAndReset() {
        step()
        reset()
        clearBindings()
    }

    private fun conf(currentModel: Long) = buildJsonObject {
        put("activeDecks", buildJsonArray { add(1) })
        put("curDeck", 1)
        put("newSpread", 0)
        put("collapseTime", 1200)
        put("timeLim", 0)
        put("estTimes", true)
        put("dueCounts", true)
        put("curModel", currentModel)
        put("nextPos", 1)
        put("sortType", "noteFld")
        put("sortBackwards", false)
        put("addToCur", true)
        put("dayLearnFirst", false)
        put("schedVer", 2)
        put("sched2021", true)
    }

    private fun model(type: AnkiNotetype, now: Instant) = buildJsonObject {
        put("id", type.id)
        put("name", type.name)
        put("type", if (type.isCloze) 1 else 0)
        put("mod", now.epochSecond)
        put("usn", -1)
        put("sortf", 0)
        put("did", JsonNull)
        putJsonArray("tmpls") {
            type.templates.forEach { t ->
                add(
                    buildJsonObject {
                        put("name", t.name)
                        put("ord", t.ord)
                        put("qfmt", t.front)
                        put("afmt", t.back)
                        put("bqfmt", "")
                        put("bafmt", "")
                        put("did", JsonNull)
                        put("bfont", "")
                        put("bsize", 0)
                    },
                )
            }
        }
        putJsonArray("flds") {
            type.fields.forEachIndexed { ord, name ->
                add(
                    buildJsonObject {
                        put("name", name)
                        put("ord", ord)
                        put("sticky", false)
                        put("rtl", false)
                        put("font", "Arial")
                        put("size", 20)
                        put("description", "")
                        put("plainText", false)
                        put("collapsed", false)
                        put("excludeFromSearch", false)
                    },
                )
            }
        }
        put("css", if (type.isCloze) CLOZE_CSS else CSS)
        put("latexPre", LATEX_PRE)
        put("latexPost", LATEX_POST)
        put("latexsvg", false)
        putJsonArray("req") {
            type.templates.forEach { t ->
                val required = if (type.isCloze) {
                    listOf(0)
                } else {
                    AnkiTemplates.frontFields(t.front, type.fields).ifEmpty { listOf(0) }
                }
                addJsonArray {
                    add(t.ord)
                    add("any")
                    addJsonArray { required.forEach { add(it) } }
                }
            }
        }
        put("tags", JsonArray(emptyList()))
        put("vers", JsonArray(emptyList()))
    }

    private fun decksJson(decks: List<AnkiDeck>, now: Instant): JsonObject {
        val all = if (decks.any { it.id == DEFAULT_DECK_ID }) decks else listOf(AnkiDeck(DEFAULT_DECK_ID, "Default")) + decks
        return JsonObject(
            all.associate { deck ->
                deck.id.toString() to buildJsonObject {
                    put("id", deck.id)
                    put("mod", now.epochSecond)
                    put("name", deck.name)
                    put("usn", -1)
                    listOf("lrnToday", "revToday", "newToday", "timeToday").forEach { key ->
                        putJsonArray(key) {
                            add(0)
                            add(0)
                        }
                    }
                    put("collapsed", false)
                    put("browserCollapsed", false)
                    put("desc", deck.description)
                    put("dyn", 0)
                    put("conf", 1)
                    put("extendNew", 0)
                    put("extendRev", 0)
                    deck.mnemoCategory?.let { put(KEY_CATEGORY, it) }
                    if (deck.mnemoStarred) put(KEY_STARRED, true)
                }
            },
        )
    }

    internal companion object {
        const val DEFAULT_DECK_ID = 1L

        /** Mnemo's own deck keys in the legacy deck JSON; Anki ignores keys it doesn't know. */
        const val KEY_CATEGORY = "mnemoCategory"
        const val KEY_STARRED = "mnemoStarred"

        private const val CSS =
            ".card {\n    font-family: arial;\n    font-size: 20px;\n    line-height: 1.5;\n    text-align: center;\n" +
                "    color: black;\n    background-color: white;\n}\n"
        private const val CLOZE_CSS =
            CSS + ".cloze {\n    font-weight: bold;\n    color: blue;\n}\n.nightMode .cloze {\n    color: lightblue;\n}\n"
        private const val LATEX_PRE =
            "\\documentclass[12pt]{article}\n\\special{papersize=3in,5in}\n\\usepackage[utf8]{inputenc}\n" +
                "\\usepackage{amssymb,amsmath}\n\\pagestyle{empty}\n\\setlength{\\parindent}{0in}\n\\begin{document}\n"
        private const val LATEX_POST = "\\end{document}"

        private val DEFAULT_DECK_CONFIG = buildJsonObject {
            put("id", 1)
            put("mod", 0)
            put("name", "Default")
            put("usn", 0)
            put("maxTaken", 60)
            put("autoplay", true)
            put("timer", 0)
            put("replayq", true)
            put(
                "new",
                buildJsonObject {
                    put("bury", false)
                    putJsonArray("delays") {
                        add(1.0)
                        add(10.0)
                    }
                    put("initialFactor", 2500)
                    putJsonArray("ints") {
                        add(1)
                        add(4)
                        add(0)
                    }
                    put("order", 1)
                    put("perDay", 20)
                },
            )
            put(
                "rev",
                buildJsonObject {
                    put("bury", false)
                    put("ease4", 1.3)
                    put("ivlFct", 1.0)
                    put("maxIvl", 36500)
                    put("perDay", 200)
                    put("hardFactor", 1.2)
                },
            )
            put(
                "lapse",
                buildJsonObject {
                    putJsonArray("delays") { add(10.0) }
                    put("leechAction", 1)
                    put("leechFails", 8)
                    put("minInt", 1)
                    put("mult", 0.0)
                },
            )
            put("dyn", false)
        }

        private val SCHEMA = listOf(
            """CREATE TABLE col (id integer primary key, crt integer not null, mod integer not null,
                scm integer not null, ver integer not null, dty integer not null, usn integer not null,
                ls integer not null, conf text not null, models text not null, decks text not null,
                dconf text not null, tags text not null)""",
            """CREATE TABLE notes (id integer primary key, guid text not null, mid integer not null,
                mod integer not null, usn integer not null, tags text not null, flds text not null,
                sfld integer not null, csum integer not null, flags integer not null, data text not null)""",
            """CREATE TABLE cards (id integer primary key, nid integer not null, did integer not null,
                ord integer not null, mod integer not null, usn integer not null, type integer not null,
                queue integer not null, due integer not null, ivl integer not null, factor integer not null,
                reps integer not null, lapses integer not null, left integer not null, odue integer not null,
                odid integer not null, flags integer not null, data text not null)""",
            """CREATE TABLE revlog (id integer primary key, cid integer not null, usn integer not null,
                ease integer not null, ivl integer not null, lastIvl integer not null, factor integer not null,
                time integer not null, type integer not null)""",
            "CREATE TABLE graves (usn integer not null, oid integer not null, type integer not null)",
            "CREATE INDEX ix_notes_usn on notes (usn)",
            "CREATE INDEX ix_cards_usn on cards (usn)",
            "CREATE INDEX ix_revlog_usn on revlog (usn)",
            "CREATE INDEX ix_cards_nid on cards (nid)",
            "CREATE INDEX ix_cards_sched on cards (did, queue, due)",
            "CREATE INDEX ix_revlog_cid on revlog (cid)",
            "CREATE INDEX ix_notes_csum on notes (csum)",
        )

        /** Anki's sort field: the first field without HTML. */
        fun sortField(html: String): String = AnkiHtml.stripHtml(html)

        /** Anki's duplicate checksum: the first 8 hex digits of SHA-1 of the stripped first field. */
        fun checksum(html: String): Long {
            val digest = java.security.MessageDigest.getInstance("SHA-1").digest(sortField(html).toByteArray())
            return ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
                ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
        }
    }
}
