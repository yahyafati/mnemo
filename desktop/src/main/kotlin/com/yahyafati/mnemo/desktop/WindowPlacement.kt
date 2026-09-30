package com.yahyafati.mnemo.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import java.io.File
import java.util.Properties

/**
 * Where the window was when Mnemo last closed (desktop ROADMAP D7): the size and position it has when
 * it is not maximized, in dp (what a Compose window state holds), and whether it was maximized.
 */
data class SavedWindow(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val maximized: Boolean,
) {
    val size: DpSize get() = DpSize(width.dp, height.dp)
}

/** A screen's area in the same units as [SavedWindow]. */
data class ScreenArea(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun contains(pointX: Float, pointY: Float) = pointX >= x && pointX < x + width && pointY >= y && pointY < y + height
}

/** How the window opens: where, how big, and maximized or not. */
data class InitialWindow(val position: WindowPosition, val size: DpSize, val maximized: Boolean)

/** Remembering the window between runs: a few lines of text next to the collection, never in a backup. */
object WindowPlacements {
    val DEFAULT_SIZE = DpSize(1100.dp, 800.dp)

    /** Below this the layouts stop working (a rail and a card side by side need about this much). */
    val MINIMUM_SIZE = DpSize(720.dp, 520.dp)

    /** The file, inside the collection's directory. */
    fun file(directory: File) = File(directory, "window.properties")

    fun read(file: File): SavedWindow? = runCatching {
        val properties = Properties().apply { file.inputStream().use(::load) }
        SavedWindow(
            x = properties.getProperty("x").toFloat(),
            y = properties.getProperty("y").toFloat(),
            width = properties.getProperty("width").toFloat(),
            height = properties.getProperty("height").toFloat(),
            maximized = properties.getProperty("maximized").toBoolean(),
        ).takeIf { listOf(it.x, it.y, it.width, it.height).all(Float::isFinite) }
    }.getOrNull()

    /** Writes [window]; a failure (a read-only directory) only means the window opens at its default next time. */
    fun write(file: File, window: SavedWindow) {
        runCatching {
            val properties = Properties().apply {
                setProperty("x", window.x.toString())
                setProperty("y", window.y.toString())
                setProperty("width", window.width.toString())
                setProperty("height", window.height.toString())
                setProperty("maximized", window.maximized.toString())
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, "Mnemo window") }
        }
    }

    /**
     * The window to open: the [saved] one, if any, made to fit the [screens] that are connected now. A
     * size below the minimum or above the biggest screen is pulled in, and a position whose title bar
     * would be off every screen (the monitor it was on is gone) falls back to where the system puts a
     * new window. With no saved window, or no screen information (a headless test), the default.
     */
    fun initial(saved: SavedWindow?, screens: List<ScreenArea>): InitialWindow {
        if (saved == null) return InitialWindow(WindowPosition.PlatformDefault, DEFAULT_SIZE, maximized = false)
        val largest = screens.maxByOrNull { it.width * it.height }
        val width = saved.width.coerceAtLeast(MINIMUM_SIZE.width.value).let { if (largest != null) it.coerceAtMost(largest.width) else it }
        val height = saved.height.coerceAtLeast(MINIMUM_SIZE.height.value).let { if (largest != null) it.coerceAtMost(largest.height) else it }
        // A strip of the title bar, a little inside the window's corner, has to be on some screen to be grabbed.
        val grabbable = screens.isEmpty() || screens.any { it.contains(saved.x + TITLE_BAR_PROBE_X, saved.y + TITLE_BAR_PROBE_Y) }
        val position = if (grabbable) WindowPosition.Absolute(saved.x.dp, saved.y.dp) else WindowPosition.PlatformDefault
        return InitialWindow(position, DpSize(width.dp, height.dp), saved.maximized)
    }

    private const val TITLE_BAR_PROBE_X = 100f
    private const val TITLE_BAR_PROBE_Y = 16f
}

/**
 * What to save of a window that moves: its floating bounds while it floats, and only the maximized flag
 * while it is maximized, so un-maximizing next time returns to the size the user had chosen.
 */
class WindowPlacementTracker(initial: SavedWindow?) {
    var current: SavedWindow? = initial
        private set

    /** Takes the window's state now; returns what to save. */
    fun update(size: DpSize, position: WindowPosition, placement: WindowPlacement): SavedWindow? {
        val last = current
        current = when {
            placement == WindowPlacement.Floating && position.isSpecified && size != DpSize.Unspecified ->
                SavedWindow(position.x.value, position.y.value, size.width.value, size.height.value, maximized = false)
            placement == WindowPlacement.Maximized ->
                (last ?: SavedWindow(0f, 0f, WindowPlacements.DEFAULT_SIZE.width.value, WindowPlacements.DEFAULT_SIZE.height.value, true)).copy(maximized = true)
            else -> last
        }
        return current
    }
}
