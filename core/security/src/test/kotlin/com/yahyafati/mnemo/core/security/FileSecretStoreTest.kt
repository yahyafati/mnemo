package com.yahyafati.mnemo.core.security

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSecretStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Software AES-GCM, standing in for the Keystore (not available on the JVM). */
    private class TestCipher : SecretCipher {
        var key = newKey()

        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            cipher.updateAAD(associatedData)
            return iv + cipher.doFinal(plaintext)
        }

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, 12)) }
            cipher.updateAAD(associatedData)
            return cipher.doFinal(ciphertext, 12, ciphertext.size - 12)
        }

        fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    private val cipher = TestCipher()

    private fun store(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = FileSecretStore({ folder.root }, cipher, dispatcher)

    @Test
    fun keysAreStoredEncrypted() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.put("provider-1", "sk-live-abcdef")
        assertEquals(StoredSecret.Present("sk-live-abcdef"), store.get("provider-1"))
        assertTrue(store.contains("provider-1"))
        assertEquals(StoredSecret.Missing, store.get("provider-2"))

        // Neither the key nor the id is readable on disk.
        val files = folder.root.listFiles()!!.toList()
        assertEquals(1, files.size)
        assertFalse("provider-1" in files.single().name)
        assertFalse("sk-live-abcdef" in files.single().readBytes().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun aSecretOnlyDecryptsForItsOwnId() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.put("a", "key-a")
        store.put("b", "key-b")
        // Swap the files: the associated data no longer matches.
        val (fa, fb) = folder.root.listFiles()!!.sortedBy { it.name }.let { it[0] to it[1] }
        val bytes = fa.readBytes()
        fa.writeBytes(fb.readBytes())
        fb.writeBytes(bytes)
        assertEquals(StoredSecret.Unreadable, store.get("a"))
        assertEquals(StoredSecret.Unreadable, store.get("b"))
    }

    @Test
    fun aLostKeyMakesSecretsUnreadable() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.put("a", "key-a")
        cipher.key = cipher.newKey()
        assertEquals(StoredSecret.Unreadable, store.get("a"))
        // Saving again works with the new key.
        store.put("a", "key-a2")
        assertEquals(StoredSecret.Present("key-a2"), store.get("a"))
    }

    @Test
    fun removeAndRetain() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        listOf("a", "b", "c").forEach { store.put(it, "key-$it") }
        store.remove("a")
        assertFalse(store.contains("a"))
        store.retainOnly(setOf("b"))
        assertTrue(store.contains("b"))
        assertFalse(store.contains("c"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun tamperingIsDetected() {
        val sealed = cipher.encrypt("secret".toByteArray(), "id".toByteArray())
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        cipher.decrypt(sealed, "id".toByteArray())
    }
}
