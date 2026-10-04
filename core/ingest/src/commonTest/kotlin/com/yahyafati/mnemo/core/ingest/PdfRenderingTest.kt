package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.PageRegion
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

    @Test
    fun aFigureIsDrawnSoThatItsLongSideGetsTheEdgeWhateverItsSize() {
        // The whole of a US Letter page, then a quarter of it, then a thin strip: all 1,600 px on the long side.
        val full = figureGeometry(612f, 792f, PageRegion.Full, 1600)
        assertEquals(1236 to 1600, full.width to full.height)
        assertEquals(1600f / 792f, full.scale, 0.0001f)

        val quarter = figureGeometry(612f, 792f, PageRegion(0f, 0f, 0.5f, 0.5f), 1600)
        assertEquals(1600, maxOf(quarter.width, quarter.height))
        assertEquals(306f / 396f, quarter.width.toFloat() / quarter.height, 0.01f)

        val strip = figureGeometry(612f, 792f, PageRegion(0f, 0.4f, 1f, 0.45f), 1600)
        assertEquals(1600, strip.width)
        assertEquals(1600 * 39.6f / 612f, strip.height.toFloat(), 1f)
    }

    @Test
    fun aSmallRegionIsNotDrawnAtAMadeUpScale() {
        // A 5% square of a 4,000-point-wide poster would need a scale beyond the cap: the page is limited to 8,192 px.
        val geometry = figureGeometry(4000f, 3000f, PageRegion(0f, 0f, 0.05f, 0.05f), 1600)
        assertEquals(8192f / 4000f, geometry.scale, 0.0001f)
        assertEquals(410, geometry.width)
        assertEquals(307, geometry.height)
    }

    @Test
    fun theSmallerEncodingWinsAndTheFirstOnATie() {
        val jpeg = RenderedPage(ByteArray(900), "image/jpeg", 10, 10, blank = false)
        val png = RenderedPage(ByteArray(400), "image/png", 10, 10, blank = false)
        assertEquals("image/png", smallerOf(jpeg, png).mimeType)
        assertEquals("image/png", smallerOf(png, jpeg).mimeType)
        val sameSize = RenderedPage(ByteArray(900), "image/png", 10, 10, blank = false)
        assertEquals("image/jpeg", smallerOf(jpeg, sameSize).mimeType)
    }
}
