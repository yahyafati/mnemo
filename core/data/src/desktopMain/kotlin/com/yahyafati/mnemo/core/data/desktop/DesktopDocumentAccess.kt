package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.platform.DocumentInfo
import com.yahyafati.mnemo.core.common.platform.FolderFile
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * [DocumentAccess] on a computer's file system: a location is an absolute path or a `file:` URI,
 * which is what the desktop pickers return. There is no permission model to keep, so
 * [keepAccess] and [releaseAccess] do nothing.
 */
class DesktopDocumentAccess : DocumentAccess {
    override fun info(uri: String): DocumentInfo {
        val file = fileOf(uri)
        val mimeType = when (file.extension.lowercase()) {
            "apkg", "colpkg", "zip" -> "application/zip"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            else -> MediaRef.mimeTypeFor(file.name).takeIf { it != "application/octet-stream" }
        }
        return DocumentInfo(name = file.name, mimeType = mimeType, size = file.takeIf { it.isFile }?.length())
    }

    override fun openInput(uri: String): InputStream = FileInputStream(fileOf(uri))

    override fun openOutput(uri: String): OutputStream {
        val file = fileOf(uri)
        file.absoluteFile.parentFile?.mkdirs()
        return FileOutputStream(file)
    }

    override fun delete(uri: String): Boolean = fileOf(uri).delete()

    override fun createInFolder(folderUri: String, name: String, mimeType: String): String {
        val folder = fileOf(folderUri)
        if (!folder.isDirectory) throw IOException("The folder is gone")
        // Never overwrite a file of the user's: "name (1).zip" like the system pickers do.
        var file = File(folder, name)
        var copy = 1
        while (file.exists()) {
            file = File(folder, "${name.substringBeforeLast('.')} ($copy)${name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }}")
            copy++
        }
        if (!file.createNewFile()) throw IOException("Can't create ${file.path}")
        return file.absolutePath
    }

    override fun listFolder(folderUri: String): List<FolderFile> {
        val files = fileOf(folderUri).listFiles() ?: throw IOException("The folder is gone")
        return files.filter { it.isFile }.map { FolderFile(it.absolutePath, it.name) }
    }

    override fun keepAccess(uri: String) = Unit

    override fun releaseAccess(uri: String) = Unit

    private fun fileOf(location: String): File =
        if (location.startsWith("file:")) File(URI(location)) else File(location)
}
