package com.yahyafati.mnemo.core.ai.schema

import com.yahyafati.mnemo.core.ai.dto.ResponseFormat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The `response_format` JSON schemas, for models that support structured output (ADR 0005).
 * Strict mode wants every property required and no others, so optional values are empty strings
 * or arrays rather than missing.
 */
object GeneratedCardsSchema {
    /** `{"cards": [{"type": "basic"|"cloze"|"choice", "front", "back", "options": [...], "tags": [...]}]}`. */
    val cards: JsonObject = buildJsonObject {
        strictObject("cards") {
            putJsonObject("cards") {
                put("type", "array")
                putJsonObject("items") {
                    strictObject("type", "front", "back", "options", "tags") {
                        putJsonObject("type") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add(JsonPrimitive("basic"))
                                add(JsonPrimitive("cloze"))
                                add(JsonPrimitive("choice"))
                            }
                        }
                        putJsonObject("front") { put("type", "string") }
                        putJsonObject("back") { put("type", "string") }
                        putJsonObject("options") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                        putJsonObject("tags") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                    }
                }
            }
        }
    }

    /** A rewritten card: `{"front", "back"}`. */
    val rewrite: JsonObject = buildJsonObject {
        strictObject("front", "back") {
            putJsonObject("front") { put("type", "string") }
            putJsonObject("back") { put("type", "string") }
        }
    }

    val cardsFormat: ResponseFormat = ResponseFormat.jsonSchema("flashcards", cards)

    val rewriteFormat: ResponseFormat = ResponseFormat.jsonSchema("flashcard", rewrite)

    private fun JsonObjectBuilder.strictObject(vararg required: String, properties: JsonObjectBuilder.() -> Unit) {
        put("type", "object")
        putJsonObject("properties", properties)
        putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        put("additionalProperties", false)
    }
}
