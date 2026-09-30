package com.yahyafati.mnemo.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.ui.keyboard.dispatch
import com.yahyafati.mnemo.shell.AppCommands
import com.yahyafati.mnemo.shell.globalShortcuts
import kotlinx.coroutines.FlowPreview
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
fun main() {
    // macOS: the name in the menu bar and a window chrome that follows the system's light or dark
    // appearance. Both have to be set before the first window exists.
    System.setProperty("apple.awt.application.name", "Mnemo")
    System.setProperty("apple.awt.application.appearance", "system")
    val session = openCollection()
    if (session == null) {
        // A second Mnemo must not open the same database: say so and leave.
        val message = "Mnemo is already open. Switch to its window instead of starting it again."
        if (GraphicsEnvironment.isHeadless()) System.err.println(message) else JOptionPane.showMessageDialog(null, message, "Mnemo", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(1)
    }
    application {
        val directories = session.koin.get<AppDirectories>()
        val placementFile = remember { WindowPlacements.file(directories.files) }
        val saved = remember { WindowPlacements.read(placementFile) }
        val initial = remember { WindowPlacements.initial(saved, connectedScreens()) }
        val state = rememberWindowState(
            placement = if (initial.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = initial.position,
            size = initial.size,
        )
        val tracker = remember { WindowPlacementTracker(saved) }
        val savePlacement = { tracker.update(state.size, state.position, state.placement)?.let { WindowPlacements.write(placementFile, it) } }
        // The window's place is saved half a second after it stops changing, and once more on closing.
        LaunchedEffect(state) {
            snapshotFlow { Triple(state.size, state.position, state.placement) }.debounce(500).collect { savePlacement() }
        }
        val commands = remember { AppCommands() }
        val quit = {
            savePlacement()
            exitApplication()
        }
        LaunchedEffect(Unit) { installDesktopIntegration(commands, quit) }
        val icon = remember { BitmapPainter(useResource("icons/mnemo.png", ::loadImageBitmap)) }
        // The app handles its shortcuts itself while it has the keyboard focus; the window hands it the
        // keys that nothing handled, so they work when nothing has the focus (after a page closes).
        val shortcuts = remember { globalShortcuts(commands) }
        Window(onCloseRequest = quit, title = "Mnemo", icon = icon, state = state, onKeyEvent = { shortcuts.dispatch(it) }) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(WindowPlacements.MINIMUM_SIZE.width.value.toInt(), WindowPlacements.MINIMUM_SIZE.height.value.toInt())
            }
            DesktopMenuBar(commands, onQuit = quit)
            DesktopApp(mediaDirectory = directories.media, commands = commands)
        }
    }
    session.close()
}
