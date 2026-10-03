package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.MediaRepository
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Media files as the sync engine needs them: by SHA-256, read to upload and written when they arrive. The files live
 * in `MediaRepository.directory`; a write goes to a temporary name and is renamed, so a crash never leaves a cut-off
 * file under a hash (the repository would take it for the real one).
 */
interface SyncMediaFiles {
    fun exists(hash: String): Boolean

    /** The file's bytes, or null if this device doesn't have it. */
    fun read(hash: String): ByteArray?

    fun write(hash: String, bytes: ByteArray)
}

internal class RepositorySyncMediaFiles(private val media: MediaRepository) : SyncMediaFiles {
    override fun exists(hash: String): Boolean = isHash(hash) && media.file(hash).isFile

    override fun read(hash: String): ByteArray? = if (exists(hash)) media.file(hash).readBytes() else null

    override fun write(hash: String, bytes: ByteArray) {
        // The name comes from another device's change file: never let it leave the folder.
        require(isHash(hash)) { "Not a media hash: $hash" }
        val directory = media.directory
        directory.mkdirs()
        val target = media.file(hash)
        if (target.isFile) return
        val temp = File(directory, ".tmp-${UUID.randomUUID()}")
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target) && !target.isFile) throw IOException("Can't store media $hash")
        } finally {
            temp.delete()
        }
    }

    companion object {
        fun isHash(name: String): Boolean = name.length == 64 && name.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
