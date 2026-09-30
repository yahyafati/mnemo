package com.yahyafati.mnemo.core.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.GeneralSecurityException
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSecretCipherTest {
    private val directory: File = Files.createTempDirectory("mnemo-cipher").toFile()

    @AfterTest
    fun deleteFiles() {
        directory.deleteRecursively()
    }

    /** A key storage in memory: [working] false acts like a computer with no usable keychain. */
    private class MemoryStorage(var working: Boolean = true, var readable: Boolean = true) : KeyStorage {
        var key: ByteArray? = null
        var saves = 0

        override fun load(): ByteArray? = if (readable) key else null

        override fun works() = working

        override fun save(key: ByteArray) {
            if (!working) throw IOException("No keychain")
            saves++
            this.key = key
        }
    }

    private val id = "provider-1".toByteArray()

    @Test
    fun roundTripsAndBindsASecretToItsId() {
        val cipher = DesktopSecretCipher(MemoryStorage(), MemoryStorage())
        val sealed = cipher.encrypt("sk-live-abcdef".toByteArray(), id)

        assertEquals("sk-live-abcdef", cipher.decrypt(sealed, id).toString(Charsets.UTF_8))
        assertFailsWith<GeneralSecurityException> { cipher.decrypt(sealed, "other".toByteArray()) }
        // Same layout as the Android cipher: version, IV length, 12-byte IV, ciphertext and 16-byte tag.
        assertEquals(1, sealed[0].toInt())
        assertEquals(12, sealed[1].toInt())
        assertEquals(2 + 12 + "sk-live-abcdef".length + 16, sealed.size)
        assertFalse("sk-live-abcdef" in sealed.toString(Charsets.ISO_8859_1))
    }

    @Test
    fun tamperingAndGarbageAreRejected() {
        val cipher = DesktopSecretCipher(MemoryStorage(), MemoryStorage())
        val sealed = cipher.encrypt("secret".toByteArray(), id)
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()

        assertFailsWith<GeneralSecurityException> { cipher.decrypt(sealed, id) }
        assertFailsWith<GeneralSecurityException> { cipher.decrypt(byteArrayOf(9, 12, 0), id) }
        assertFailsWith<GeneralSecurityException> { cipher.decrypt(byteArrayOf(1, 12, 0), id) }
    }

    @Test
    fun theKeyGoesToTheKeychainWhenItWorksAndIsKeptAcrossRuns() {
        val keychain = MemoryStorage()
        val keyFile = MemoryStorage()
        val sealed = DesktopSecretCipher(keychain, keyFile).encrypt("secret".toByteArray(), id)

        assertNotNull(keychain.key)
        assertNull(keyFile.key)
        // A new run finds the key again.
        val later = DesktopSecretCipher(keychain, keyFile)
        assertEquals("secret", later.decrypt(sealed, id).toString(Charsets.UTF_8))
        later.encrypt("more".toByteArray(), id)
        assertEquals(1, keychain.saves)
        assertEquals(KeyProtection.OsKeychain, later.protection)
    }

    @Test
    fun withoutAKeychainTheKeyIsAFileAndSettingsCanSay() {
        val keychain = MemoryStorage(working = false)
        val keyFile = MemoryStorage()
        val cipher = DesktopSecretCipher(keychain, keyFile)

        assertEquals(KeyProtection.KeyFile, cipher.protection)
        val sealed = cipher.encrypt("secret".toByteArray(), id)

        assertNotNull(keyFile.key)
        assertEquals("secret", DesktopSecretCipher(keychain, keyFile).decrypt(sealed, id).toString(Charsets.UTF_8))
        assertEquals(KeyProtection.KeyFile, DesktopSecretCipher(keychain, keyFile).protection)
        // No keychain at all behaves the same.
        assertEquals(KeyProtection.KeyFile, DesktopSecretCipher(null, MemoryStorage()).protection)
    }

    @Test
    fun aKeychainThatIsUnusableIsSkippedAndItsKeyIsNotTouched() {
        // Locked or unreachable: reads find nothing, and it can't even take a probe entry.
        val keychain = MemoryStorage(working = false).apply { key = ByteArray(32) { 7 }; readable = false }
        val keyFile = MemoryStorage()

        DesktopSecretCipher(keychain, keyFile).encrypt("secret".toByteArray(), id)

        assertNotNull(keyFile.key)
        assertEquals(0, keychain.saves)
        assertContentEquals(ByteArray(32) { 7 }, keychain.key)
    }

    @Test
    fun aLostKeyMeansUnreadableSecretsAndANewKeyWorks() {
        val keychain = MemoryStorage()
        val cipher = DesktopSecretCipher(keychain, MemoryStorage())
        val sealed = cipher.encrypt("secret".toByteArray(), id)

        // The keychain entry is gone (a new user profile, a keychain reset).
        keychain.key = null
        val after = DesktopSecretCipher(keychain, MemoryStorage())
        assertFailsWith<GeneralSecurityException> { after.decrypt(sealed, id) }
        val fresh = after.encrypt("secret2".toByteArray(), id)
        assertEquals("secret2", after.decrypt(fresh, id).toString(Charsets.UTF_8))
    }

    @Test
    fun ifSavingToTheKeychainFailsTheKeyFileIsUsed() {
        val keychain = object : KeyStorage {
            override fun load(): ByteArray? = null
            override fun works() = true
            override fun save(key: ByteArray) = throw IOException("Access denied")
        }
        val keyFile = MemoryStorage()
        val sealed = DesktopSecretCipher(keychain, keyFile).encrypt("secret".toByteArray(), id)

        assertNotNull(keyFile.key)
        assertEquals("secret", DesktopSecretCipher(keychain, keyFile).decrypt(sealed, id).toString(Charsets.UTF_8))
    }

    @Test
    fun theKeyFileIsPrivateAndNeverReplacedByGarbage() {
        val file = File(directory, "secrets.key")
        val storage = KeyFileStorage(file)
        assertNull(storage.load())

        val key = ByteArray(32) { it.toByte() }
        storage.save(key)

        assertContentEquals(key, storage.load())
        assertTrue(File(directory, "secrets.key.tmp").exists().not())
        if (file.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(file.toPath()),
            )
        }
        // A damaged file is not a key.
        file.writeBytes(ByteArray(5))
        assertNull(storage.load())
        // Saving again replaces it.
        storage.save(key)
        assertContentEquals(key, storage.load())
    }

    @Test
    fun theOsKeychainStoresAndReturnsAKeyWhereThereIsOne() {
        val storage = KeyringKeyStorage(service = "Mnemo test", account = "secrets-key-${UUID.randomUUID()}")
        // CI runners and headless Linux boxes have no keychain: nothing to check there.
        if (!storage.works()) {
            println("No usable OS keychain on this machine: skipped")
            return
        }
        val key = ByteArray(32) { (it * 3).toByte() }
        try {
            assertNull(storage.load())
            storage.save(key)
            assertContentEquals(key, storage.load())
            val other = ByteArray(32) { 1 }
            storage.save(other)
            assertContentEquals(other, storage.load())
            assertNotEquals(key.toList(), storage.load()!!.toList())
        } finally {
            storage.delete()
        }
        assertNull(storage.load())
    }

    private fun assertFalse(value: Boolean) = kotlin.test.assertFalse(value)
}
