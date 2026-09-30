package com.yahyafati.mnemo.core.testing.platform

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.platform.DocumentInfo
import com.yahyafati.mnemo.core.common.platform.FolderFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [DocumentAccess] in memory. A location is any string; [put] makes a readable document, [folder]
 * a folder, and a document opened for writing exists once [openOutput] was called on it.
 */
class FakeDocumentAccess : DocumentAccess {
    private val documents = LinkedHashMap<String, ByteArray>()
    private val mimeTypes = HashMap<String, String>()
    private val folders = LinkedHashMap<String, MutableList<String>>()

    /** Locations that fail with an [IOException] when opened for writing (a full disk, a revoked grant). */
    val unwritable = mutableSetOf<String>()

    /** Locations whose stream opens fine but fails on the first write (a disk that fills up). */
    val failingWrites = mutableSetOf<String>()

    /** Folders that fail when a file is created in them. */
    val unavailableFolders = mutableSetOf<String>()

    val kept = mutableSetOf<String>()

    fun put(uri: String, content: ByteArray, mimeType: String? = null) {
        documents[uri] = content
        mimeType?.let { mimeTypes[uri] = it }
    }

    fun put(uri: String, content: String) = put(uri, content.toByteArray())

    fun folder(uri: String) {
        folders.getOrPut(uri) { mutableListOf() }
    }

    /** A file the user keeps in [folderUri]; returns its location. */
    fun putInFolder(folderUri: String, name: String, content: String = ""): String {
        val uri = "$folderUri/$name"
        folder(folderUri)
        folders.getValue(folderUri) += uri
        documents[uri] = content.toByteArray()
        return uri
    }

    fun exists(uri: String): Boolean = uri in documents

    fun content(uri: String): ByteArray = documents.getValue(uri)

    /** The names of the files in [folderUri], in creation order. */
    fun names(folderUri: String): List<String> = folders.getValue(folderUri).map { it.substringAfterLast('/') }

    override fun info(uri: String): DocumentInfo =
        DocumentInfo(name = uri.substringAfterLast('/'), mimeType = mimeTypes[uri], size = documents[uri]?.size?.toLong())

    override fun openInput(uri: String): InputStream =
        ByteArrayInputStream(documents[uri] ?: throw FileNotFoundException(uri))

    override fun openOutput(uri: String): OutputStream {
        if (uri in unwritable) throw IOException("Can't write $uri")
        documents[uri] = ByteArray(0)
        return object : ByteArrayOutputStream() {
            override fun write(b: Int) {
                if (uri in failingWrites) throw IOException("No space left on $uri")
                super.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                if (uri in failingWrites) throw IOException("No space left on $uri")
                super.write(b, off, len)
            }

            override fun flush() {
                documents[uri] = toByteArray()
            }

            override fun close() {
                flush()
            }
        }
    }

    override fun delete(uri: String): Boolean {
        folders.values.forEach { it.remove(uri) }
        return documents.remove(uri) != null
    }

    override fun createInFolder(folderUri: String, name: String, mimeType: String): String {
        if (folderUri in unavailableFolders) throw IOException("The folder is gone")
        val entries = folders[folderUri] ?: throw IOException("The folder is gone")
        val uri = "$folderUri/$name"
        entries += uri
        documents[uri] = ByteArray(0)
        mimeTypes[uri] = mimeType
        return uri
    }

    override fun listFolder(folderUri: String): List<FolderFile> =
        (folders[folderUri] ?: throw IOException("The folder is gone")).map { FolderFile(it, it.substringAfterLast('/')) }

    override fun keepAccess(uri: String) {
        kept += uri
    }

    override fun releaseAccess(uri: String) {
        kept -= uri
    }
}
