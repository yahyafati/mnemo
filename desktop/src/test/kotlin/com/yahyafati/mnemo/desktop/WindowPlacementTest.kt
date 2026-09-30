package com.yahyafati.mnemo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Remembering the window between runs (desktop ROADMAP D7). */
class WindowPlacementTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val laptop = ScreenArea(0f, 0f, 1440f, 900f)
    private val monitor = ScreenArea(1440f, 0f, 2560f, 1440f)

    @Test
    fun aWindowIsWrittenAndReadBack() {
        val file = WindowPlacements.file(folder.root)
        val window = SavedWindow(x = 120.5f, y = 64f, width = 1000f, height = 700.25f, maximized = true)
        WindowPlacements.write(file, window)
        assertEquals(window, WindowPlacements.read(file))
    }

    @Test
    fun nothingOrGarbageIsNoWindow() {
        val file = WindowPlacements.file(folder.root)
        assertNull(WindowPlacements.read(file))
        file.writeText("x=left\nwidth=wide\n")
        assertNull(WindowPlacements.read(file))
        file.writeText("x=NaN\ny=0\nwidth=900\nheight=700\nmaximized=false\n")
        assertNull(WindowPlacements.read(file))
    }

    @Test
    fun aWriteThatFailsIsNotAnError() {
        // The directory is a file: there is nowhere to write, and the window just opens at its default next time.
        val notADirectory = folder.newFile("taken")
        WindowPlacements.write(WindowPlacements.file(notADirectory), SavedWindow(0f, 0f, 900f, 700f, false))
    }

    @Test
    fun withoutASavedWindowItOpensWhereTheSystemPutsIt() {
        val initial = WindowPlacements.initial(null, listOf(laptop))
        assertEquals(WindowPosition.PlatformDefault, initial.position)
        assertEquals(WindowPlacements.DEFAULT_SIZE, initial.size)
        assertFalse(initial.maximized)
    }

    @Test
    fun aSavedWindowOnAScreenThatIsStillThereComesBack() {
        val saved = SavedWindow(x = 1500f, y = 100f, width = 1200f, height = 800f, maximized = false)
        val initial = WindowPlacements.initial(saved, listOf(laptop, monitor))
        assertEquals(WindowPosition.Absolute(1500.dp, 100.dp), initial.position)
        assertEquals(DpSize(1200.dp, 800.dp), initial.size)
    }

    @Test
    fun aWindowWhoseScreenIsGoneFallsBackToTheDefaultPosition() {
        // It was on the second monitor, which is unplugged: its title bar would be off every screen.
        val saved = SavedWindow(x = 1500f, y = 100f, width = 1200f, height = 800f, maximized = true)
        val initial = WindowPlacements.initial(saved, listOf(laptop))
        assertEquals(WindowPosition.PlatformDefault, initial.position)
        // Its size and maximized state are kept.
        assertEquals(DpSize(1200.dp, 800.dp), initial.size)
        assertTrue(initial.maximized)
    }

    @Test
    fun theSizeStaysBetweenTheMinimumAndTheBiggestScreen() {
        val tiny = WindowPlacements.initial(SavedWindow(10f, 10f, 200f, 100f, false), listOf(laptop))
        assertEquals(WindowPlacements.MINIMUM_SIZE, tiny.size)
        val huge = WindowPlacements.initial(SavedWindow(10f, 10f, 9000f, 9000f, false), listOf(laptop, monitor))
        assertEquals(DpSize(2560.dp, 1440.dp), huge.size)
        // With no screen information (no display) a saved window is taken as it is.
        val headless = WindowPlacements.initial(SavedWindow(5000f, 5000f, 1000f, 700f, false), emptyList())
        assertEquals(WindowPosition.Absolute(5000.dp, 5000.dp), headless.position)
    }

    @Test
    fun aMaximizedWindowRemembersTheSizeItHadBefore() {
        val tracker = WindowPlacementTracker(null)
        val floating = tracker.update(DpSize(1000.dp, 700.dp), WindowPosition.Absolute(80.dp, 60.dp), WindowPlacement.Floating)
        assertEquals(SavedWindow(80f, 60f, 1000f, 700f, maximized = false), floating)

        // Maximized, the window's bounds are the screen's: only the flag is kept.
        val maximized = tracker.update(DpSize(1440.dp, 900.dp), WindowPosition.Absolute(0.dp, 0.dp), WindowPlacement.Maximized)
        assertEquals(SavedWindow(80f, 60f, 1000f, 700f, maximized = true), maximized)

        // Full screen changes nothing; floating again saves the new bounds.
        assertEquals(maximized, tracker.update(DpSize(1440.dp, 900.dp), WindowPosition.Absolute(0.dp, 0.dp), WindowPlacement.Fullscreen))
        assertEquals(
            SavedWindow(10f, 20f, 900f, 650f, maximized = false),
            tracker.update(DpSize(900.dp, 650.dp), WindowPosition.Absolute(10.dp, 20.dp), WindowPlacement.Floating),
        )
    }

    @Test
    fun aWindowThatHasNotBeenPlacedYetIsNotSaved() {
        val tracker = WindowPlacementTracker(null)
        assertNull(tracker.update(DpSize(1100.dp, 800.dp), WindowPosition.PlatformDefault, WindowPlacement.Floating))
    }
}
