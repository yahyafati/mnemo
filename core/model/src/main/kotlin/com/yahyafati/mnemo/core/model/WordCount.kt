package com.yahyafati.mnemo.core.model

/**
 * How much text there is, for the numbers shown to the user and for the size of a request (Smart Extract's
 * parts and card estimate, ADR 0011). A word is a run of characters between spaces. Japanese and Chinese are
 * written without spaces, so a whole chapter would be one "word": their characters (Han ideographs, kana)
 * count [CJK_CHARS_PER_WORD] to a word instead, which is close to how many tokens they take.
 *
 * Other scripts written without spaces (Thai, Lao, Khmer, Burmese) are not covered: a run of them counts
 * as one word.
 */
object WordCount {
    const val CJK_CHARS_PER_WORD = 2

    fun count(text: String): Int {
        var words = 0
        var cjk = 0
        var inWord = false
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            i += Character.charCount(codePoint)
            when {
                isCjk(codePoint) -> {
                    cjk++
                    inWord = false
                }
                Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) -> inWord = false
                !inWord -> {
                    words++
                    inWord = true
                }
            }
        }
        return words + (cjk + CJK_CHARS_PER_WORD - 1) / CJK_CHARS_PER_WORD
    }

    /** A Han ideograph, hiragana or katakana (Korean's hangul is written with spaces and counts as words). */
    fun isCjk(codePoint: Int): Boolean = when (codePoint) {
        in 0x3040..0x30FF, // hiragana, katakana
        in 0x31F0..0x31FF, // katakana extensions
        in 0x3400..0x4DBF, // Han extension A
        in 0x4E00..0x9FFF, // Han
        in 0xF900..0xFAFF, // Han compatibility
        in 0xFF66..0xFF9F, // half-width katakana
        in 0x20000..0x2FA1F, // Han extensions B and later, compatibility supplement
        -> true
        else -> false
    }
}
