package com.yahyafati.mnemo.core.ui.files

import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.ui.files.android.rememberAndroidFilePicker
import com.yahyafati.mnemo.core.ui.files.android.rememberAndroidFileSaver
import com.yahyafati.mnemo.core.ui.files.android.rememberAndroidFileSharer
import com.yahyafati.mnemo.core.ui.files.android.rememberAndroidFolderPicker
import com.yahyafati.mnemo.core.ui.files.android.rememberAndroidMediaPicker

@Composable
actual fun rememberFilePicker(mimeTypes: List<String>, onPicked: (String) -> Unit): FilePicker =
    rememberAndroidFilePicker(mimeTypes, onPicked)

@Composable
actual fun rememberMediaPicker(mimeType: String, onPicked: (String) -> Unit): FilePicker =
    rememberAndroidMediaPicker(mimeType, onPicked)

@Composable
actual fun rememberFileSaver(mimeType: String, onSaved: (String) -> Unit): FileSaver =
    rememberAndroidFileSaver(mimeType, onSaved)

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): FolderPicker =
    rememberAndroidFolderPicker(onPicked)

@Composable
actual fun rememberFileSharer(mimeType: String): FileSharer = rememberAndroidFileSharer(mimeType)
