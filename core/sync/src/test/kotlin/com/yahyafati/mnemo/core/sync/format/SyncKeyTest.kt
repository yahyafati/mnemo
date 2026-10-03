package com.yahyafati.mnemo.core.sync.format

import com.yahyafati.mnemo.core.sync.FAST_ITERATIONS
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncKeyTest {
    @Test
    fun pbkdf2MatchesTheRfc7914Vector() {
        val key = SyncKey.pbkdf2("passwd".toCharArray(), "salt".toByteArray(), 1, 512)
        assertEquals(
            "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783",
            key.joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun theSamePassphraseAndSaltGiveTheSameKeyAndAnotherSaltDoesNot() {
        val salt = ByteArray(16) { it.toByte() }
        val a = SyncKey.derive("correct horse".toCharArray(), salt, FAST_ITERATIONS)
        val b = SyncKey.derive("correct horse".toCharArray(), salt, FAST_ITERATIONS)
        val otherSalt = SyncKey.derive("correct horse".toCharArray(), ByteArray(16) { 1 }, FAST_ITERATIONS)
        val otherPassphrase = SyncKey.derive("correct horse!".toCharArray(), salt, FAST_ITERATIONS)
        assertContentEquals(a.toBytes(), b.toBytes())
        assertNotEquals(a.toBytes().toList(), otherSalt.toBytes().toList())
        assertNotEquals(a.toBytes().toList(), otherPassphrase.toBytes().toList())
    }

    @Test
    fun anAccentTypedAsOneCharacterOrTwoIsTheSamePassphrase() {
        val salt = ByteArray(16)
        val composed = SyncKey.derive("café".toCharArray(), salt, FAST_ITERATIONS)
        val decomposed = SyncKey.derive("café".toCharArray(), salt, FAST_ITERATIONS)
        assertContentEquals(composed.toBytes(), decomposed.toBytes())
    }

    @Test
    fun preparedPassphrasesAreAsciiSoNoProviderCanReadThemDifferently() {
        val prepared = String(SyncKey.prepare("pässwörd 🙂".toCharArray()))
        assertTrue(prepared.all { it in "0123456789abcdef" })
    }

    @Test
    fun encryptionRoundTripsAndNeverRepeatsACiphertext() {
        val key = SyncKey.derive("p".toCharArray(), ByteArray(16), FAST_ITERATIONS)
        val aad = "a".toByteArray()
        val one = key.encrypt("hello".toByteArray(), aad)
        val two = key.encrypt("hello".toByteArray(), aad)
        assertNotEquals(one.toList(), two.toList())
        assertEquals("hello", key.decrypt(one, aad)!!.decodeToString())
    }

    @Test
    fun aWrongKeyWrongAssociatedDataOrDamageDoesNotDecrypt() {
        val key = SyncKey.derive("p".toCharArray(), ByteArray(16), FAST_ITERATIONS)
        val other = SyncKey.derive("q".toCharArray(), ByteArray(16), FAST_ITERATIONS)
        val sealed = key.encrypt("hello".toByteArray(), "a".toByteArray())
        assertNull(other.decrypt(sealed, "a".toByteArray()))
        assertNull(key.decrypt(sealed, "b".toByteArray()))
        assertNull(key.decrypt(sealed.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }, "a".toByteArray()))
        assertNull(key.decrypt(ByteArray(5), "a".toByteArray()))
    }

    @Test
    fun keysSurviveBeingKeptAsBytes() {
        val key = SyncKey.derive("p".toCharArray(), ByteArray(16), FAST_ITERATIONS)
        val restored = SyncKey.fromBytes(key.toBytes())
        assertEquals("x", restored.decrypt(key.encrypt("x".toByteArray(), ByteArray(0)), ByteArray(0))!!.decodeToString())
        assertFailsWith<IllegalArgumentException> { SyncKey.fromBytes(ByteArray(5)) }
    }

    @Test
    fun unlockingChecksThePassphraseAtOnce() {
        val (params, key) = EncryptionParams.create("open sesame".toCharArray(), FAST_ITERATIONS)
        assertContentEquals(key.toBytes(), params.unlock("open sesame".toCharArray()).toBytes())
        val wrong = assertFailsWith<SyncPassphraseException> { params.unlock("open sesamE".toCharArray()) }
        assertEquals(false, wrong.required)
        assertTrue(params.verify(key))
        assertTrue(!params.verify(SyncKey.fromBytes(ByteArray(32))))
    }

    @Test
    fun theDefaultWorkFactorIsTheAdrsAndWorks() {
        assertEquals(600_000, EncryptionParams.DEFAULT_ITERATIONS)
        val (params, key) = EncryptionParams.create("a real passphrase".toCharArray())
        assertEquals(600_000, params.iterations)
        assertContentEquals(key.toBytes(), params.unlock("a real passphrase".toCharArray()).toBytes())
    }

    @Test
    fun anAbsurdIterationCountIsRefusedBeforeItCanHangTheApp() {
        val (params, _) = EncryptionParams.create("p".toCharArray(), FAST_ITERATIONS)
        assertFailsWith<SyncCorruptException> { params.copy(iterations = Int.MAX_VALUE).unlock("p".toCharArray()) }
        assertFailsWith<SyncCorruptException> { params.copy(iterations = 1).unlock("p".toCharArray()) }
        assertFailsWith<SyncCorruptException> { params.copy(kdf = "scrypt").unlock("p".toCharArray()) }
        assertFailsWith<IllegalArgumentException> { EncryptionParams.create("".toCharArray(), FAST_ITERATIONS) }
    }
}
