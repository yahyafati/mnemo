package com.yahyafati.mnemo.core.sync.format

import com.yahyafati.mnemo.core.sync.FAST_ITERATIONS
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileCodecTest {
    private val key = EncryptionParams.create("pass".toCharArray(), FAST_ITERATIONS).second
    private val payload = ByteArray(5_000) { (it % 7).toByte() }

    @Test
    fun everyCombinationRoundTrips() {
        for (codec in listOf(FileCodec(null), FileCodec(key))) {
            for (compress in listOf(false, true)) {
                val file = codec.seal("media/x", payload, compress)
                assertContentEquals(payload, codec.open("media/x", file))
            }
        }
        assertContentEquals(ByteArray(0), FileCodec(key).let { it.open("a", it.seal("a", ByteArray(0), true)) })
    }

    @Test
    fun compressionShrinksRepetitiveDataAndEncryptionHidesIt() {
        val plain = FileCodec(null)
        assertTrue(plain.seal("a", payload, compress = true).size < payload.size / 4)
        val encrypted = FileCodec(key).seal("a", "needle in a haystack".toByteArray(), compress = false)
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("needle"))
    }

    @Test
    fun aCutOffFileIsCorruptAndMaybeStillBeingWritten() {
        val codec = FileCodec(key)
        val file = codec.seal("a", payload, true)
        for (length in listOf(0, 3, 10, 11, file.size / 2, file.size - 1)) {
            val e = assertFailsWith<SyncCorruptException>("length $length") { codec.open("a", file.copyOf(length)) }
            assertTrue(e.maybeIncomplete, "length $length")
        }
    }

    @Test
    fun aDamagedFileFailsItsChecksumAndWillNotHeal() {
        val codec = FileCodec(null)
        val file = codec.seal("a", payload, true)
        val damaged = file.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertFalse(assertFailsWith<SyncCorruptException> { codec.open("a", damaged) }.maybeIncomplete)
        assertFailsWith<SyncCorruptException> { codec.open("a", file + 0) }
        assertFailsWith<SyncCorruptException> { codec.open("a", "not a sync file at all".toByteArray()) }
    }

    @Test
    fun aNewerFormatVersionIsRefusedBeforeAnythingElseIsChecked() {
        val codec = FileCodec(null)
        val file = codec.seal("a", payload, false).copyOf()
        file[5] = (SyncFormat.VERSION + 1).toByte()
        val e = assertFailsWith<SyncUnsupportedVersionException> { codec.open("a", file) }
        assertEquals(SyncFormat.VERSION + 1, e.found)
        assertEquals(SyncFormat.VERSION, e.supported)
    }

    @Test
    fun anEncryptedFileNeedsTheKeyAndTheRightOne() {
        val file = FileCodec(key).seal("a", payload, true)
        assertTrue(assertFailsWith<SyncPassphraseException> { FileCodec(null).open("a", file) }.required)
        val other = SyncKey.fromBytes(ByteArray(32))
        assertFailsWith<SyncCorruptException> { FileCodec(other).open("a", file) }
    }

    @Test
    fun aFileMovedToAnotherNameDoesNotDecrypt() {
        val codec = FileCodec(key)
        val file = codec.seal("devices/a/changes/1.mnc", payload, true)
        assertFailsWith<SyncCorruptException> { codec.open("devices/a/changes/2.mnc", file) }
    }

    @Test
    fun anUnencryptedFileInAnEncryptedLocationIsRefused() {
        val file = FileCodec(null).seal("a", payload, true)
        assertFailsWith<SyncCorruptException> { FileCodec(key).open("a", file) }
    }

    @Test
    fun aCompressionBombIsStopped() {
        val big = ByteArray(1 shl 20)
        val file = FileCodec(null).seal("a", big, compress = true)
        assertTrue(file.size < 1_000)
        assertContentEquals(big, FileCodec(null, maxPayloadBytes = 1L shl 20).open("a", file))
        assertFailsWith<SyncCorruptException> { FileCodec(null, maxPayloadBytes = (1L shl 20) - 1).open("a", file) }
    }
}
