package com.yahyafati.mnemo.core.security

import com.yahyafati.mnemo.core.model.KeyProtection
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSecretStoreTest {
    private val folder: File = Files.createTempDirectory("mnemo-secrets").toFile()

    @AfterTest
    fun deleteFiles() {
        folder.deleteRecursively()
    }

    @Test
    fun aStoreSaysWhereItsKeyIsKept() {
        val dispatcher = UnconfinedTestDispatcher()
        val keychain = object : SecretCipher by TestCipher() {
            override val protection = KeyProtection.OsKeychain
        }
        assertEquals(KeyProtection.OsKeychain, FileSecretStore({ folder }, keychain, dispatcher).protection)
        // The phone's keystore is what a cipher says unless it says otherwise.
        assertEquals(KeyProtection.PlatformKeystore, FileSecretStore({ folder }, TestCipher(), dispatcher).protection)
    }

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

    private fun store(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = FileSecretStore({ folder }, cipher, dispatcher)

    @Test
    fun keysAreStoredEncrypted() = runTest {
        val store = store(StandardTestDispatcher(testScheduler))
        store.put("provider-1", "sk-live-abcdef")
        assertEquals(StoredSecret.Present("sk-live-abcdef"), store.get("provider-1"))
        assertTrue(store.contains("provider-1"))
        assertEquals(StoredSecret.Missing, store.get("provider-2"))

        // Neither the key nor the id is readable on disk.
        val files = folder.listFiles()!!.toList()
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
        val (fa, fb) = folder.listFiles()!!.sortedBy { it.name }.let { it[0] to it[1] }
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

    @Test
    fun tamperingIsDetected() {
        val sealed = cipher.encrypt("secret".toByteArray(), "id".toByteArray())
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertFailsWith<GeneralSecurityException> { cipher.decrypt(sealed, "id".toByteArray()) }
    }
}
