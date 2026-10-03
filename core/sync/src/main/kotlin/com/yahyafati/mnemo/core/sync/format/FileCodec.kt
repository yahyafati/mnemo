package com.yahyafati.mnemo.core.sync.format

import com.squareup.zstd.okio.zstdCompress
import com.squareup.zstd.okio.zstdDecompress
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import okio.Buffer
import okio.buffer
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * The envelope around every remote file except `sync.json`:
 *
 * ```
 * "MNSY"  u16 version  u8 flags  u32 body length  body  sha-256 of everything before it
 * ```
 *
 * `flags`: bit 0 the body is encrypted, bit 1 it is zstd-compressed (compressed first, then encrypted). The
 * trailing checksum and the length are what tell a file whose write was cut off, or that a sync tool damaged, from
 * a whole one: such a file is never applied ([SyncCorruptException]). An encrypted location also authenticates
 * each file with its path (see [SyncKey]).
 *
 * [key] null means the location is not encrypted; a file that disagrees with that is refused.
 */
internal class FileCodec(
    private val key: SyncKey?,
    private val maxPayloadBytes: Long = SyncFormat.MAX_PAYLOAD_BYTES,
) {

    fun seal(path: String, payload: ByteArray, compress: Boolean): ByteArray {
        var flags = 0
        var body = payload
        if (compress) {
            flags = flags or COMPRESSED
            body = compress(body)
        }
        if (key != null) {
            flags = flags or ENCRYPTED
            body = key.encrypt(body, associatedData(path, SyncFormat.VERSION))
        }
        val out = ByteArrayOutputStream(HEADER_BYTES + body.size + CHECKSUM_BYTES)
        out.write(MAGIC)
        out.write(SyncFormat.VERSION ushr 8)
        out.write(SyncFormat.VERSION and 0xFF)
        out.write(flags)
        val length = body.size
        for (shift in intArrayOf(24, 16, 8, 0)) out.write(length ushr shift)
        out.write(body)
        out.write(MessageDigest.getInstance("SHA-256").digest(out.toByteArray()))
        return out.toByteArray()
    }

    /** The payload of [file], which was read from [path]; throws a [com.yahyafati.mnemo.core.sync.SyncException]. */
    fun open(path: String, file: ByteArray): ByteArray {
        if (file.size < HEADER_BYTES) throw SyncCorruptException("$path is too short to be a sync file", maybeIncomplete = true)
        if (!file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw SyncCorruptException("$path is not a sync file")
        val version = ((file[4].toInt() and 0xFF) shl 8) or (file[5].toInt() and 0xFF)
        if (version > SyncFormat.VERSION) throw SyncUnsupportedVersionException(version, SyncFormat.VERSION)
        if (version < 1) throw SyncCorruptException("$path has no format version")
        val flags = file[6].toInt() and 0xFF
        var length = 0L
        for (i in 7..10) length = (length shl 8) or (file[i].toLong() and 0xFF)
        val expected = HEADER_BYTES + length + CHECKSUM_BYTES
        if (file.size < expected) throw SyncCorruptException("$path is shorter than it says (${file.size} of $expected bytes)", maybeIncomplete = true)
        if (file.size > expected) throw SyncCorruptException("$path is longer than it says")
        val checksumAt = HEADER_BYTES + length.toInt()
        val sum = MessageDigest.getInstance("SHA-256").apply { update(file, 0, checksumAt) }.digest()
        if (!MessageDigest.isEqual(sum, file.copyOfRange(checksumAt, file.size))) throw SyncCorruptException("$path fails its checksum")

        var body = file.copyOfRange(HEADER_BYTES, checksumAt)
        val encrypted = flags and ENCRYPTED != 0
        when {
            encrypted && key == null -> throw SyncPassphraseException(required = true)
            !encrypted && key != null -> throw SyncCorruptException("$path is not encrypted, but its location is")
        }
        if (key != null) {
            body = key.decrypt(body, associatedData(path, version)) ?: throw SyncCorruptException("$path can't be decrypted: wrong key, or it was changed or moved")
        }
        return if (flags and COMPRESSED != 0) decompress(path, body, maxPayloadBytes) else body
    }

    private fun associatedData(path: String, version: Int) = "mnemo-sync/v$version/$path".toByteArray()

    companion object {
        private val MAGIC = "MNSY".toByteArray()
        private const val HEADER_BYTES = 11
        private const val CHECKSUM_BYTES = 32
        private const val ENCRYPTED = 1
        private const val COMPRESSED = 2

        /** What compressing [payload] gives, to size a change file before it is sealed. */
        fun compressedSize(payload: ByteArray): Int = compress(payload).size

        private fun compress(payload: ByteArray): ByteArray {
            val out = Buffer()
            out.zstdCompress().buffer().use { it.write(payload) }
            return out.readByteArray()
        }

        private fun decompress(path: String, body: ByteArray, limit: Long): ByteArray {
            try {
                Buffer().write(body).zstdDecompress().buffer().use { source ->
                    val out = ByteArrayOutputStream()
                    val chunk = ByteArray(64 * 1024)
                    while (true) {
                        val n = source.read(chunk)
                        if (n < 0) break
                        if (out.size() + n > limit) throw SyncCorruptException("$path is larger than a sync file may be")
                        out.write(chunk, 0, n)
                    }
                    return out.toByteArray()
                }
            } catch (e: Exception) {
                if (e is SyncCorruptException) throw e
                throw SyncCorruptException("$path doesn't decompress: ${e.message}")
            }
        }
    }
}
