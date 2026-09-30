package com.yahyafati.mnemo.shell

import androidx.compose.runtime.Stable
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.shell.navigation.TopLevelDestination
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Something the user asked the app to do from outside the screen in front of them: a menu item, a
 * keyboard shortcut, a file dropped on the window (desktop ROADMAP D7). The shell ([MnemoRoot])
 * carries them out, so every route to an action (menu, key, drop) goes through one place.
 */
sealed interface AppCommand {
    data class GoToTab(val tab: TopLevelDestination) : AppCommand

    /** The note editor, adding to whichever deck it last used. */
    data object NewNote : AppCommand

    /** Finds cards: focuses the search box of the card browser, opening the browser if needed. */
    data object Find : AppCommand

    /** Goes back from a full-screen page; does nothing on a tab. */
    data object Back : AppCommand

    data object OpenSettings : AppCommand

    data object OpenLicenses : AppCommand

    data object ShowShortcuts : AppCommand

    /** Asks for a file (File › Import…) and opens it with [OpenFile]. */
    data object ChooseFileToOpen : AppCommand

    /** Asks for a backup to restore (File › Restore…), and restores it with [RestoreBackup]. */
    data object ChooseBackupToRestore : AppCommand

    /** Asks where to save a backup (File › Back up…). */
    data object ChooseBackupLocation : AppCommand

    /** Asks where to save the collection as [format] (File › Export). */
    data class ChooseExportLocation(val format: ExportFormat) : AppCommand

    /** Opens the file at [location] by what it is: an Anki package is imported, a zip is a backup to restore. */
    data class OpenFile(val location: String) : AppCommand

    data class RestoreBackup(val location: String) : AppCommand

    data class BackUpTo(val location: String) : AppCommand

    data class ExportTo(val location: String, val format: ExportFormat) : AppCommand
}

/**
 * The queue of [AppCommand]s between whoever raises them (the window's menu bar, key handlers, a
 * drop target) and the shell. Commands wait here until the shell is showing, so one sent during
 * start-up isn't lost.
 */
@Stable
class AppCommands {
    private val channel = Channel<AppCommand>(Channel.UNLIMITED)

    /** Collect from one place (the shell): each command is delivered once. */
    val flow: Flow<AppCommand> = channel.receiveAsFlow()

    fun send(command: AppCommand) {
        channel.trySend(command)
    }
}

/**
 * The commands for files dropped on the window: every Anki package is imported, one after another;
 * with none, the first backup is offered for restoring. Files of any other kind are not taken.
 */
fun droppedFileCommands(paths: List<String>): List<AppCommand> {
    val packages = paths.filter { FileKind.of(it) == FileKind.AnkiPackage }
    if (packages.isNotEmpty()) return packages.map { AppCommand.OpenFile(it) }
    return listOfNotNull(paths.firstOrNull { FileKind.of(it) == FileKind.Backup }?.let { AppCommand.OpenFile(it) })
}

/** What a file is to Mnemo, going by its name. */
enum class FileKind {
    /** An Anki package: `.apkg`, `.colpkg`. Imported. */
    AnkiPackage,

    /** A Mnemo backup: `.zip`. Restored. */
    Backup,

    Unknown,
    ;

    companion object {
        fun of(location: String): FileKind = when (location.substringAfterLast('.', "").lowercase()) {
            "apkg", "colpkg" -> AnkiPackage
            "zip" -> Backup
            else -> Unknown
        }
    }
}
