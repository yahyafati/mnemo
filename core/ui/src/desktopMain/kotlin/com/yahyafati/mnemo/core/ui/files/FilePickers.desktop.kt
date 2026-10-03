package com.yahyafati.mnemo.core.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.yahyafati.mnemo.core.ui.files.desktop.DesktopFileDialogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The desktop pickers: native AWT file dialogs (Swing's chooser for a folder off macOS). A dialog
// blocks its thread until it closes, so it runs on the IO dispatcher. A location is an absolute path,
// which `DesktopDocumentAccess` opens.

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, onPicked: (String) -> Unit): FilePicker {
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return remember(mimeTypes, scope) {
        object : FilePicker {
            override fun launch() = scope.showDialog({ DesktopFileDialogs.open(mimeTypes) }) { latest(it) }
        }
    }
}

@Composable
actual fun rememberMediaPicker(mimeType: String, onPicked: (String) -> Unit): FilePicker =
    rememberFilePicker(listOf(mimeType), onPicked)

@Composable
actual fun rememberFileSaver(mimeType: String, onSaved: (String) -> Unit): FileSaver {
    val latest by rememberUpdatedState(onSaved)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        object : FileSaver {
            override fun launch(suggestedName: String) =
                scope.showDialog({ DesktopFileDialogs.save(suggestedName) }) { latest(it) }
        }
    }
}

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker {
    val latest by rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return remember(scope) {
        object : FolderPicker {
            override fun launch() = scope.showDialog({ DesktopFileDialogs.folder() }) { latest(it) }
        }
    }
}

/** A computer has no share sheet (`shareFiles` is off, so nothing offers sharing): Export saves the file instead. */
@Composable
actual fun rememberFileSharer(mimeType: String): FileSharer = remember { NoFileSharer }

private object NoFileSharer : FileSharer {
    override fun share(location: String, title: String) = Unit
}

private fun CoroutineScope.showDialog(dialog: () -> String?, onChosen: (String) -> Unit) {
    launch {
        val chosen = withContext(Dispatchers.IO) { dialog() }
        if (chosen != null) onChosen(chosen)
    }
}
