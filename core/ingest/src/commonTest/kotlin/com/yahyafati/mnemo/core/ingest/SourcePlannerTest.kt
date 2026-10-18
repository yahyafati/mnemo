package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.Overlap
import com.yahyafati.mnemo.core.model.SourcePart
import com.yahyafati.mnemo.core.model.WordCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcePlannerTest {
    /** A paragraph of exactly [n] words, each tagged so a test can tell paragraphs apart. */
    private fun para(n: Int, tag: String = "w") = (1..n).joinToString(" ") { "$tag$it" }

    /** A section: its heading line, then paragraphs of the given sizes. */
    private fun section(heading: String, vararg paragraphs: Int) =
        (listOf(heading) + paragraphs.mapIndexed { i, n -> para(n, heading.substringAfterLast(' ').lowercase() + "p$i-") }).joinToString("\n\n")

    private fun doc(vararg sections: String) = sections.joinToString("\n\n")

    private val headingLine = Regex("""^#{1,6} +(.+)$""", RegexOption.MULTILINE)

    private fun headings(part: SourcePart) = headingLine.findAll(part.text).map { it.groupValues[1] }.toList()

    private fun squeeze(text: String) = text.replace(Regex("""\s+"""), "")

    /** Nothing is lost, repeated or moved: the parts in order are the text. */
    private fun assertCovers(text: String, parts: List<SourcePart>) =
        assertEquals(squeeze(text), squeeze(parts.joinToString("") { it.text }))

    // Rule 1
    @Test
    fun sectionsThatFitGoInWhole() {
        val text = doc(section("## S1", 30), section("## S2", 30), section("## S3", 30), section("## S4", 30), section("## S5", 30))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("S1", "S2", "S3"), listOf("S4", "S5")), parts.map(::headings))
        assertEquals(doc(section("## S1", 30), section("## S2", 30), section("## S3", 30)), parts[0].text)
        assertTrue(parts.all { it.path.isEmpty() && !it.continued })
    }

    @Test
    fun aTextThatFitsIsOnePartAsWritten() {
        val text = "# Title\n\nSome text.\n\n\n\n## Sub\n\nMore text."
        assertEquals(listOf(SourcePart(text)), SourcePlanner.plan(text))
    }

    @Test
    fun aBlankTextHasNoParts() {
        assertEquals(emptyList(), SourcePlanner.plan(" \n\n "))
    }

    // Rule 2
    @Test
    fun aSectionThatOnlyFitsAnEmptyPartStartsTheNext() {
        val text = doc(section("## S1", 30), section("## S2", 80))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("S1"), listOf("S2")), parts.map(::headings))
    }

    // Rule 3
    @Test
    fun aSectionTooBigIsOpenedUpWithItsTitleOnThePath() {
        val text = doc(section("# Chapter", 40), section("## One", 40), section("## Two", 40))
        val parts = SourcePlanner.plan(text, listOf("Book"), maxWords = 100)
        assertEquals(listOf(listOf("Chapter", "One"), listOf("Two")), parts.map(::headings))
        assertEquals(listOf(listOf("Book", "Chapter"), listOf("Book", "Chapter")), parts.map { it.path })
    }

    // Rule 4
    @Test
    fun aBodyTooLongIsCutIntoContinuedParts() {
        val long = section("## Long", 40, 40, 40, 40, 40)
        val text = doc(section("## Short", 20), long)
        val parts = SourcePlanner.plan(text, listOf("Notes"), maxWords = 100)
        val pieces = TextChunker.chunk(long, maxWords = 100)
        assertEquals(3, pieces.size)
        assertEquals(listOf(section("## Short", 20)) + pieces, parts.map { it.text })
        assertEquals(listOf(false, false, true, true), parts.map { it.continued })
        assertEquals(listOf(listOf("Notes"), listOf("Notes", "Long"), listOf("Notes", "Long"), listOf("Notes", "Long")), parts.map { it.path })
    }

    @Test
    fun aTextWithoutHeadingsIsCutAsTheChunkerCutsIt() {
        val text = (1..12).joinToString("\n\n") { para(150, "p$it-") }
        val parts = SourcePlanner.plan(text)
        assertEquals(TextChunker.chunk(text), parts.map { it.text })
        assertEquals(listOf(false) + List(parts.size - 1) { true }, parts.map { it.continued })
        assertTrue(parts.all { it.path.isEmpty() })
        assertEquals(listOf("Article"), SourcePlanner.plan(text, listOf("Article")).first().path)
    }

    // Rule 5
    @Test
    fun aHalfFullPartDoesNotTakeACousin() {
        val text = doc("# A", section("## A1", 60), section("## A2", 55), "# B", section("## B1", 20), section("## B2", 90))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        // A2 and B1 would fit together, but A2 already fills half a part and B1 belongs to another chapter.
        assertEquals(listOf(listOf("A", "A1"), listOf("A2"), listOf("B", "B1"), listOf("B2")), parts.map(::headings))
        assertEquals(listOf(listOf("A"), listOf("A"), listOf("B"), listOf("B")), parts.map { it.path })
    }

    @Test
    fun aPartLessThanHalfFullTakesACousin() {
        val text = doc("# A", section("## A1", 60), section("## A2", 40), "# B", section("## B1", 20), section("## B2", 90))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("A", "A1"), listOf("A2", "B", "B1"), listOf("B2")), parts.map(::headings))
        // What the middle part holds has only the root in common.
        assertEquals(emptyList(), parts[1].path)
    }

    @Test
    fun siblingsStayTogetherWhateverTheFill() {
        val text = doc("# A", section("## A1", 55), section("## A2", 20), section("## A3", 30))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("A", "A1", "A2"), listOf("A3")), parts.map(::headings))
    }

    // Rule 6
    @Test
    fun aTinyBodyStaysWithItsFirstSubsection() {
        val text = doc("# Chapter\n\nOne sentence.", section("## One", 95), section("## Two", 50))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("Chapter", "One"), listOf("Two")), parts.map(::headings))
        assertTrue(parts[0].text.startsWith("# Chapter\n\nOne sentence.\n\n## One"))
    }

    @Test
    fun aLongerBodyIsAPartOfItsOwnWhenTheSubsectionDoesNotFit() {
        val text = doc(section("# Chapter", 30), section("## One", 90), section("## Two", 50))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("Chapter"), listOf("One"), listOf("Two")), parts.map(::headings))
    }

    @Test
    fun aShortTailJoinsThePartBefore() {
        val text = doc(section("## S1", 95), section("## S2", 10))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("S1", "S2")), parts.map(::headings))
    }

    @Test
    fun aShortLastChapterIsNotAddedToTheChapterBefore() {
        val text = doc("# A", section("## A1", 60), section("## A2", 55), "# B", section("## B1", 15))
        val parts = SourcePlanner.plan(text, maxWords = 100)
        assertEquals(listOf(listOf("A", "A1"), listOf("A2"), listOf("B", "B1")), parts.map(::headings))
        // B is in whole, so its heading is in the text and not on the path.
        assertEquals(emptyList(), parts.last().path)
    }

    /** The table in ADR 0015, "2. Heading-aware parts with a path". */
    @Test
    fun theGameTheoryExample() {
        val text = doc(
            "# 1. Introduction\n\nGames are everywhere.",
            section("## 1.1 Players", 100, 100),
            section("## 1.2 Strategies", 100, 100),
            section("## 1.3 Payoffs", 100, 100),
            section("## 1.4 Information", 100, 100),
            section("## 1.5 Nash Equilibrium", *IntArray(20) { 100 }),
            section("## 1.6 Repeated Games", 100, 100, 100, 100, 100, 100, 50),
            "# 2. Mixed Strategies\n\nRandomness helps.",
            section("## 2.1 Mixing", 250, 250),
            section("## 2.2 Indifference", 250, 250),
            section("## 2.3 Existence", 250, 250),
        )
        val parts = SourcePlanner.plan(text, listOf("Game Theory"))

        val intro = listOf("Game Theory", "1. Introduction")
        val nash = intro + "1.5 Nash Equilibrium"
        val mixed = listOf("Game Theory", "2. Mixed Strategies")
        assertEquals(
            listOf(
                SourcePartShape(listOf("1. Introduction", "1.1 Players", "1.2 Strategies", "1.3 Payoffs", "1.4 Information"), intro, false),
                SourcePartShape(listOf("1.5 Nash Equilibrium"), nash, false),
                SourcePartShape(emptyList(), nash, true),
                SourcePartShape(listOf("1.6 Repeated Games"), intro, false),
                SourcePartShape(listOf("2. Mixed Strategies", "2.1 Mixing", "2.2 Indifference"), mixed, false),
                SourcePartShape(listOf("2.3 Existence"), mixed, false),
            ),
            parts.map { SourcePartShape(headings(it), it.path, it.continued) },
        )
        assertTrue(parts.all { WordCount.count(it.text) <= TextChunker.DEFAULT_MAX_WORDS })
        assertCovers(text, parts)
    }

    private data class SourcePartShape(val headings: List<String>, val path: List<String>, val continued: Boolean)

    @Test
    fun aFirstHeadingThatRepeatsTheRootIsNotRepeated() {
        val text = doc("# Chapter 3", section("## Cells", 80), section("## Tissues", 80))
        val parts = SourcePlanner.plan(text, listOf("Biology", "Chapter 3"), maxWords = 100)
        assertEquals(listOf(listOf("Biology", "Chapter 3"), listOf("Biology", "Chapter 3")), parts.map { it.path })
        // A page whose title is its h1, whatever the case.
        assertEquals(listOf("chapter 3"), SourcePlanner.plan(text, listOf("chapter 3"), maxWords = 100).first().path)
    }

    @Test
    fun titlesAreCutAndLongPathsKeepTheirEnds() {
        val long = "A very long heading " + "that goes on ".repeat(10)
        val headings = listOf("# H1", "## H2", "### H3", "#### H4", "##### H5", "###### $long")
        val text = doc(*headings.toTypedArray(), (1..3).joinToString("\n\n") { para(150, "p$it-") })
        val parts = SourcePlanner.plan(text, listOf("Book", "Chapter"), maxWords = 200)
        val deepest = parts.last().path
        assertEquals(SourcePlanner.MAX_PATH_LEVELS, deepest.size)
        assertEquals(listOf("Book", "…", "H3", "H4", "H5"), deepest.dropLast(1))
        assertTrue(deepest.last().length <= SourcePlanner.MAX_TITLE_CHARS)
        assertTrue(deepest.last().endsWith("…"))
        assertTrue(parts.last().continued)
    }

    @Test
    fun sizesCountJapaneseByCharacter() {
        // 1,000 kana are 500 words: two sections fit in a part, three don't. Counted between spaces they would be one word each.
        val text = doc("## 一\n\n" + "あ".repeat(1000), "## 二\n\n" + "い".repeat(1000), "## 三\n\n" + "う".repeat(1000))
        val parts = SourcePlanner.plan(text)
        assertEquals(listOf(listOf("一", "二"), listOf("三")), parts.map(::headings))
    }

    @Test
    fun fencesAndTablesUnderHeadingsStayWhole() {
        val fence = "```kotlin\n# a comment, not a heading\n\n" + (1..20).joinToString("\n") { "val x$it = $it" } + "\n```"
        val table = "| Term | Meaning |\n|---|---|\n" + (1..10).joinToString("\n") { "| t$it | m$it |" }
        val text = doc(section("## Code", 30), fence, section("## Table", 30), table, section("## After", 30))
        val parts = SourcePlanner.plan(text, maxWords = 120)
        // The line in the fence is no heading, so the code is one section with its fence, and the table one with its table.
        assertEquals(listOf(doc(section("## Code", 30), fence), doc(section("## Table", 30), table, section("## After", 30))), parts.map { it.text })
    }

    @Test
    fun theSameTextGivesTheSameParts() {
        val text = doc("Lead.", "# A", section("## A1", 300, 300), section("## A2", 900), "# B", section("## B1", 1500), section("## B2", 50))
        val first = SourcePlanner.plan(text, listOf("Root"))
        assertEquals(first, SourcePlanner.plan(text, listOf("Root")))
        assertCovers(text, first)
    }

    // Overlap (ADR 0015, C3)
    private val continuedText = doc(section("## Short", 20), section("## Long", 40, 40, 40, 40, 40))

    @Test
    fun withinSectionsOnlyAContinuedPartGetsTheEndOfThePartBefore() {
        val parts = SourcePlanner.plan(continuedText, listOf("Notes"), maxWords = 100)
        assertEquals(listOf(false, false, true, true), parts.map { it.continued })
        assertEquals(listOf("", "", SourceOverlap.tail(parts[1].text), SourceOverlap.tail(parts[2].text)), parts.map { it.overlap })
        assertTrue(parts[2].text.isNotBlank() && parts[2].overlap.isNotBlank())
        assertTrue(parts[1].text.endsWith(parts[2].overlap))
    }

    @Test
    fun withinSectionsIsTheDefault() {
        assertEquals(SourcePlanner.plan(continuedText, maxWords = 100, overlap = Overlap.WithinSections), SourcePlanner.plan(continuedText, maxWords = 100))
    }

    @Test
    fun alwaysGivesEveryPartAfterTheFirstOneAcrossHeadings() {
        val parts = SourcePlanner.plan(continuedText, maxWords = 100, overlap = Overlap.Always)
        assertEquals("", parts.first().overlap)
        for (index in 1 until parts.size) assertEquals(SourceOverlap.tail(parts[index - 1].text), parts[index].overlap)
        // The second part starts a section of its own (it isn't continued) and still has one.
        assertFalse(parts[1].continued)
        assertTrue(parts[1].overlap.isNotEmpty())
    }

    @Test
    fun offGivesNone() {
        val parts = SourcePlanner.plan(continuedText, maxWords = 100, overlap = Overlap.Off)
        assertTrue(parts.all { it.overlap.isEmpty() })
    }

    @Test
    fun nothingComesBeforeThePartOne() {
        for (mode in Overlap.entries) assertEquals("", SourcePlanner.plan(continuedText, maxWords = 100, overlap = mode).first().overlap)
    }

    @Test
    fun theSettingNeverChangesWhereTheTextIsCut() {
        val text = doc("Lead.", "# A", section("## A1", 300, 300), section("## A2", 900), "# B", section("## B1", 1500), section("## B2", 50))
        val shapes = Overlap.entries.map { mode -> SourcePlanner.plan(text, listOf("Root"), overlap = mode).map { Triple(it.text, it.path, it.continued) } }
        assertEquals(1, shapes.distinct().size)
    }

    @Test
    fun aTextWithoutHeadingsGivesEveryPartAfterTheFirstItsOverlapInEveryMode() {
        val text = (1..12).joinToString("\n\n") { para(150, "p$it-") }
        val within = SourcePlanner.plan(text)
        assertEquals(within, SourcePlanner.plan(text, overlap = Overlap.Always))
        assertEquals("", within.first().overlap)
        assertTrue(within.drop(1).all { it.overlap.isNotEmpty() })
    }

    @Test
    fun anOverlapNeverHoldsMoreThanTheLimitAndIsNotCountedInThePart() {
        val text = doc("# A", section("## A1", 300, 300), section("## A2", 900))
        val parts = SourcePlanner.plan(text, overlap = Overlap.Always)
        assertTrue(parts.all { WordCount.count(it.overlap) <= SourceOverlap.OVERLAP_WORDS })
        assertTrue(parts.all { WordCount.count(it.text) <= TextChunker.DEFAULT_MAX_WORDS + TextChunker.DEFAULT_MAX_WORDS / 4 })
        // The text itself holds no overlap: the parts in order are still the whole text.
        assertCovers(text, parts)
    }
}
