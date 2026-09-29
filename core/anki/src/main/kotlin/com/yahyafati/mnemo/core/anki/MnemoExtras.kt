package com.yahyafati.mnemo.core.anki

import com.yahyafati.mnemo.core.model.NoteKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import java.util.zip.CRC32

// What Mnemo adds to the packages it exports, so importing its own export restores everything
// exactly (ADR 0003). Anki ignores or drops these, so a package that went through Anki falls back
// to converting the HTML, which is still faithful but not byte-identical.

/**
 * Stored in `notes.data`: the note's original id, Markdown and timestamps, and a checksum of the
 * HTML fields they were exported as. If the fields were edited since, the checksum no longer
 * matches and the HTML is converted instead.
 */
@Serializable
internal data class MnemoNoteData(
    val mnemo: Int = 1,
    val id: String,
    val fields: List<String>,
    val crc: Long,
    /** Epoch millis. Anki's note id is the creation time too, but nudged where ids collide. */
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    /** The note's `NoteKind` name (since Phase 6; older stashes infer it from the note type). */
    val kind: String? = null,
    /** The note's hint, which has no Anki field. */
    val hint: String? = null,
) {
    companion object {
        // encodeDefaults: the "mnemo" marker must be written even though it has a default.
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }

        fun encode(note: com.yahyafati.mnemo.core.model.Note, kind: NoteKind, htmlFields: List<String>): String = json.encodeToString(
            serializer(),
            MnemoNoteData(
                id = note.id,
                fields = note.fields,
                crc = crc(htmlFields),
                createdAt = note.createdAt.toEpochMilli(),
                updatedAt = note.updatedAt.toEpochMilli(),
                kind = kind.name,
                hint = note.hint?.takeIf { it.isNotBlank() },
            ),
        )

        /** The stash in [note], if it is one and still matches the note's fields. */
        fun decode(note: AnkiNote): MnemoNoteData? {
            if (!note.data.startsWith("{\"mnemo\"")) return null
            val data = runCatching { json.decodeFromString(serializer(), note.data) }.getOrNull() ?: return null
            return data.takeIf { it.crc == crc(note.fields) }
        }

        fun crc(fields: List<String>): Long = CRC32().apply { update(fields.joinToString("\u001f").toByteArray()) }.value
    }
}

/**
 * The parts of Anki's card `data` JSON Mnemo reads and writes: FSRS memory state (`s`, `d`,
 * `lrt`, which Anki itself uses) and Mnemo's exact schedule in Anki's `cd` custom-data string
 * (kept under Anki's 100-byte limit).
 */
internal data class AnkiCardData(
    val stability: Double? = null,
    val difficulty: Double? = null,
    /** Last review, epoch seconds. */
    val lastReviewSeconds: Long? = null,
    val extras: CardExtras? = null,
) {
    @Serializable
    data class CardExtras(
        /** Exact due, epoch millis (Anki only keeps review due dates to the day). */
        val due: Long? = null,
        /** Exact last review, epoch millis. */
        @SerialName("lr") val lastReview: Long? = null,
        /** Learning step. */
        @SerialName("st") val step: Int? = null,
        /** Created, epoch millis: the order new cards are introduced in. */
        @SerialName("ca") val createdAt: Long? = null,
    )

    fun encode(desiredRetention: Double): String {
        val map = linkedMapOf<String, JsonPrimitive>()
        if (stability != null && difficulty != null) {
            map["s"] = JsonPrimitive(stability)
            map["d"] = JsonPrimitive(difficulty)
            map["dr"] = JsonPrimitive(desiredRetention)
        }
        if (lastReviewSeconds != null) map["lrt"] = JsonPrimitive(lastReviewSeconds)
        if (extras != null) map["cd"] = JsonPrimitive(json.encodeToString(CardExtras.serializer(), extras))
        return JsonObject(map).toString()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun decode(data: String): AnkiCardData {
            if (data.isBlank() || data == "{}") return AnkiCardData()
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return AnkiCardData()
            fun number(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }
            val extras = (obj["cd"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { cd ->
                runCatching { json.decodeFromString(CardExtras.serializer(), cd) }.getOrNull()
            }
            return AnkiCardData(
                stability = number("s")?.doubleOrNull?.takeIf { it > 0 },
                difficulty = number("d")?.doubleOrNull?.takeIf { it in 1.0..10.0 },
                lastReviewSeconds = number("lrt")?.longOrNull?.takeIf { it > 0 },
                extras = extras,
            )
        }
    }
}

/** The note types Mnemo exports its kinds as. Fixed ids, so re-exports reuse them in Anki. */
internal object MnemoNotetypes {
    private const val ANSWER = "{{FrontSide}}\n\n<hr id=answer>\n\n"

    val Basic = AnkiNotetype(
        id = 1_600_000_000_001,
        name = "Mnemo Basic",
        isCloze = false,
        fields = listOf("Front", "Back"),
        templates = listOf(AnkiTemplate(0, "Card 1", "{{Front}}", ANSWER + "{{Back}}")),
    )

    val Reversed = AnkiNotetype(
        id = 1_600_000_000_002,
        name = "Mnemo Basic + Reversed",
        isCloze = false,
        fields = listOf("Front", "Back"),
        templates = listOf(
            AnkiTemplate(0, "Card 1", "{{Front}}", ANSWER + "{{Back}}"),
            AnkiTemplate(1, "Card 2", "{{Back}}", ANSWER + "{{Front}}"),
        ),
    )

    val Cloze = AnkiNotetype(
        id = 1_600_000_000_003,
        name = "Mnemo Cloze",
        isCloze = true,
        fields = listOf("Text", "Back Extra"),
        templates = listOf(AnkiTemplate(0, "Cloze", "{{cloze:Text}}", "{{cloze:Text}}<br>\n{{Back Extra}}")),
    )

    /** Anki's own "Basic (type in the answer)" layout: Anki shows a text box and compares. */
    val TypeIn = AnkiNotetype(
        id = 1_600_000_000_004,
        name = "Mnemo Type-in answer",
        isCloze = false,
        fields = listOf("Front", "Back"),
        templates = listOf(AnkiTemplate(0, "Card 1", "{{Front}}\n\n{{type:Back}}", "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}")),
    )

    /**
     * Anki has no multiple choice: its cards ask the question and show the answer. The wrong
     * answers are kept in their own field, so a round trip back into Mnemo restores the options.
     */
    val MultipleChoice = AnkiNotetype(
        id = 1_600_000_000_005,
        name = "Mnemo Multiple choice",
        isCloze = false,
        fields = listOf("Question", "Answer", "Wrong answers"),
        templates = listOf(AnkiTemplate(0, "Card 1", "{{Question}}", ANSWER + "{{Answer}}")),
    )

    val All = listOf(Basic, Reversed, Cloze, TypeIn, MultipleChoice)

    fun forKind(kind: NoteKind): AnkiNotetype = when (kind) {
        NoteKind.Basic -> Basic
        NoteKind.Reversed -> Reversed
        NoteKind.Cloze -> Cloze
        NoteKind.TypeIn -> TypeIn
        NoteKind.MultipleChoice -> MultipleChoice
    }
}

/** A deterministic Anki-style guid (base91 of 64 bits) for a Mnemo note that never had one. */
internal fun ankiGuidFor(noteId: String): String {
    val uuid = runCatching { UUID.fromString(noteId) }.getOrElse { UUID.nameUUIDFromBytes(noteId.toByteArray()) }
    var value = (uuid.mostSignificantBits xor uuid.leastSignificantBits).toULong()
    if (value == 0uL) return BASE91[0].toString()
    val out = StringBuilder()
    while (value > 0uL) {
        out.append(BASE91[(value % 91uL).toInt()])
        value /= 91uL
    }
    return out.reverse().toString()
}

/** Anki's guid alphabet. */
private const val BASE91 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~"
