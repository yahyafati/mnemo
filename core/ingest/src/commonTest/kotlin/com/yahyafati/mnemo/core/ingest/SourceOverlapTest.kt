package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceOverlapTest {
    private fun words(n: Int, tag: String) = (1..n).joinToString(" ") { "$tag$it" }

    private fun sentence(n: Int, tag: String) = words(n, tag) + "."

    @Test
    fun aShortPartIsItsOwnOverlap() {
        val text = "First.\n\nSecond paragraph here."
        assertEquals(text, SourceOverlap.tail(text))
    }

    @Test
    fun blankTextHasNoOverlap() {
        assertEquals("", SourceOverlap.tail(" \n\n "))
    }

    @Test
    fun theLastParagraphsThatFitAreTaken() {
        val paragraphs = listOf(words(30, "a"), words(30, "b"), words(30, "c"), words(30, "d"))
        val tail = SourceOverlap.tail(paragraphs.joinToString("\n\n"))
        assertEquals(paragraphs.drop(1).joinToString("\n\n"), tail)
        assertEquals(90, WordCount.count(tail))
    }

    @Test
    fun aParagraphThatWouldGoOverTheLimitIsLeftWhole() {
        val paragraphs = listOf(words(60, "a"), words(60, "b"))
        assertEquals(paragraphs.last(), SourceOverlap.tail(paragraphs.joinToString("\n\n")))
    }

    @Test
    fun aParagraphExactlyAtTheLimitIsKeptWhole() {
        val last = words(SourceOverlap.OVERLAP_WORDS, "b")
        assertEquals(last, SourceOverlap.tail(words(10, "a") + "\n\n" + last))
    }

    @Test
    fun aLongParagraphIsCutAtASentence() {
        val sentences = (1..5).map { sentence(30, "s$it-") }
        val tail = SourceOverlap.tail(words(10, "a") + "\n\n" + sentences.joinToString(" "))
        assertEquals(sentences.drop(2).joinToString(" "), tail)
        assertEquals(90, WordCount.count(tail))
    }

    @Test
    fun aSentenceLongerThanTheLimitIsCutAtAWord() {
        val long = sentence(300, "w")
        val tail = SourceOverlap.tail(long)
        assertTrue(tail.startsWith("…w"), tail)
        assertTrue(long.endsWith(tail.removePrefix("…")), tail)
        assertEquals(SourceOverlap.OVERLAP_WORDS, WordCount.count(tail.removePrefix("…")))
    }

    @Test
    fun japaneseIsCountedByCharacterAndCutAtASentence() {
        // 38 kana and the full stop count 19 + 1 words a sentence; the limit is 100 words.
        val sentences = (1..8).map { "あ".repeat(38) + "。" }
        val tail = SourceOverlap.tail(sentences.joinToString(""))
        assertEquals(sentences.takeLast(5).joinToString(""), tail)
        assertEquals(100, WordCount.count(tail))
    }

    @Test
    fun aLimitOfYourOwn() {
        val tail = SourceOverlap.tail(words(5, "a") + "\n\n" + words(5, "b"), maxWords = 5)
        assertEquals(words(5, "b"), tail)
        assertFalse("a1" in tail)
    }
}
