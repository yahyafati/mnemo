package com.yahyafati.mnemo.core.data.android

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.platform.DocumentInfo
import com.yahyafati.mnemo.core.common.platform.FolderFile
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * [DocumentAccess] on the Storage Access Framework: locations are content URIs, and folders are
 * document trees.
 */
class AndroidDocumentAccess(private val context: Context) : DocumentAccess {
    private val resolver get() = context.contentResolver

    override fun info(uri: String): DocumentInfo {
        val parsed = uri.toUri()
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    size = if (cursor.isNull(1)) null else cursor.getLong(1)
                }
            }
        }
        val mimeType = runCatching { resolver.getType(parsed) }.getOrNull()
        return DocumentInfo(name ?: parsed.lastPathSegment?.substringAfterLast('/'), mimeType, size)
    }

    override fun openInput(uri: String): InputStream = guarded {
        resolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException("Can't open $uri")
    }

    override fun openOutput(uri: String): OutputStream = guarded {
        resolver.openOutputStream(uri.toUri(), "wt") ?: throw FileNotFoundException("Can't write $uri")
    }

    override fun delete(uri: String): Boolean =
        runCatching { DocumentsContract.deleteDocument(resolver, uri.toUri()) }.getOrDefault(false)

    override fun createInFolder(folderUri: String, name: String, mimeType: String): String {
        val folder = DocumentFile.fromTreeUri(context, folderUri.toUri()) ?: throw IOException("The folder is gone")
        val file = guarded { folder.createFile(mimeType, name) } ?: throw IOException("Can't create a file in the folder")
        return file.uri.toString()
    }

    override fun listFolder(folderUri: String): List<FolderFile> {
        val folder = DocumentFile.fromTreeUri(context, folderUri.toUri()) ?: throw IOException("The folder is gone")
        return guarded { folder.listFiles() }
            .filter { it.isFile }
            .mapNotNull { file -> file.name?.let { FolderFile(file.uri.toString(), it) } }
    }

    override fun keepAccess(uri: String) {
        runCatching { resolver.takePersistableUriPermission(uri.toUri(), READ_WRITE) }
    }

    override fun releaseAccess(uri: String) {
        runCatching { resolver.releasePersistableUriPermission(uri.toUri(), READ_WRITE) }
    }

    /** A revoked permission is a failure to read or write, like any other. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw IOException(e.message, e)
    }

    private companion object {
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
