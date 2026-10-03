package com.yahyafati.mnemo.core.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A note's `fields` column is a JSON list of texts, and two devices that edit different fields of one note must
 * both keep their edit (ADR 0013), so the list merges by position. These are the pieces both sides need.
 */
internal object NoteFields {
    /** The list stored in the column (a JSON text), formatted the way Room's converter writes it. */
    fun parse(column: JsonElement?): List<String> {
        val text = (column as? JsonPrimitive)?.contentOrNull ?: return emptyList()
        return Json.decodeFromString(text)
    }

    fun toColumn(fields: List<String>): JsonElement = JsonPrimitive(Json.encodeToString(fields))

    /** A stable fingerprint of one field's text (FNV-1a over its UTF-8 bytes): what a position looked like at the last sync. */
    fun hash(text: String): String {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (byte in text.toByteArray()) {
            h = (h xor (byte.toLong() and 0xff)) * 0x100000001b3L
        }
        return h.toULong().toString(16)
    }

    fun positions(element: JsonElement?): List<Int>? =
        (element as? kotlinx.serialization.json.JsonArray)?.map { it.jsonPrimitive.content.toInt() }
}
