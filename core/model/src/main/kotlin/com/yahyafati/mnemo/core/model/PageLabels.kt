package com.yahyafati.mnemo.core.model

/**
 * The printed page numbers of a PDF (`i`, `ii`, … `xii`, `1`, `2`, …), as [PdfInfo.labels] has them, written the way
 * the page count line shows them: `i–xii, 1–600` (docs/pdf/ROADMAP.md, P2).
 */
object PageLabels {
    /** [first] to [last] are the labels of pages [firstPage] to [lastPage] (1-based), each continuing the one before. */
    data class Run(val firstPage: Int, val lastPage: Int, val first: String, val last: String)

    /**
     * The runs of pages whose labels count on (`1, 2, 3`, `i, ii`, `A-1, A-2`); a label that doesn't continue the one
     * before starts a new run. Pages with no label are left out.
     */
    fun runs(labels: List<String>): List<Run> {
        val runs = mutableListOf<Run>()
        var previous: Counted? = null
        labels.forEachIndexed { index, label ->
            if (label.isBlank()) {
                previous = null
                return@forEachIndexed
            }
            val page = index + 1
            val counted = Counted.of(label)
            val last = runs.lastOrNull()
            if (last != null && counted != null && previous?.continuedBy(counted) == true) {
                runs[runs.lastIndex] = last.copy(lastPage = page, last = label)
            } else {
                runs += Run(page, page, label, label)
            }
            previous = counted
        }
        return runs
    }

    /**
     * `i–xii, 1–600`: the runs of [labels] in order, at most [maxRuns] of them (then `…`), or null when there are none
     * to show.
     */
    fun summary(labels: List<String>, maxRuns: Int = 4): String? {
        val runs = runs(labels)
        if (runs.isEmpty()) return null
        val shown = runs.take(maxRuns).joinToString(", ") { if (it.first == it.last) it.first else "${it.first}–${it.last}" }
        return if (runs.size > maxRuns) "$shown, …" else shown
    }

    /** What a label counts: a number after some prefix (`12`, `A-3`), or a Roman numeral in one case. */
    private class Counted private constructor(private val prefix: String, private val value: Int, private val roman: Boolean) {
        fun continuedBy(next: Counted) = next.prefix == prefix && next.roman == roman && next.value == value + 1

        companion object {
            fun of(label: String): Counted? {
                val digits = label.takeLastWhile { it in '0'..'9' }
                if (digits.isNotEmpty()) {
                    val number = digits.toIntOrNull() ?: return null
                    return Counted(label.dropLast(digits.length), number, roman = false)
                }
                val value = romanValue(label) ?: return null
                // Case is the prefix, so `i, II` doesn't count on.
                return Counted(if (label[0].isUpperCase()) "U" else "l", value, roman = true)
            }

            private val numerals = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)

            /** The value of a well-formed Roman numeral, or null for anything else (`iiii` is not 4). */
            private fun romanValue(label: String): Int? {
                if (label.any { it.lowercaseChar() !in numerals } || label.any { it.isUpperCase() } && label.any { it.isLowerCase() }) return null
                val digits = label.map { numerals.getValue(it.lowercaseChar()) }
                var total = 0
                for (i in digits.indices) {
                    total += if (i + 1 < digits.size && digits[i] < digits[i + 1]) -digits[i] else digits[i]
                }
                return total.takeIf { it > 0 && toRoman(it) == label.lowercase() }
            }

            private fun toRoman(number: Int): String {
                val steps = listOf(1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc", 50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i")
                var left = number
                return buildString {
                    for ((value, text) in steps) while (left >= value) {
                        append(text)
                        left -= value
                    }
                }
            }
        }
    }
}
