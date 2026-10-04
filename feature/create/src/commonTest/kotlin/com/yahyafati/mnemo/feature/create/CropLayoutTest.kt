package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.RegionCorner
import org.junit.Test
import kotlin.test.assertEquals

/** The arithmetic of the crop screen (docs/pdf/ROADMAP.md, P7): where the page sits, the box on it, and what a touch takes hold of. */
class CropLayoutTest {
    // A 600 × 800 page picture fitted into a 1,000 × 1,000 area: 750 × 1,000, centred, 125 px from the left.
    private val layout = CropLayout.fit(1000f, 1000f, 600, 800)
    private val region = PageRegion(0.2f, 0.25f, 0.6f, 0.75f)

    @Test
    fun aPictureIsFittedAndCentredInTheArea() {
        assertEquals(Rect(125f, 0f, 875f, 1000f), layout.bounds)
        // A wide picture in a tall area fills the width and floats in the middle.
        assertEquals(Rect(0f, 375f, 1000f, 625f), CropLayout.fit(1000f, 1000f, 800, 200).bounds)
        // A small picture is enlarged to fit, not left small.
        assertEquals(Rect(0f, 200f, 400f, 600f), CropLayout.fit(400f, 800f, 10, 10).bounds)
    }

    @Test
    fun theBoxIsTheRegionOfThePictureOnScreen() {
        assertEquals(Rect(275f, 250f, 575f, 750f), layout.rectOf(region))
        assertEquals(layout.bounds, layout.rectOf(PageRegion.Full))
    }

    @Test
    fun aScreenPointIsAFractionOfThePageAndStaysOnIt() {
        assertEquals(Offset(0.5f, 0.5f), layout.fractionOf(Offset(500f, 500f)))
        assertEquals(Offset(0f, 0f), layout.fractionOf(Offset(10f, -50f)))
        assertEquals(Offset(1f, 1f), layout.fractionOf(Offset(2000f, 2000f)))
    }

    @Test
    fun aTouchNearACornerTakesThatCornerInsideTheBoxTakesTheBoxAndElsewhereNothing() {
        val box = layout.rectOf(region)
        assertEquals(CropTarget.Corner(RegionCorner.TopLeft), layout.targetAt(Offset(280f, 255f), box, reach = 28f))
        assertEquals(CropTarget.Corner(RegionCorner.BottomRight), layout.targetAt(Offset(560f, 760f), box, reach = 28f))
        assertEquals(CropTarget.Corner(RegionCorner.TopRight), layout.targetAt(Offset(590f, 240f), box, reach = 28f))
        assertEquals(CropTarget.Corner(RegionCorner.BottomLeft), layout.targetAt(Offset(260f, 770f), box, reach = 28f))
        assertEquals(CropTarget.Move, layout.targetAt(Offset(420f, 500f), box, reach = 28f))
        assertEquals(CropTarget.None, layout.targetAt(Offset(800f, 900f), box, reach = 28f))
        // Just outside the reach of a corner and outside the box: nothing.
        assertEquals(CropTarget.None, layout.targetAt(Offset(240f, 215f), box, reach = 28f))
    }

    @Test
    fun theNearestCornerWinsWhenTwoAreInReach() {
        val small = layout.rectOf(PageRegion(0.4f, 0.4f, 0.5f, 0.45f)) // 75 × 50 px
        // Reach is limited to a third of the smaller side (about 16 px), so the corners don't cover the box.
        assertEquals(CropTarget.Move, layout.targetAt(small.center, small, reach = 28f))
        assertEquals(CropTarget.Corner(RegionCorner.TopLeft), layout.targetAt(small.topLeft + Offset(3f, 3f), small, reach = 28f))
    }

    @Test
    fun draggingACornerThroughTheLayoutGivesTheRegionTheCornerAtThePointer() {
        val box = layout.rectOf(region)
        val pointer = layout.fractionOf(Offset(425f, 100f))
        val moved = region.withCorner(RegionCorner.TopRight, pointer.x, pointer.y)
        assertEquals(0.4f, moved.right, 0.0001f)
        assertEquals(0.1f, moved.top, 0.0001f)
        assertEquals(region.left, moved.left)
        assertEquals(region.bottom, moved.bottom)
        assertEquals(Offset(575f, 250f), layout.cornerOf(box, RegionCorner.TopRight))
    }
}
