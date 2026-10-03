package com.yahyafati.mnemo.core.sync.store

import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncStore
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException

/**
 * A sync location in a folder the user picked, through [DocumentAccess]: a plain directory on a computer (which may
 * sit inside Google Drive for Desktop, Dropbox, Nextcloud or Syncthing), a Storage Access Framework tree on Android.
 *
 * [DocumentAccess] lists files but no subfolders, so the whole layout lives in the one folder and a path's `/`
 * becomes `__` in the file name (`devices/<id>/changes/3.mnc` → `devices__<id>__changes__3.mnc`).
 * Files that aren't ours (`.DS_Store`, "name (1)" copies a sync tool makes of a conflict) are not valid paths and
 * are never listed.
 *
 * There is no rename, so a write isn't atomic: a crash leaves a partial file, which the file's checksum catches
 * when it is read (`SyncRemote`), and an own file that is cut off is replaced on the next write. A name is claimed
 * by creating the file; a provider that renames a clash ("name (1)") is detected and undone, so
 * [SyncAlreadyExistsException] holds on Android too.
 */
class FolderSyncStore(
    private val documents: DocumentAccess,
    private val folderUri: String,
) : SyncStore {
    /** name → location, filled by listings and writes. The locations of a SAF tree can't be built from names. */
    private val locations = HashMap<String, String>()

    @Synchronized
    override fun list(prefix: String): List<String> {
        val files = guarded("list the sync folder") { documents.listFolder(folderUri) }
        locations.clear()
        val paths = ArrayList<String>()
        for (file in files) {
            val path = pathOf(file.name) ?: continue
            locations[file.name] = file.uri
            if (path.startsWith(prefix)) paths += path
        }
        return paths.sorted()
    }

    @Synchronized
    override fun read(path: String): ByteArray {
        val uri = locate(path) ?: throw SyncNotFoundException("$path isn't in the folder")
        return try {
            guarded("read $path") { documents.openInput(uri).use { it.readBytes() } }
        } catch (e: SyncNotFoundException) {
            locations.remove(nameOf(path))
            throw e
        }
    }

    @Synchronized
    override fun write(path: String, bytes: ByteArray) {
        val name = nameOf(path)
        if (locate(path) != null) throw SyncAlreadyExistsException("$path exists")
        val uri = guarded("create $path") { documents.createInFolder(folderUri, name, MIME_TYPE) }
        val created = runCatching { documents.info(uri).name }.getOrNull()
        if (created != null && created != name) {
            // The provider found the name taken (a file we didn't list) and made "name (1)".
            runCatching { documents.delete(uri) }
            locations.clear()
            throw SyncAlreadyExistsException("$path exists")
        }
        locations[name] = uri
        try {
            guarded("write $path") { documents.openOutput(uri).use { it.write(bytes) } }
        } catch (e: SyncException) {
            // Don't leave a partial file under the final name when we can tell the write failed.
            runCatching { documents.delete(uri) }
            locations.remove(name)
            throw e
        }
    }

    @Synchronized
    override fun overwrite(path: String, bytes: ByteArray) {
        val name = nameOf(path)
        val uri = locate(path) ?: guarded("create $path") { documents.createInFolder(folderUri, name, MIME_TYPE) }.also { locations[name] = it }
        guarded("write $path") { documents.openOutput(uri).use { it.write(bytes) } }
    }

    @Synchronized
    override fun delete(path: String) {
        val name = nameOf(path)
        val uri = locate(path) ?: return
        locations.remove(name)
        guarded("delete $path") { documents.delete(uri) }
    }

    /** The location of [path]'s file, from the last listing, or from a fresh one; null if the folder doesn't have it. */
    private fun locate(path: String): String? {
        val name = nameOf(path)
        locations[name]?.let { return it }
        list("")
        return locations[name]
    }

    private fun nameOf(path: String): String {
        SyncPaths.requireValid(path)
        return path.replace("/", SEPARATOR)
    }

    private fun pathOf(name: String): String? = name.replace(SEPARATOR, "/").takeIf(SyncPaths::isValid)

    private inline fun <T> guarded(what: String, block: () -> T): T = try {
        block()
    } catch (e: SyncException) {
        throw e
    } catch (e: IOException) {
        throw translate(what, e)
    }

    private fun translate(what: String, e: IOException): SyncException {
        val message = e.message.orEmpty()
        return when {
            e is FileNotFoundException && message.contains("denied", ignoreCase = true) -> SyncAuthException("No access to the sync folder ($what)", e)
            e is FileNotFoundException || e is NoSuchFileException -> SyncNotFoundException("Not found: $what", e)
            message.contains("no space", ignoreCase = true) || message.contains("quota", ignoreCase = true) ->
                SyncQuotaException("The sync folder is full ($what)", e)
            message.contains("denied", ignoreCase = true) || message.contains("permission", ignoreCase = true) ->
                SyncAuthException("No access to the sync folder ($what)", e)
            message.contains("folder is gone", ignoreCase = true) -> SyncNotFoundException("The sync folder is gone", e)
            else -> SyncIoException("Couldn't $what: ${e.message}", e)
        }
    }

    private companion object {
        const val SEPARATOR = "__"
        const val MIME_TYPE = "application/octet-stream"
    }
}
