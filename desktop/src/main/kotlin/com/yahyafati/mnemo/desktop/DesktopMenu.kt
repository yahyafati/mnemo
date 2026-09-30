package com.yahyafati.mnemo.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ProjectLinks
import com.yahyafati.mnemo.core.ui.keyboard.Shortcut
import com.yahyafati.mnemo.core.ui.keyboard.Shortcuts
import com.yahyafati.mnemo.shell.AppCommand
import com.yahyafati.mnemo.shell.AppCommands
import com.yahyafati.mnemo.shell.navigation.TopLevelDestination

/** Whether this is macOS, where Settings and About live in the application menu and Quit is ⌘Q. */
internal val isMac: Boolean = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

/** The Compose shortcut for [this], or null for one that is a typed character (`?`), which a menu can't hold. */
internal fun Shortcut.toKeyShortcut(mac: Boolean = isMac): KeyShortcut? {
    val key = key ?: return null
    return KeyShortcut(key, ctrl = primary && !mac, meta = primary && mac, shift = shift, alt = alt)
}

/**
 * The window's menu bar (desktop ROADMAP D7). Every item sends an [AppCommand], as the key handlers
 * in the app do, and shows the same shortcut. Text editing keys (Undo, Cut, Copy, Paste) are not here:
 * a text field handles them, and on macOS a menu shortcut would take them from the field.
 */
@Composable
internal fun FrameWindowScope.DesktopMenuBar(commands: AppCommands, onQuit: () -> Unit) {
    fun send(command: AppCommand) = commands.send(command)
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Import…", onClick = { send(AppCommand.ChooseFileToOpen) }, shortcut = Shortcuts.Import.toKeyShortcut())
            Menu("Export") {
                Item("Anki package (.apkg)…", onClick = { send(AppCommand.ChooseExportLocation(ExportFormat.Apkg)) })
                Item("JSON…", onClick = { send(AppCommand.ChooseExportLocation(ExportFormat.Json)) })
            }
            Separator()
            Item("Back up…", onClick = { send(AppCommand.ChooseBackupLocation) })
            Item("Restore from backup…", onClick = { send(AppCommand.ChooseBackupToRestore) })
            // macOS has Quit (⌘Q) in the application menu.
            if (!isMac) {
                Separator()
                Item("Quit", onClick = onQuit, shortcut = KeyShortcut(Key.Q, ctrl = true))
            }
        }
        Menu("Edit", mnemonic = 'E') {
            Item("New note", onClick = { send(AppCommand.NewNote) }, shortcut = Shortcuts.NewNote.toKeyShortcut())
            Item("Find cards", onClick = { send(AppCommand.Find) }, shortcut = Shortcuts.Search.toKeyShortcut())
            // macOS has Settings (⌘,) in the application menu.
            if (!isMac) {
                Separator()
                Item("Settings…", onClick = { send(AppCommand.OpenSettings) }, shortcut = Shortcuts.Settings.toKeyShortcut())
            }
        }
        Menu("View", mnemonic = 'V') {
            TopLevelDestination.entries.zip(Shortcuts.Tabs).forEach { (tab, shortcut) ->
                Item(tab.name, onClick = { send(AppCommand.GoToTab(tab)) }, shortcut = shortcut.toKeyShortcut())
            }
        }
        Menu("Help", mnemonic = 'H') {
            Item("Keyboard shortcuts", onClick = { send(AppCommand.ShowShortcuts) })
            Item("Open-source licenses", onClick = { send(AppCommand.OpenLicenses) })
            Separator()
            Item("Report an issue", onClick = { openInBrowser(ProjectLinks.ISSUES) })
            Item("Privacy policy", onClick = { openInBrowser(ProjectLinks.PRIVACY_POLICY) })
            if (!isMac) {
                Separator()
                Item("About Mnemo", onClick = { send(AppCommand.OpenSettings) })
            }
        }
    }
}
