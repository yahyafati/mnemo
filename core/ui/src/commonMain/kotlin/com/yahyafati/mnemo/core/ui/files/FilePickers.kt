package com.yahyafati.mnemo.core.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

// The system dialogs that hand Mnemo a file or a folder (desktop ROADMAP D3). Each returns a
// launcher and reports the chosen location, a string that `DocumentAccess` (`:core:common`) opens.
// A cancelled dialog reports nothing.
//
// These are the shared API; the implementations are per platform: the Storage Access Framework on
// Android (files/android), and the AWT and Swing file dialogs on desktop.

/** Opens a dialog to pick one file. */
@Stable
interface FilePicker {
    fun launch()
}

/** Opens a dialog to choose where to save a file, suggesting a name. */
@Stable
interface FileSaver {
    fun launch(suggestedName: String)
}

/** Opens a dialog to pick a folder, and keeps access to it across restarts. */
@Stable
interface FolderPicker {
    fun launch()
}

/**
 * Picks one file of one of [mimeTypes] (a wildcard subtype is allowed). Anki packages have no
 * registered type, so those callers pass the default, which shows every file.
 */
@Composable
expect fun rememberFilePicker(mimeTypes: List<String> = listOf("*/*"), onPicked: (String) -> Unit): FilePicker

/**
 * Picks an image or a sound for a card, of [mimeType] (the image or audio wildcard). On Android
 * that is the system's content chooser, which offers the gallery; elsewhere it is a file dialog filtered to [mimeType].
 */
@Composable
expect fun rememberMediaPicker(mimeType: String, onPicked: (String) -> Unit): FilePicker

/** Chooses where to save a new file of [mimeType]; the file exists (empty) once [onSaved] is called. */
@Composable
expect fun rememberFileSaver(mimeType: String, onSaved: (String) -> Unit): FileSaver

/** Picks a folder to read and write in. */
@Composable
expect fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker
