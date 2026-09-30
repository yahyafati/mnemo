package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.model.Media
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

/**
 * In-memory [MediaRepository]. [importUri] "reads" the bytes set in [files] for a URI, so tests can
 * attach media without a content resolver.
 */
class FakeMediaRepository : MediaRepository {
    override val directory: File = File("fake-media")

    val stored = mutableMapOf<String, Media>()

    /** Content for [importUri], by URI; a URI not listed can't be read. */
    val files = mutableMapOf<String, Pair<String, ByteArray>>()

    override suspend fun store(input: InputStream, name: String): Media {
        val bytes = input.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return stored.getOrPut(hash) { Media(hash, name, com.yahyafati.mnemo.core.model.MediaRef.mimeTypeFor(name), bytes.size.toLong(), Instant.EPOCH) }
    }

    override suspend fun importUri(uri: String): Media? = files[uri]?.let { (name, bytes) -> store(bytes.inputStream(), name) }

    override suspend fun getAll(): List<Media> = stored.values.toList()

    override fun file(hash: String): File = File(directory, hash)

    override suspend fun collectGarbage(): Int = 0
}
