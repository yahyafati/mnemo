package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.shell.AppCommand
import com.yahyafati.mnemo.shell.AppCommands
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.Taskbar
import java.net.URI
import javax.imageio.ImageIO

/** Opens [url] in the user's browser. Off the UI thread: some desktops take a moment to answer. */
internal fun openInBrowser(url: String) {
    Thread {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) Desktop.getDesktop().browse(URI(url))
        }
    }.apply { isDaemon = true }.start()
}

/** The screens that are connected now, for putting the window back where it was. Empty when there is no display. */
internal fun connectedScreens(): List<ScreenArea> {
    if (GraphicsEnvironment.isHeadless()) return emptyList()
    return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map {
        val bounds = it.defaultConfiguration.bounds
        ScreenArea(bounds.x.toFloat(), bounds.y.toFloat(), bounds.width.toFloat(), bounds.height.toFloat())
    }
}

/**
 * What the operating system expects of an application that a window doesn't cover (desktop ROADMAP D7):
 * the macOS application menu's About, Settings and Quit, the Dock icon, and files handed to the app
 * by the system (dropped on its Dock icon, or opened with it once the installers register the types, D8).
 * Anything a system doesn't have is skipped.
 */
internal fun installDesktopIntegration(commands: AppCommands, quit: () -> Unit) {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) desktop.setAboutHandler { commands.send(AppCommand.OpenSettings) }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) desktop.setPreferencesHandler { commands.send(AppCommand.OpenSettings) }
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            // Close through the window, so its place is saved, rather than letting the JVM exit.
            desktop.setQuitHandler { _, response ->
                response.cancelQuit()
                quit()
            }
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { event -> event.files.forEach { commands.send(AppCommand.OpenFile(it.absolutePath)) } }
        }
    }
    if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
        AppInfo::class.java.getResourceAsStream("/icons/mnemo.png")?.use { runCatching { Taskbar.getTaskbar().iconImage = ImageIO.read(it) } }
    }
}
