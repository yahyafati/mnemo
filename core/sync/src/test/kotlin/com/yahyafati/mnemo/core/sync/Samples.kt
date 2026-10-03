package com.yahyafati.mnemo.core.sync

import com.yahyafati.mnemo.core.sync.format.Change
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Random

internal const val DEVICE_A = "0a1b2c3d-0000-4000-8000-00000000000a"
internal const val DEVICE_B = "0a1b2c3d-0000-4000-8000-00000000000b"

/** PBKDF2's lowest allowed work factor: the tests derive many keys. */
internal const val FAST_ITERATIONS = 1_000

/** One of each kind of change: strings with LaTeX and emoji, numbers, null, a list, a nested object, a delete. */
internal fun sampleChanges(startClock: Long = 100): List<Change> = listOf(
    Change.Put(
        "notes", "n-1", startClock,
        mapOf(
            "fields" to JsonArray(listOf(JsonPrimitive("What is \\(\\frac{a}{b}\\)?"), JsonPrimitive("A ratio 🙂 — «ünïcode»"))),
            "tags" to JsonPrimitive("math calc"),
            "hint" to JsonNull,
            "deletedAt" to JsonNull,
        ),
    ),
    Change.Put("cards", "c-1", startClock + 1, mapOf("due" to JsonPrimitive(1_785_000_000_000L), "stability" to JsonPrimitive(4.25), "suspended" to JsonPrimitive(false))),
    Change.Put("settings", "scheduling", startClock + 2, mapOf("weights" to JsonObject(mapOf("w" to JsonArray(List(21) { JsonPrimitive(it / 7.0) }))))),
    Change.Put("ai_answers", "n-1/EXPLAIN", startClock + 3, mapOf("text" to JsonPrimitive("line one\nline two\t\"quoted\""))),
    Change.Delete("review_logs", "r-9", startClock + 4),
)

/** Changes whose text doesn't compress, so a size cap really bites. */
internal fun randomChanges(count: Int, chars: Int, seed: Long = 7): List<Change> {
    val random = Random(seed)
    val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    return List(count) { i ->
        val text = String(CharArray(chars) { alphabet[random.nextInt(alphabet.length)] })
        Change.Put("notes", "n-$i", 1_000L + i, mapOf("fields" to JsonArray(listOf(JsonPrimitive(text)))))
    }
}
