package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PageRangesTest {
    private fun parse(text: String, pageCount: Int = 600): PageRangesResult = PageRanges.parse(text, pageCount)

    private fun pages(text: String, pageCount: Int = 600): PageRanges = assertIs<PageRangesResult.Valid>(parse(text, pageCount)).ranges

    private fun error(text: String, pageCount: Int = 600): PageRangeError = assertIs<PageRangesResult.Invalid>(parse(text, pageCount)).error

    @Test
    fun partsAreSinglePagesAndRanges() {
        val ranges = pages("1-10, 14, 20-25")
        assertEquals((1..10).toList() + 14 + (20..25).toList(), ranges.pages)
        assertEquals(17, ranges.count)
        assertEquals("1-10, 14, 20-25", ranges.format())
    }

    @Test
    fun spacesAndEveryKindOfDashAreAccepted() {
        assertEquals(PageRanges.of(1, 2, 3, 7, 8, 9), pages(" 1 – 3 ,7—9 "))
        assertEquals(PageRanges.of(1, 2, 3, 7, 8, 9), pages("1-3,7-9"))
        assertEquals(PageRanges.of(4), pages("04"))
    }

    @Test
    fun anOpenEndRunsToTheLastPage() {
        assertEquals(PageRanges.of(8, 9, 10), pages("8-", pageCount = 10))
        assertEquals(PageRanges.of(1, 2, 8, 9, 10), pages("1-2, 8 -", pageCount = 10))
        assertEquals(PageRanges.of(10), pages("10-", pageCount = 10))
    }

    @Test
    fun overlappingAndRepeatedPartsMergeAndSort() {
        assertEquals("1-12, 20", pages("20, 5-12, 1-6, 3, 20").format())
        assertEquals("1-6", pages("1-3, 4-6").format())
        assertEquals(pages("3,1,2"), pages("1-3"))
    }

    @Test
    fun aRunOfTwoIsWrittenAsARangeAndAlonePagesAreSeparate() {
        assertEquals("1-2, 4, 6-7", PageRanges.of(7, 6, 4, 2, 1).format())
        assertEquals("", PageRanges.Empty.format())
    }

    @Test
    fun theFormatIsReadBackToTheSamePages() {
        val ranges = PageRanges.of(listOf(1, 2, 3, 9, 11, 12, 400))
        assertEquals(ranges, pages(ranges.format()))
    }

    @Test
    fun blankTextIsEmpty() {
        assertEquals(PageRangeError.Empty, error(""))
        assertEquals(PageRangeError.Empty, error("   "))
    }

    @Test
    fun whatIsNotAPageRangeSaysWhereItWentWrong() {
        assertEquals(PageRangeError.Malformed(0), error("a"))
        assertEquals(PageRangeError.Malformed(2), error("1-x"))
        assertEquals(PageRangeError.Malformed(4), error("1-3 5"))
        assertEquals(PageRangeError.Malformed(4), error("1-3,"))
        assertEquals(PageRangeError.Malformed(0), error(",1"))
        assertEquals(PageRangeError.Malformed(0), error("-5"))
        assertEquals(PageRangeError.Malformed(1), error("1.5"))
    }

    @Test
    fun pagesTheDocumentDoesNotHaveAreOutOfRange() {
        assertEquals(PageRangeError.OutOfRange(0), error("0"))
        assertEquals(PageRangeError.OutOfRange(11), error("11", pageCount = 10))
        assertEquals(PageRangeError.OutOfRange(30), error("1-30", pageCount = 10))
        assertEquals(PageRangeError.OutOfRange(11), error("11-", pageCount = 10))
        assertEquals(PageRangeError.OutOfRange(Int.MAX_VALUE), error("99999999999999"))
        assertEquals(PageRangeError.OutOfRange(1), error("1", pageCount = 0))
    }

    @Test
    fun aRangeThatRunsBackwardsIsReversed() {
        assertEquals(PageRangeError.Reversed(10, 3), error("10-3"))
        assertEquals(PageRangeError.Reversed(5, 4), error("1, 5-4"))
    }

    @Test
    fun theFirstPagesAreWhatALimitLetsThrough() {
        val ranges = pages("1-10, 14, 20-25")
        assertEquals("1-10, 14, 20-21", ranges.first(13).format())
        assertEquals("1-3", ranges.first(3).format())
        assertEquals(ranges, ranges.first(100))
        assertEquals(PageRanges.Empty, ranges.first(0))
    }

    @Test
    fun allAndContains() {
        assertEquals("1-600", PageRanges.all(600).format())
        assertEquals(PageRanges.Empty, PageRanges.all(0))
        val ranges = pages("2-4, 9")
        assertEquals(listOf(false, true, true, true, false, true), listOf(1, 2, 4, 3, 5, 9).map { it in ranges })
    }

    @Test
    fun setsAddSubtractAndCompare() {
        val ranges = pages("2-9, 20")
        assertEquals("2-9, 12-15, 20", (ranges + PageRanges.of(12..15)).format())
        assertEquals("2-15, 20", (ranges + PageRanges.of(10..15)).format()) // touching runs join
        assertEquals("2-3, 8-9, 20", (ranges - PageRanges.of(4..7)).format())
        assertEquals("3-9", (ranges - PageRanges.of(2) - PageRanges.of(20)).format())
        assertEquals(ranges, ranges - PageRanges.of(30..40))
        assertEquals(PageRanges.Empty, ranges - PageRanges.all(600))
        assertEquals("2-3, 5-9, 20", (ranges - PageRanges.of(1..1) - PageRanges.of(4)).format())
        assertEquals(true, ranges.containsAll(PageRanges.of(3..5)))
        assertEquals(false, ranges.containsAll(PageRanges.of(8..10)))
        assertEquals(true, ranges.containsAll(PageRanges.Empty))
        assertEquals(PageRanges.Empty, PageRanges.of(5..4))
    }
}
