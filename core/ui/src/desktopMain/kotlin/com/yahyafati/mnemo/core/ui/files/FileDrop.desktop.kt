package com.yahyafati.mnemo.core.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
actual fun Modifier.fileDropTarget(
    accepts: (fileName: String) -> Boolean,
    onHover: (Boolean) -> Unit,
    onDrop: (paths: List<String>) -> Unit,
): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { event -> event.awtTransferable.droppedFiles().any { accepts(it.name) } },
    target = object : DragAndDropTarget {
        override fun onEntered(event: DragAndDropEvent) = onHover(true)

        override fun onExited(event: DragAndDropEvent) = onHover(false)

        override fun onEnded(event: DragAndDropEvent) = onHover(false)

        override fun onDrop(event: DragAndDropEvent): Boolean {
            onHover(false)
            val files = event.awtTransferable.droppedFiles().filter { accepts(it.name) }
            if (files.isEmpty()) return false
            onDrop(files.map { it.absolutePath })
            return true
        }
    },
)

/**
 * The files in a drag from the file manager. Java gives a file list on Windows, macOS and most Linux
 * desktops, and a `text/uri-list` of `file:` URIs from some Linux file managers. Anything else (text,
 * an image dragged from a browser) holds no files.
 */
internal fun Transferable.droppedFiles(): List<File> {
    runCatching {
        if (isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return (getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
        }
    }
    runCatching {
        val uriList = DataFlavor("text/uri-list;class=java.lang.String")
        if (isDataFlavorSupported(uriList)) {
            return (getTransferData(uriList) as String).lines()
                .map(String::trim)
                .filter { it.startsWith("file:") }
                .map { File(java.net.URI(it)) }
        }
    }
    return emptyList()
}
