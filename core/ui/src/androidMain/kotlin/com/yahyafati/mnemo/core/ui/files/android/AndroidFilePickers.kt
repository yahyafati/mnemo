package com.yahyafati.mnemo.core.ui.files.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yahyafati.mnemo.core.ui.files.FilePicker
import com.yahyafati.mnemo.core.ui.files.FileSaver
import com.yahyafati.mnemo.core.ui.files.FolderPicker

/** The Storage Access Framework's document picker. */
@Composable
fun rememberAndroidFilePicker(mimeTypes: List<String>, onPicked: (String) -> Unit): FilePicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher, mimeTypes) {
        object : FilePicker {
            override fun launch() = launcher.launch(mimeTypes.toTypedArray())
        }
    }
}

/** `GetContent`: the chooser that offers the gallery and other apps' content. */
@Composable
fun rememberAndroidMediaPicker(mimeType: String, onPicked: (String) -> Unit): FilePicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher, mimeType) {
        object : FilePicker {
            override fun launch() = launcher.launch(mimeType)
        }
    }
}

@Composable
fun rememberAndroidFileSaver(mimeType: String, onSaved: (String) -> Unit): FileSaver {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        uri?.let { onSaved(it.toString()) }
    }
    return remember(launcher) {
        object : FileSaver {
            override fun launch(suggestedName: String) = launcher.launch(suggestedName)
        }
    }
}

@Composable
fun rememberAndroidFolderPicker(onPicked: (String) -> Unit): FolderPicker {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onPicked(it.toString()) }
    }
    return remember(launcher) {
        object : FolderPicker {
            override fun launch() = launcher.launch(null)
        }
    }
}
