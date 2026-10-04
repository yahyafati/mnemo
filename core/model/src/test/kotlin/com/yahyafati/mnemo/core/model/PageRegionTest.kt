package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PageRegionTest {
    private val region = PageRegion(0.2f, 0.3f, 0.6f, 0.7f)

    private fun assertRegion(expected: PageRegion, actual: PageRegion) {
        fun close(a: Float, b: Float) = assertTrue(kotlin.math.abs(a - b) < 1e-5f, "expected $expected but was $actual")
        close(expected.left, actual.left)
        close(expected.top, actual.top)
        close(expected.right, actual.right)
        close(expected.bottom, actual.bottom)
    }

    @Test
    fun aRegionMustLieOnThePageAndHaveASize() {
        assertFailsWith<IllegalArgumentException> { PageRegion(-0.1f, 0f, 0.5f, 0.5f) }
        assertFailsWith<IllegalArgumentException> { PageRegion(0f, 0f, 1.1f, 0.5f) }
        assertFailsWith<IllegalArgumentException> { PageRegion(0.5f, 0.1f, 0.4f, 0.5f) }
        assertFailsWith<IllegalArgumentException> { PageRegion(0.1f, 0.1f, 0.12f, 0.5f) }
    }

    @Test
    fun theSameRegionIsTheSamePlaceAtAnyScale() {
        assertEquals(PixelRect(200, 300, 400, 400), region.toPixels(1000, 1000))
        assertEquals(PixelRect(20, 60, 40, 80), region.toPixels(100, 200))
        // The page drawn at 1,568 px and at 320 px: the same rectangle of it.
        val big = region.toPixels(1212, 1568)
        val small = region.toPixels(247, 320)
        assertEquals(big.left / 1212.0, small.left / 247.0, 0.01)
        assertEquals(big.height / 1568.0, small.height / 320.0, 0.01)
    }

    @Test
    fun pixelsStayOnTheImageAndAreNeverEmpty() {
        assertEquals(PixelRect(0, 0, 50, 50), PageRegion.Full.toPixels(50, 50))
        val tiny = PageRegion(0.95f, 0.95f, 1f, 1f).toPixels(10, 10)
        assertTrue(tiny.width >= 1 && tiny.height >= 1 && tiny.right <= 10 && tiny.bottom <= 10, "$tiny")
        assertFailsWith<IllegalArgumentException> { region.toPixels(0, 10) }
    }

    @Test
    fun movingKeepsTheSizeAndStopsAtTheEdge() {
        assertRegion(PageRegion(0.3f, 0.2f, 0.7f, 0.6f), region.moved(0.1f, -0.1f))
        assertRegion(PageRegion(0.6f, 0.6f, 1f, 1f), region.moved(5f, 5f))
        assertRegion(PageRegion(0f, 0f, 0.4f, 0.4f), region.moved(-5f, -5f))
    }

    @Test
    fun aCornerMovesAndTheOppositeOneStays() {
        assertRegion(PageRegion(0.1f, 0.25f, 0.6f, 0.7f), region.withCorner(RegionCorner.TopLeft, 0.1f, 0.25f))
        assertRegion(PageRegion(0.2f, 0.3f, 0.9f, 0.8f), region.withCorner(RegionCorner.BottomRight, 0.9f, 0.8f))
        assertRegion(PageRegion(0.2f, 0.1f, 0.8f, 0.7f), region.withCorner(RegionCorner.TopRight, 0.8f, 0.1f))
        assertRegion(PageRegion(0.05f, 0.3f, 0.6f, 0.95f), region.withCorner(RegionCorner.BottomLeft, 0.05f, 0.95f))
    }

    @Test
    fun aCornerDraggedPastTheOppositeOneStopsAtTheMinimumInsteadOfFlipping() {
        val bottomRight = region.withCorner(RegionCorner.BottomRight, 0f, 0f)
        assertRegion(PageRegion(0.2f, 0.3f, 0.25f, 0.35f), bottomRight)
        val topLeft = region.withCorner(RegionCorner.TopLeft, 1f, 1f)
        assertRegion(PageRegion(0.55f, 0.65f, 0.6f, 0.7f), topLeft)
    }

    @Test
    fun aCornerDraggedOffThePageStopsAtTheEdge() {
        assertRegion(PageRegion(0f, 0f, 0.6f, 0.7f), region.withCorner(RegionCorner.TopLeft, -3f, -3f))
        assertRegion(PageRegion(0.2f, 0.3f, 1f, 1f), region.withCorner(RegionCorner.BottomRight, 3f, 3f))
    }

    @Test
    fun theKeyboardResizesFromTheBottomRight() {
        assertRegion(PageRegion(0.2f, 0.3f, 0.62f, 0.69f), region.resized(0.02f, -0.01f))
        assertRegion(PageRegion(0.2f, 0.3f, 0.25f, 0.35f), region.resized(-1f, -1f))
    }

    @Test
    fun aNewCropStartsInsetFromThePageEdges() {
        assertRegion(PageRegion(0.1f, 0.1f, 0.9f, 0.9f), PageRegion.inset(0.1f))
        assertRegion(PageRegion(0.475f, 0.475f, 0.525f, 0.525f), PageRegion.inset(0.9f))
    }

    @Test
    fun theKeyNamesTheRegionInThousandthsForAFileName() {
        assertEquals("200-300-600-700", region.key)
        assertEquals("0-0-1000-1000", PageRegion.Full.key)
    }

    @Test
    fun onlyTheFrontOfAChoiceOrTypeInCardTakesAFigure() {
        assertEquals(listOf(FigureSide.Front, FigureSide.Back), FigureSide.allowedFor(NoteKind.Basic))
        assertEquals(listOf(FigureSide.Front, FigureSide.Back), FigureSide.allowedFor(NoteKind.Cloze))
        assertEquals(listOf(FigureSide.Front), FigureSide.allowedFor(NoteKind.MultipleChoice))
        assertEquals(listOf(FigureSide.Front), FigureSide.allowedFor(NoteKind.TypeIn))
        // Every kind has a front to put it on.
        NoteKind.entries.forEach { assertTrue(FigureSide.Front in FigureSide.allowedFor(it)) }
    }
}
