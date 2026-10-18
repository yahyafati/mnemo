package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.WordCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryInputTest {
    private fun words(n: Int, tag: String) = (1..n).joinToString(" ") { "$tag$it" }

    /** A paragraph of [n] words, the first one [tag]. */
    private fun paragraph(n: Int, tag: String) = "$tag " + words(n - 1, "w")

    @Test
    fun aShortTextIsSentWhole() {
        val text = "# Title\n\n" + words(500, "a")
        val input = SummaryInput.of(text)
        assertEquals(text, input.text)
        assertFalse(input.skimmed)
    }

    @Test
    fun aTextExactlyAtTheBudgetIsStillWhole() {
        val text = words(SummaryInput.BUDGET_WORDS, "a")
        assertFalse(SummaryInput.of(text).skimmed)
        assertTrue(SummaryInput.of(text + " more").skimmed)
    }

    @Test
    fun aLongTextWithHeadingsIsSkimmedAsItsOutlineAndTheOpeningOfEachSection() {
        val sections = (1..30).joinToString("\n\n") { n ->
            "## Section $n\n\n${paragraph(150, "Opening$n")}\n\n${paragraph(150, "Later$n")}"
        }
        val text = "Lead paragraph of the whole text. ${words(200, "lead")}\n\n$sections"
        assertTrue(WordCount.count(text) > SummaryInput.BUDGET_WORDS)

        val input = SummaryInput.of(text)
        assertTrue(input.skimmed)
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
        // Every heading, in order, and each section's first paragraph, not its later ones.
        (1..30).forEach { n ->
            assertTrue("## Section $n\n" in input.text, "heading $n")
            assertTrue("Opening$n " in input.text, "opening $n")
            assertFalse("Later$n " in input.text, "later paragraph $n")
        }
        val positions = (1..30).map { input.text.indexOf("## Section $it\n") }
        assertEquals(positions.sorted(), positions)
        assertTrue(input.text.startsWith("Lead paragraph of the whole text."))
    }

    @Test
    fun theLeadAndTheLastSectionGetMoreThanTheOthers() {
        val sections = (1..20).joinToString("\n\n") { n -> "## Section $n\n\n${words(400, "s$n")}" }
        val text = "${words(400, "lead")}\n\n$sections"
        val input = SummaryInput.of(text)

        fun shown(tag: String) = Regex("""\b$tag\d+\b""").findAll(input.text).count()
        assertTrue(shown("lead") > shown("s5"), "lead ${shown("lead")} vs ${shown("s5")}")
        assertTrue(shown("s20") > shown("s5"), "last ${shown("s20")} vs ${shown("s5")}")
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
    }

    @Test
    fun aSectionThatIsCutSaysSo() {
        val text = (1..20).joinToString("\n\n") { n -> "# Chapter $n\n\n${paragraph(300, "c$n")}\n\n${paragraph(300, "d$n")}" }
        assertTrue("[…]" in SummaryInput.of(text).text)
    }

    @Test
    fun manyHeadingsAreThinnedOutSoTheSkimStaysInTheBudget() {
        val text = (1..2_000).joinToString("\n\n") { n -> "## Heading number $n\n\nSome words in section $n of the text, a few more words." }
        val input = SummaryInput.of(text)
        assertTrue(input.skimmed)
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
        assertTrue("## Heading number 1\n" in input.text)
        assertTrue("## Heading number 2000\n" in input.text)
    }

    @Test
    fun aTextWithoutHeadingsKeepsItsStartItsEndAndPiecesBetween() {
        val body = (1..100).joinToString("\n\n") { n -> paragraph(100, "p$n") }
        val input = SummaryInput.of(body)

        assertTrue(input.skimmed)
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
        assertTrue(input.text.startsWith("p1 "), input.text.take(40))
        assertTrue(input.text.trimEnd().endsWith(" w99"), input.text.takeLast(40))
        // The head is about 1,500 words, the tail about 500, and some pieces are sampled between.
        val head = input.text.substringBefore("[…]")
        assertTrue((1..14).all { "p$it " in head }, "head")
        assertTrue(WordCount.count(head) in 1_300..SummaryInput.HEAD_WORDS, "head words: ${WordCount.count(head)}")
        assertTrue("p100 " in input.text && "p96 " in input.text, "tail")
        val sampled = (17..94).count { "p$it " in input.text }
        assertTrue(sampled in 1..12, "sampled $sampled")
        assertTrue("[…]" in input.text, "gap")
    }

    @Test
    fun aTextWithOneHugeParagraphIsStillSampled() {
        val text = (1..6_000).joinToString(" ") { "word$it" }.replace(Regex("""(word\d+) """), "$1. ")
        val input = SummaryInput.of(text)

        assertTrue(input.skimmed)
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
        assertTrue(input.text.startsWith("word1."))
        assertTrue("word6000" in input.text)
    }

    @Test
    fun fewHeadingsAreNotAnOutline() {
        val text = "# One\n\n${words(2_000, "a")}\n\n# Two\n\n${words(2_000, "b")}"
        val input = SummaryInput.of(text)
        assertTrue(input.skimmed)
        // Skimmed as plain text: the end of the text is there.
        assertTrue("b2000" in input.text)
    }

    @Test
    fun japaneseCountsByCharactersAndIsSkimmedToo() {
        val text = (1..4_000).joinToString("") { "これは文です。" }
        val input = SummaryInput.of(text)
        assertTrue(input.skimmed)
        assertTrue(WordCount.count(input.text) <= SummaryInput.BUDGET_WORDS, "words: ${WordCount.count(input.text)}")
    }

    @Test
    fun theSameTextAlwaysGivesTheSameSkim() {
        val text = (1..60).joinToString("\n\n") { n -> "## S$n\n\n${paragraph(120, "o$n")}" }
        assertEquals(SummaryInput.of(text), SummaryInput.of(text))
    }
}
