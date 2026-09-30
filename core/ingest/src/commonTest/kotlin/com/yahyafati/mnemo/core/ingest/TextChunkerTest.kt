package com.yahyafati.mnemo.core.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {
    private fun words(n: Int, prefix: String = "w") = (1..n).joinToString(" ") { "$prefix$it" }

    private fun wordCount(text: String) = text.split(Regex("\\s+")).count { it.isNotEmpty() }

    @Test
    fun shortTextIsOnePart() {
        val text = "First paragraph.\n\nSecond paragraph."
        assertEquals(listOf(text), TextChunker.chunk(text))
    }

    @Test
    fun partsBreakBetweenParagraphsAndStayUnderTheLimit() {
        val paragraphs = (1..10).map { words(30, "p$it-") }
        val chunks = TextChunker.chunk(paragraphs.joinToString("\n\n"), maxWords = 100)
        assertTrue(chunks.all { wordCount(it) <= 100 })
        // Every paragraph survives whole, in order.
        assertEquals(paragraphs, chunks.flatMap { it.split("\n\n") })
    }

    @Test
    fun longParagraphsBreakBetweenSentences() {
        val sentences = (1..12).map { "${words(9, "s$it-")}." }
        val chunks = TextChunker.chunk(sentences.joinToString(" "), maxWords = 40)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.trim().endsWith(".") && wordCount(it) <= 40 })
    }

    @Test
    fun endlessSentencesBreakBetweenWords() {
        val chunks = TextChunker.chunk(words(220), maxWords = 100)
        assertEquals(listOf(100, 120), chunks.map(::wordCount)) // the 20-word tail joins the part before
    }

    @Test
    fun aLargeTailStaysSeparate() {
        assertEquals(listOf(100, 100, 40), TextChunker.chunk(words(240), maxWords = 100).map(::wordCount))
    }

    @Test
    fun cleanup() {
        assertEquals("a b\n\nc", TextCleanup.normalize("  a \t b  \r\n\n\n\n c\u0000 "))
        assertEquals(
            "Photosynthesis turns light into sugar.\nChlorophyll absorbs it.",
            TextCleanup.joinWrappedLines("Photo-\nsynthesis turns light\ninto sugar.\nChlorophyll absorbs it."),
        )
    }
}
