package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageLabelsTest {
    private fun summary(vararg labels: String, maxRuns: Int = 4) = PageLabels.summary(labels.toList(), maxRuns)

    @Test
    fun aFrontMatterThenABodyAreTwoRuns() {
        val labels = (1..12).map { roman(it) } + (1..600).map { it.toString() }
        assertEquals("i–xii, 1–600", PageLabels.summary(labels))
    }

    @Test
    fun aSingleLabelOrPageIsWrittenOnce() {
        assertEquals("cover, i–iii, 1", summary("cover", "i", "ii", "iii", "1"))
        assertEquals("7", summary("7"))
    }

    @Test
    fun labelsThatDoNotCountOnStartNewRuns() {
        assertEquals("1–3, 7–8", summary("1", "2", "3", "7", "8"))
        assertEquals("i–ii, I–II", summary("i", "ii", "I", "II"))
        assertEquals("A-1–A-3, B-1", summary("A-1", "A-2", "A-3", "B-1"))
    }

    @Test
    fun romanNumeralsMustBeWellFormedToCount() {
        assertEquals("iv–vi", summary("iv", "v", "vi"))
        assertEquals("iii, iiii", summary("iii", "iiii")) // "iiii" is not a numeral
        assertEquals("a, b, c", summary("a", "b", "c"))
    }

    @Test
    fun pagesWithoutALabelAreLeftOutAndBreakARun() {
        assertEquals("1–2, 3", summary("1", "2", "", "3"))
        assertNull(summary("", ""))
        assertNull(PageLabels.summary(emptyList()))
    }

    @Test
    fun aLongListIsCutAfterTheLimit() {
        assertEquals("a, b, …", summary("a", "b", "c", "d", maxRuns = 2))
    }

    @Test
    fun runsKnowTheirPages() {
        val runs = PageLabels.runs(listOf("i", "ii", "1", "2", "3"))
        assertEquals(listOf(PageLabels.Run(1, 2, "i", "ii"), PageLabels.Run(3, 5, "1", "3")), runs)
    }

    private fun roman(n: Int) = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii")[n - 1]
}
