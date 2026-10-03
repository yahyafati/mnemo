package com.yahyafati.mnemo.core.sync

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.platform.DocumentInfo
import com.yahyafati.mnemo.core.common.platform.FolderFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * A [DocumentAccess] on a real directory that behaves like the platform ones: files only in a listing, a clash on
 * create is renamed "name (1)", a gone folder is an [IOException]. (`:core:testing`'s fake can't be used here: it
 * depends on `:core:data`, which will depend on this module.)
 */
class DirectoryDocumentAccess : DocumentAccess {
    /** Names whose stream accepts [writeLimit] bytes and then fails, like a disk that fills up. */
    var writeLimit: Int? = null

    override fun info(uri: String): DocumentInfo = File(uri).let { DocumentInfo(it.name, null, it.takeIf(File::isFile)?.length()) }

    override fun openInput(uri: String): InputStream = FileInputStream(uri)

    override fun openOutput(uri: String): OutputStream {
        val out = FileOutputStream(uri)
        val limit = writeLimit ?: return out
        return object : OutputStream() {
            var written = 0
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                val room = (limit - written).coerceAtLeast(0)
                out.write(b, off, minOf(room, len))
                written += minOf(room, len)
                if (len > room) throw IOException("No space left on device")
            }
            override fun close() = out.close()
        }
    }

    override fun delete(uri: String): Boolean = File(uri).delete()

    override fun createInFolder(folderUri: String, name: String, mimeType: String): String {
        val folder = File(folderUri)
        if (!folder.isDirectory) throw IOException("The folder is gone")
        var file = File(folder, name)
        var copy = 1
        while (file.exists()) file = File(folder, "$name ($copy)").also { copy++ }
        file.createNewFile()
        return file.absolutePath
    }

    override fun listFolder(folderUri: String): List<FolderFile> {
        val files = File(folderUri).listFiles() ?: throw IOException("The folder is gone")
        return files.filter { it.isFile }.map { FolderFile(it.absolutePath, it.name) }
    }

    override fun keepAccess(uri: String) = Unit

    override fun releaseAccess(uri: String) = Unit
}
