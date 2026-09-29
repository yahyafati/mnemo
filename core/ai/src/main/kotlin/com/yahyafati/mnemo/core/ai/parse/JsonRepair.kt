package com.yahyafati.mnemo.core.ai.parse

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Parses JSON the way models actually write it. The text is repaired before parsing; valid JSON
 * comes through unchanged except for LaTeX (`"\frac"` is valid JSON for a form feed and `rac`,
 * but a model always means the command). Repairs:
 *
 * - single-quoted strings, unquoted keys, Python's `True`/`False`/`None`;
 * - raw newlines and tabs inside strings, and invalid escapes such as LaTeX's `\(` (kept as a
 *   literal backslash, which is what the model meant);
 * - trailing commas, doubled commas, and missing commas between values;
 * - `//` and `/* */` comments;
 * - with `closeOpen`, an unterminated string and unclosed brackets (a truncated reply).
 */
object JsonRepair {
    private val strictJson = Json

    /** [text] as JSON, repaired if needed; null if it still isn't JSON. */
    fun parse(text: String, closeOpen: Boolean = false): JsonElement? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return strict(repair(trimmed, closeOpen)) ?: strict(trimmed)
    }

    private fun strict(text: String): JsonElement? = try {
        strictJson.parseToJsonElement(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    /** The repaired text. Exposed for tests; callers want [parse]. */
    fun repair(text: String, closeOpen: Boolean = false): String {
        val out = StringBuilder(text.length + 16)
        val stack = ArrayDeque<Char>()
        var quote: Char? = null
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            if (quote != null) {
                i = inString(text, i, quote, out)
                if (i < 0) {
                    // The string closed.
                    quote = null
                    i = -i
                }
                continue
            }
            when {
                c == '"' || c == '\'' -> {
                    if (stack.isNotEmpty() && needsComma(out)) out.append(',')
                    quote = c
                    out.append('"')
                }
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    while (i < n && text[i] != '\n') i++
                    continue
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    val end = text.indexOf("*/", i + 2)
                    i = if (end < 0) n else end + 2
                    continue
                }
                c == '{' || c == '[' -> {
                    if (stack.isNotEmpty() && needsComma(out)) out.append(',')
                    stack.addLast(c)
                    out.append(c)
                }
                c == '}' || c == ']' -> {
                    dropTrailingComma(out)
                    stack.removeLastOrNull()
                    out.append(c)
                }
                c == ',' -> if (lastSignificant(out) !in ",[{") out.append(c)
                c.isLetter() || c == '_' || c == '$' -> {
                    var end = i
                    while (end < n && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '$' || text[end] == '-')) end++
                    val word = text.substring(i, end)
                    // The exponent of a number (`1e5`), not a word.
                    if (out.isNotEmpty() && out.last().isDigit() && (word[0] == 'e' || word[0] == 'E')) {
                        out.append(word)
                        i = end
                        continue
                    }
                    val literal = when (word) {
                        "true", "True" -> "true"
                        "false", "False" -> "false"
                        "null", "None", "undefined" -> "null"
                        else -> null
                    }
                    if (stack.isNotEmpty() && needsComma(out)) out.append(',')
                    out.append(literal ?: "\"$word\"")
                    i = end
                    continue
                }
                else -> {
                    if ((c.isDigit() || c == '-') && stack.isNotEmpty() && lastSignificant(out) in "\"}]") out.append(',')
                    out.append(c)
                }
            }
            i++
        }
        if (closeOpen) {
            if (quote != null) out.append('"')
            while (stack.isNotEmpty()) {
                dropTrailingComma(out)
                // A key with no value yet: give it one.
                if (lastSignificant(out) == ':') out.append("null")
                out.append(if (stack.removeLast() == '{') '}' else ']')
            }
        }
        return out.toString()
    }

    /**
     * Copies one character (or escape) of a string delimited by [quote]. Returns the next index,
     * or its negation when the string closed there.
     */
    private fun inString(text: String, start: Int, quote: Char, out: StringBuilder): Int {
        val c = text[start]
        val n = text.length
        when {
            c == '\\' -> {
                val next = text.getOrNull(start + 1) ?: run {
                    out.append("\\\\")
                    return start + 1
                }
                when {
                    next == '\'' -> {
                        out.append('\'')
                        return start + 2
                    }
                    next == 'u' -> {
                        val hex = text.substring(start + 2, minOf(start + 6, n))
                        if (hex.length == 4 && hex.all { it.isHexDigit() }) {
                            out.append("\\u").append(hex)
                            return start + 6
                        }
                        out.append("\\\\")
                        return start + 1
                    }
                    next in VALID_ESCAPES && !looksLikeLatex(text, start + 1) -> {
                        out.append(c).append(next)
                        return start + 2
                    }
                    else -> {
                        // `\(`, `\alpha`, `\frac`: a literal backslash.
                        out.append("\\\\")
                        return start + 1
                    }
                }
            }
            c == quote -> {
                out.append('"')
                return -(start + 1)
            }
            c == '"' -> out.append("\\\"")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' -> Unit
            else -> out.append(c)
        }
        return start + 1
    }

    /**
     * `\b`, `\f`, `\n`, `\r`, `\t` followed by letters are LaTeX commands in model output
     * (`\beta`, `\frac`, `\nabla`, `\rho`, `\theta`), not control characters.
     */
    private fun looksLikeLatex(text: String, escapeIndex: Int): Boolean {
        if (text[escapeIndex] !in "bfnrt") return false
        var end = escapeIndex
        while (end < text.length && text[end].isLetter()) end++
        return text.substring(escapeIndex, end) in LATEX_COMMANDS
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun lastSignificant(out: StringBuilder): Char {
        for (j in out.indices.reversed()) if (!out[j].isWhitespace()) return out[j]
        return ' '
    }

    /** A value ends here, so the next value needs a comma first. */
    private fun needsComma(out: StringBuilder): Boolean {
        val last = lastSignificant(out)
        return last == '"' || last == '}' || last == ']' || last.isDigit() || endsWithLiteral(out)
    }

    private fun endsWithLiteral(out: StringBuilder): Boolean {
        val trimmed = out.trimEnd()
        return trimmed.endsWith("true") || trimmed.endsWith("false") || trimmed.endsWith("null")
    }

    private fun dropTrailingComma(out: StringBuilder) {
        var j = out.length - 1
        while (j >= 0 && out[j].isWhitespace()) j--
        if (j >= 0 && out[j] == ',') out.deleteCharAt(j)
    }

    private const val VALID_ESCAPES = "\"\\/bfnrt"

    /** LaTeX commands that start with a JSON escape letter. */
    private val LATEX_COMMANDS = setOf(
        "beta", "bar", "bf", "big", "Big", "bigg", "binom", "bmod", "boldsymbol", "bot", "boxed", "bullet", "backslash", "begin",
        "frac", "forall", "flat", "frown",
        "nabla", "neq", "ne", "neg", "nu", "not", "ni", "nless", "ngtr", "nleq", "ngeq", "newline", "nearrow", "nwarrow", "notin", "natural",
        "rho", "rightarrow", "right", "Rightarrow", "rangle", "rceil", "rfloor", "rm", "rvert", "rVert",
        "theta", "tau", "times", "text", "textbf", "textit", "tfrac", "tilde", "top", "to", "triangle", "therefore", "tan", "tanh",
    )
}
