package com.yahyafati.mnemo.core.ui.files

import androidx.compose.ui.Modifier

// A phone has no file manager to drag from.
actual fun Modifier.fileDropTarget(
    accepts: (fileName: String) -> Boolean,
    onHover: (Boolean) -> Unit,
    onDrop: (paths: List<String>) -> Unit,
): Modifier = this
