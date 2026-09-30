package com.yahyafati.mnemo.core.common.platform

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** What a picker's document says about itself; any part may be missing. */
data class DocumentInfo(
    /** The display name the user sees, or the last part of the location if the provider has none. */
    val name: String?,
    val mimeType: String?,
    val size: Long?,
)

/** A file in a folder the user granted access to. */
data class FolderFile(val uri: String, val name: String)

/**
 * The user's files, addressed by the location strings the platform's pickers return (content URIs
 * on Android, file paths or `file:` URIs on desktop). Everything that reads or writes something
 * the user picked goes through here.
 *
 * Every failure to read or write, including a revoked permission, is an [IOException].
 */
interface DocumentAccess {
    fun info(uri: String): DocumentInfo

    /** Opens [uri] for reading. The caller closes the stream. */
    fun openInput(uri: String): InputStream

    /** Opens [uri] for writing, replacing what it holds. The caller closes the stream. */
    fun openOutput(uri: String): OutputStream

    /** Deletes [uri]; false if it couldn't be. */
    fun delete(uri: String): Boolean

    /** Creates an empty file in the folder [folderUri] and returns its location. */
    fun createInFolder(folderUri: String, name: String, mimeType: String): String

    /** The files (not subfolders) in [folderUri]. */
    fun listFolder(folderUri: String): List<FolderFile>

    /** Keeps access to [uri] (a picked folder) across restarts. */
    fun keepAccess(uri: String)

    /** Gives up the access [keepAccess] kept. */
    fun releaseAccess(uri: String)
}
