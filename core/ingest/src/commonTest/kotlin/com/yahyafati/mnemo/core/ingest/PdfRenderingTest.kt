package com.yahyafati.mnemo.core.ingest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The rules both page renderers share (docs/pdf/ROADMAP.md, P3): sizes, the size limit and telling a white page from a written one. */
class PdfRenderingTest {
    @Test
    fun theLongSideGetsTheEdgeAndTheShortSideKeepsTheShape() {
        assertEquals(1212 to 1568, fitLongEdge(612f, 792f, 1568)) // US Letter, portrait
        assertEquals(1568 to 882, fitLongEdge(960f, 540f, 1568)) // 16:9 slides
        assertEquals(1280 to 2048, fitLongEdge(595f, 952f, 2048))
        assertEquals(320 to 240, fitLongEdge(4f, 3f, 320))
    }

    @Test
    fun aPageNeverShrinksToNothing() {
        assertEquals(1 to 400, fitLongEdge(1f, 1000f, 400))
    }

    private fun page(size: Int, edge: Int, quality: Int) = RenderedPage(ByteArray(size), "image/jpeg", edge, edge, blank = false).also {
        attempts += edge to quality
    }

    private val attempts = mutableListOf<Pair<Int, Int>>()

    @Test
    fun aPageUnderTheLimitIsEncodedOnce() {
        val result = encodeWithin(1568, limit = 1000) { edge, quality -> page(900, edge, quality) }
        assertEquals(900, result.bytes.size)
        assertEquals(listOf(1568 to 80), attempts)
    }

    @Test
    fun aPageOverTheLimitIsEncodedAgainLowerAndThenSmaller() {
        // Each attempt is smaller than the last; the third fits.
        val sizes = listOf(2_000, 1_400, 900).iterator()
        val result = encodeWithin(1568, limit = 1000) { edge, quality -> page(sizes.next(), edge, quality) }
        assertEquals(900, result.bytes.size)
        assertEquals(listOf(1568 to 80, 1568 to 60, 1176 to 60), attempts)
    }

    @Test
    fun aPageThatNeverFitsEndsAtTheLastAttemptAndStopsAtTheMinimumEdge() {
        val result = encodeWithin(1000, limit = 10) { edge, quality -> page(5_000, edge, quality) }
        assertEquals(5_000, result.bytes.size)
        // 1000 -> q60, 750, 500 (q50) and then 350 is under the 400 px floor.
        assertEquals(listOf(1000 to 80, 1000 to 60, 750 to 60, 500 to 50), attempts)
    }

    @Test
    fun aSmallThumbnailIsNeverShrunkBelowItsEdge() {
        val result = encodeWithin(320, limit = 10) { edge, quality -> page(5_000, edge, quality) }
        assertEquals(5_000, result.bytes.size)
        assertEquals(listOf(320 to 80), attempts)
    }

    private fun pixels(count: Int, dark: Int, grey: Int = 0x20): IntArray =
        IntArray(count) { if (it < dark) 0xFF000000.toInt() or (grey * 0x010101) else 0xFFFFFFFF.toInt() }

    @Test
    fun aWhitePageHasNoInkAndATextedOneHas() {
        val white = InkCounter(1_000_000).apply { add(pixels(1_000_000, dark = 0)) }
        assertFalse(white.hasInk)

        val written = InkCounter(1_000_000).apply { add(pixels(1_000_000, dark = 5_000)) }
        assertTrue(written.hasInk)
    }

    @Test
    fun aStraySpeckAndPaleGreyAreNotInk() {
        assertFalse(InkCounter(1_000_000).apply { add(pixels(1_000_000, dark = 3)) }.hasInk)
        // Light grey (a scan's yellowed paper or a watermark) is above the threshold.
        assertFalse(InkCounter(1_000_000).apply { add(pixels(1_000_000, dark = 100_000, grey = 0xC8)) }.hasInk)
    }

    @Test
    fun inkAddsUpAcrossRows() {
        val counter = InkCounter(1_000_000)
        repeat(200) { counter.add(pixels(1_000, dark = 1)) }
        assertTrue(counter.hasInk)
    }

    @Test
    fun aSmallImageNeedsOnlyTheMinimumNumberOfDarkPixels() {
        assertTrue(InkCounter(320 * 240).apply { add(pixels(320 * 240, dark = 40)) }.hasInk)
        assertFalse(InkCounter(320 * 240).apply { add(pixels(320 * 240, dark = 11)) }.hasInk)
    }
}
