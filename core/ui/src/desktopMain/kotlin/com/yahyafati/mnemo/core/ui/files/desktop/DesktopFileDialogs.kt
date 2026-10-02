package com.yahyafati.mnemo.core.ui.files.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * The operating system's file dialogs through AWT (`FileDialog` is native on macOS, Windows and GTK
 * systems). Each function blocks until the dialog closes and returns the chosen absolute path, or null
 * when the user cancelled. Call them off the UI thread.
 */
object DesktopFileDialogs {
    /** Picks one existing file whose type matches one of [mimeTypes]; a wildcard shows every file. */
    fun open(mimeTypes: List<String>): String? {
        val dialog = FileDialog(null as Frame?, "Open", FileDialog.LOAD)
        extensionsFor(mimeTypes)?.let { extensions ->
            dialog.setFilenameFilter { _, name -> extensions.any { name.endsWith(".$it", ignoreCase = true) } }
        }
        return dialog.chosen()
    }

    /**
     * Chooses where to save [suggestedName]. The file exists (empty) afterwards, as the save
     * dialog's contract on Android is; the system dialog has already asked about overwriting.
     */
    fun save(suggestedName: String): String? {
        val dialog = FileDialog(null as Frame?, "Save", FileDialog.SAVE)
        dialog.file = suggestedName
        val path = dialog.chosen() ?: return null
        File(path).apply { absoluteFile.parentFile?.mkdirs() }.writeBytes(ByteArray(0))
        return path
    }

    /** Picks a folder. macOS' native dialog can do it with a property; elsewhere Swing's chooser does. */
    fun folder(): String? {
        if (System.getProperty("os.name").orEmpty().startsWith("Mac")) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                return FileDialog(null as Frame?, "Choose a folder", FileDialog.LOAD).chosen()
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }

    private fun FileDialog.chosen(): String? {
        isVisible = true
        val name = file ?: return null
        return File(directory, name).absolutePath
    }

    /** File extensions for [mimeTypes]; null when any file will do. */
    internal fun extensionsFor(mimeTypes: List<String>): List<String>? {
        if (mimeTypes.any { it == "*/*" || it == "application/octet-stream" }) return null
        return mimeTypes.flatMap { type ->
            when (type) {
                "image/*" -> listOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
                "audio/*" -> listOf("mp3", "ogg", "wav", "m4a", "flac", "opus", "aac")
                "application/pdf" -> listOf("pdf")
                "application/zip" -> listOf("zip", "apkg", "colpkg")
                "application/epub+zip" -> listOf("epub")
                "application/json" -> listOf("json")
                "text/plain" -> listOf("txt", "md")
                else -> emptyList()
            }
        }.ifEmpty { null }
    }
}
