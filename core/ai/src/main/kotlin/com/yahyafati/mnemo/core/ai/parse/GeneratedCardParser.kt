package com.yahyafati.mnemo.core.ai.parse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Turns a streamed reply into cards as soon as each one is complete (ARCHITECTURE §5.2, step 4).
 *
 * [feed] takes the text deltas in order and returns the cards they completed. It tracks JSON
 * structure without parsing the whole reply: whenever an object closes, it is read as a card
 * ([CardFields]); objects around a card (`{"cards": [...]}`) are skipped. So the reply may be
 * `{"cards": [...]}`, a bare array, one object per line, wrapped in a code fence, preceded by
 * prose, or cut off mid-card: every complete card counts. `<think>…</think>` blocks from
 * reasoning models are dropped.
 *
 * [finish] handles what can only be judged at the end: a reply with no card objects gets one more
 * look as a whole (JSON with the cards nested in strings, or plain "Q: … A: …" text).
 */
class GeneratedCardParser {
    private val buffer = StringBuilder()
    private var scanned = 0
    private var inString = false
    private var escaped = false
    private val stack = ArrayList<Frame>()

    private val think = ThinkFilter()
    private var cardCount = 0

    /** Cards found so far. */
    val count: Int get() = cardCount

    /** The reply so far, without reasoning blocks: for a repair request. */
    val text: String get() = buffer.toString()

    private class Frame(val open: Char, val start: Int) {
        /** A card was found inside; this object is a wrapper, not a card. */
        var holdsCard = false
    }

    fun feed(delta: String): List<ParsedCard> {
        val visible = think.feed(delta)
        if (visible.isEmpty()) return emptyList()
        buffer.append(visible)
        return scan()
    }

    /** Call once the reply is complete. Returns any cards only visible now. */
    fun finish(): List<ParsedCard> {
        val rest = think.flush()
        val cards = if (rest.isNotEmpty()) {
            buffer.append(rest)
            scan()
        } else {
            emptyList()
        }
        if (cardCount > 0) return cards
        val fallback = wholeReply().ifEmpty { PlainTextCards.parse(buffer.toString()) }
        cardCount += fallback.size
        return fallback
    }

    private fun scan(): List<ParsedCard> {
        val found = mutableListOf<ParsedCard>()
        while (scanned < buffer.length) {
            val index = scanned++
            val c = buffer[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                // Quotes only open strings inside JSON, so apostrophes in prose don't confuse the scan.
                '"' -> if (stack.isNotEmpty()) inString = true
                '{', '[' -> stack += Frame(c, index)
                '}', ']' -> {
                    val frame = stack.removeLastOrNull() ?: continue
                    if (c != '}' || frame.open != '{' || frame.holdsCard) continue
                    val card = (JsonRepair.parse(buffer.substring(frame.start, index + 1)) as? JsonObject)?.let(CardFields::from)
                    if (card != null) {
                        found += card
                        stack.forEach { it.holdsCard = true }
                    }
                }
            }
        }
        cardCount += found.size
        return found
    }

    /** The whole reply as JSON (repaired), with cards found anywhere in it, even inside strings. */
    private fun wholeReply(): List<ParsedCard> {
        val text = buffer.toString()
        val start = text.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return emptyList()
        val end = text.indexOfLast { it == '}' || it == ']' }
        val candidate = if (end > start) text.substring(start, end + 1) else text.substring(start)
        val element = JsonRepair.parse(candidate) ?: JsonRepair.parse(candidate, closeOpen = true) ?: return emptyList()
        return mutableListOf<ParsedCard>().also { collect(element, it, depth = 0) }
    }

    private fun collect(element: JsonElement, into: MutableList<ParsedCard>, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (element) {
            is JsonObject -> {
                val card = CardFields.from(element)
                if (card != null) into += card else element.values.forEach { collect(it, into, depth + 1) }
            }
            is JsonArray -> element.forEach { collect(it, into, depth + 1) }
            is JsonPrimitive -> if (element.isString) {
                // A model that put the JSON in a string: {"cards": "[{…}]"}.
                val nested = element.content.trim()
                if (nested.startsWith("{") || nested.startsWith("[")) JsonRepair.parse(nested)?.let { collect(it, into, depth + 1) }
            }
        }
    }

    /** Drops `<think>…</think>` blocks, holding back text that might be the start of a tag. */
    private class ThinkFilter {
        private val pending = StringBuilder()
        private var inThink = false

        fun feed(delta: String): String {
            pending.append(delta)
            val out = StringBuilder()
            while (true) {
                if (inThink) {
                    val end = pending.indexOf(CLOSE)
                    if (end < 0) {
                        // Keep only what could still be the closing tag.
                        val keep = CLOSE.length - 1
                        if (pending.length > keep) pending.delete(0, pending.length - keep)
                        return out.toString()
                    }
                    pending.delete(0, end + CLOSE.length)
                    inThink = false
                } else {
                    val open = pending.indexOf(OPEN)
                    if (open >= 0) {
                        out.append(pending, 0, open)
                        pending.delete(0, open + OPEN.length)
                        inThink = true
                        continue
                    }
                    val hold = partialTagLength()
                    out.append(pending, 0, pending.length - hold)
                    pending.delete(0, pending.length - hold)
                    return out.toString()
                }
            }
        }

        fun flush(): String = if (inThink) "" else pending.toString().also { pending.setLength(0) }

        /** How much of the end of [pending] is a prefix of `<think>`. */
        private fun partialTagLength(): Int {
            for (length in minOf(OPEN.length - 1, pending.length) downTo 1) {
                if (OPEN.startsWith(pending.substring(pending.length - length))) return length
            }
            return 0
        }

        private companion object {
            const val OPEN = "<think>"
            const val CLOSE = "</think>"
        }
    }

    private companion object {
        const val MAX_DEPTH = 8
    }
}
