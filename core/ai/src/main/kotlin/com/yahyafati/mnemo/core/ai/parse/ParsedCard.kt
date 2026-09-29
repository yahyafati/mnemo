package com.yahyafati.mnemo.core.ai.parse

import com.yahyafati.mnemo.core.model.NoteKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A card as a model described it, before validation against the deck. Cloze syntax is normalized;
 * whether it is complete is for the validator to decide.
 */
data class ParsedCard(
    val kind: NoteKind,
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
)

/**
 * Reads a card out of whatever object a model wrote. The schema asks for `type`, `front`, `back`
 * and `tags`, but models without structured output use their own names (`question`/`answer`,
 * `text`/`extra`, `q`/`a` …), put tags in a string, or add multiple-choice `options`.
 */
internal object CardFields {
    private val FRONT = listOf("front", "question", "q", "prompt", "text", "cloze", "clozetext", "sentence", "term", "statement")
    private val BACK = listOf("back", "answer", "a", "definition", "extra", "explanation", "response", "solution")
    private val TAGS = listOf("tags", "tag", "topics", "keywords")
    private val OPTIONS = listOf("options", "choices", "answers")

    /** The card [obj] describes, or null if it isn't one (no front, or a Basic card with no back). */
    fun from(obj: JsonObject): ParsedCard? {
        val fields = obj.entries.associate { (key, value) -> normalizeKey(key) to value }
        val rawFront = FRONT.firstNotNullOfOrNull { fields[it]?.asText() }?.trim().orEmpty()
        val back = BACK.firstNotNullOfOrNull { fields[it]?.asText() }?.trim().orEmpty()
        if (rawFront.isEmpty()) return null
        val front = normalizeCloze(rawFront)

        // Any deletion marks a cloze card, even a broken one: the validator reports those.
        // A "cloze" type without deletions is a question and answer, so it's kept as Basic.
        val kind = if (CLOZE_OPENING.containsMatchIn(front)) NoteKind.Cloze else NoteKind.Basic
        if (kind == NoteKind.Basic && back.isEmpty()) return null

        val options = OPTIONS.firstNotNullOfOrNull { fields[it] as? JsonArray }
            ?.mapNotNull { it.asText()?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()
        val fullFront = if (kind == NoteKind.Basic && options.size >= 2 && options.none { it in front }) {
            front + "\n\n" + options.mapIndexed { index, option -> "- ${optionLabel(index, option)}" }.joinToString("\n")
        } else {
            front
        }
        val tags = TAGS.firstNotNullOfOrNull { fields[it] }?.let(::tagsOf).orEmpty()
        return ParsedCard(kind, fullFront, back, tags)
    }

    /** `{{C1: x}}`, `{{c1 :: x}}` and `{{ c1::x }}` → `{{c1::x}}`. */
    fun normalizeCloze(text: String): String =
        CLOZE_OPENING.replace(text) { "{{c${it.groupValues[1]}::" }

    private val CLOZE_OPENING = Regex("""\{\{\s*[cC]\s*(\d+)\s*:{1,2}\s*""")

    private fun optionLabel(index: Int, option: String): String {
        // Options that already carry a letter ("A. Paris", "B) Rome") keep it.
        if (Regex("""^[A-Ha-h][.)]\s""").containsMatchIn(option)) return option
        return "${'A' + index}. $option"
    }

    private fun tagsOf(element: JsonElement): List<String> {
        val raw = when (element) {
            is JsonArray -> element.mapNotNull { it.asText() }
            else -> element.asText()?.split(',', ';', ' ').orEmpty()
        }
        return raw.map { it.trim().removePrefix("#").trim().replace(Regex("\\s+"), "-").lowercase() }
            .filter { it.isNotEmpty() && it.length <= MAX_TAG_LENGTH }
            .distinct()
            .take(MAX_TAGS)
    }

    private fun JsonElement.asText(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        is JsonArray -> mapNotNull { it.asText() }.takeIf { it.isNotEmpty() }?.joinToString("\n")
        is JsonObject -> null
    }

    private fun normalizeKey(key: String) = key.lowercase().filter { it.isLetterOrDigit() }

    const val MAX_TAGS = 5
    const val MAX_TAG_LENGTH = 40
}
