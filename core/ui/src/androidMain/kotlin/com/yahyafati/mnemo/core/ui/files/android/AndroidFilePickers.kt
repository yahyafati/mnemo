package com.yahyafati.mnemo.core.ui.files.android

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.yahyafati.mnemo.core.ui.files.FilePicker
import com.yahyafati.mnemo.core.ui.files.FileSaver
import com.yahyafati.mnemo.core.ui.files.FileSharer
import com.yahyafati.mnemo.core.ui.files.FolderPicker
import java.io.File

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

/**
 * `ACTION_SEND` through the app's `FileProvider` (declared in `:app`'s manifest, with the cache
 * folder it may share in `res/xml/share_paths.xml`). The chooser carries the read grant, so only
 * the app the user picks can open the file.
 */
@Composable
fun rememberAndroidFileSharer(mimeType: String): FileSharer {
    val context = LocalContext.current
    return remember(context, mimeType) {
        object : FileSharer {
            override fun share(location: String, title: String) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(location))
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, title)
                    clipData = ClipData.newRawUri(title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, title))
            }
        }
    }
}
