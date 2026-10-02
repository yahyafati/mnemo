package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount
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

    private fun kanji(n: Int) = "漢".repeat(n)

    @Test
    fun aChapterWithoutSpacesIsMeasuredByItsCharacters() {
        // 1,000 characters are 500 words, and no sentence mark: broken between characters, none lost.
        val text = kanji(2_600)
        val chunks = TextChunker.chunk(text, maxWords = 500)
        assertTrue(chunks.size >= 2, "${chunks.size} parts")
        assertTrue(chunks.all { WordCount.count(it) <= 625 }, "a part grew past the limit and its tail allowance")
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun japaneseSentencesBreakAtTheirMarksAndAreRejoinedWithoutSpaces() {
        val sentences = (1..10).map { "${kanji(100)}。" }
        val chunks = TextChunker.chunk(sentences.joinToString(""), maxWords = 120) // 240 characters: two sentences a part
        assertEquals(5, chunks.size)
        assertTrue(chunks.all { it.endsWith("。") && !it.contains(' ') })
        assertEquals(sentences.joinToString(""), chunks.joinToString(""))
    }

    @Test
    fun aClosingQuoteStaysWithItsSentence() {
        val text = (1..6).joinToString("") { "「${kanji(100)}。」" }
        val chunks = TextChunker.chunk(text, maxWords = 110)
        assertTrue(chunks.all { it.startsWith("「") && it.endsWith("。」") }, chunks.joinToString(" | ") { it.take(2) + "…" + it.takeLast(2) })
    }

    @Test
    fun aJapaneseChapterIsMoreThanOneRequestWhenItIsLong() {
        // Before characters were counted this was a single "word", and so a single request.
        assertTrue(TextChunker.chunk("本".repeat(10_000)).size >= 4)
    }

    @Test
    fun cleanup() {
        assertEquals("a b\n\nc", TextCleanup.normalize("  a \t b  \r\n\n\n\n c\u0000 "))
        assertEquals(
            "Photosynthesis turns light into sugar.\nChlorophyll absorbs it.",
            TextCleanup.joinWrappedLines("Photo-\nsynthesis turns light\ninto sugar.\nChlorophyll absorbs it."),
        )
    }

    // ---- Markdown (W1) ----

    @Test
    fun markdownCleanupKeepsIndentationAndFences() {
        val text = "# T\r\n\r\n\r\n\r\n- a  b \n    - nested   x\n\n```kotlin\n  fun  x()  {\n\n\n\n  }\n```\n\n\n\nend  "
        assertEquals(
            "# T\n\n- a b\n    - nested x\n\n```kotlin\n  fun  x()  {\n\n\n\n  }\n```\n\nend",
            TextCleanup.normalizeMarkdown(text),
        )
    }

    @Test
    fun aLongerFenceIsClosedByAtLeastItsLength() {
        val text = "````\nbody\n```\n\n\n\nstill code\n````\n\n\n\nafter"
        assertEquals("````\nbody\n```\n\n\n\nstill code\n````\n\nafter", TextCleanup.normalizeMarkdown(text))
    }

    @Test
    fun markdownCleanupDropsControlCharactersAndLeadingBlankLines() {
        assertEquals("a\n\nb", TextCleanup.normalizeMarkdown("\n\na\u0000 \n \n\n\nb"))
    }

    private fun fence(lines: Int, language: String = "kotlin") =
        "```$language\n" + (1..lines).joinToString("\n") { if (it % 7 == 0) "" else "    val line$it = $it" } + "\n```"

    private fun openFences(part: String) = part.lines().count { it.startsWith("```") }

    @Test
    fun aFencedBlockWithBlankLinesIsNotCut() {
        val code = fence(14)
        val text = words(40, "a") + "\n\n" + code + "\n\n" + words(40, "b")
        val chunks = TextChunker.chunk(text, maxWords = 100)
        assertTrue(chunks.any { code in it }, chunks.joinToString("\n---\n"))
        assertTrue(chunks.all { openFences(it) % 2 == 0 })
    }

    @Test
    fun aFenceLongerThanAPartIsSplitIntoWholeFences() {
        val code = fence(120)
        val chunks = TextChunker.chunk(code, maxWords = 60)
        assertTrue(chunks.size >= 3, "${chunks.size} parts")
        for (chunk in chunks) {
            assertTrue(chunk.startsWith("```kotlin\n") && chunk.endsWith("\n```"), chunk)
            assertTrue(wordCount(chunk) <= 60 + 15, "${wordCount(chunk)} words")
        }
        // Every line survives once, in order.
        val lines = chunks.flatMap { it.lines().drop(1).dropLast(1) }
        assertEquals(code.lines().drop(1).dropLast(1), lines)
    }

    private fun table(rows: Int) =
        "| Name | Value |\n| --- | --- |\n" + (1..rows).joinToString("\n") { "| row$it | value$it |" }

    @Test
    fun aTableIsNotCutAndALongOneRepeatsItsHeader() {
        val small = table(5)
        val kept = TextChunker.chunk(words(40, "a") + "\n\n" + small + "\n\n" + words(40, "b"), maxWords = 70)
        assertTrue(kept.any { small in it })

        val chunks = TextChunker.chunk(table(100), maxWords = 60)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.startsWith("| Name | Value |\n| --- | --- |\n| row") })
        assertEquals((1..100).map { "| row$it | value$it |" }, chunks.flatMap { it.lines().drop(2) })
    }

    @Test
    fun aPartPrefersToStartAtAHeading() {
        val text = listOf(
            "# Intro", words(40, "i"),
            "## Alpha", words(40, "a"),
            "## Beta", words(40, "b"),
            "## Gamma", words(40, "g"),
        ).joinToString("\n\n")
        val chunks = TextChunker.chunk(text, maxWords = 100)
        assertTrue(chunks.size >= 2)
        for (chunk in chunks.drop(1)) assertTrue(chunk.startsWith("#"), chunk.take(40))
        assertTrue(chunks.none { it.trimEnd().lines().last().startsWith("#") })
    }

    @Test
    fun aHeadingIsNeverLeftAloneAtTheEndOfAPart() {
        val text = words(95, "a") + "\n\n## Next\n\n" + words(60, "b")
        val chunks = TextChunker.chunk(text, maxWords = 100)
        assertEquals(2, chunks.size)
        assertTrue(chunks[1].startsWith("## Next"))
    }

    @Test
    fun plainTextWithHashesInItIsStillSplitLikeBefore() {
        val paragraphs = (1..10).map { "#tag " + words(30, "p$it-") }
        val chunks = TextChunker.chunk(paragraphs.joinToString("\n\n"), maxWords = 100)
        assertEquals(paragraphs, chunks.flatMap { it.split("\n\n") })
    }
}
