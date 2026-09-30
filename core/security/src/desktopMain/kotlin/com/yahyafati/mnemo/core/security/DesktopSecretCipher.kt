package com.yahyafati.mnemo.core.security

import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** How the key behind the encrypted API keys is kept on this computer. Settings says which one it is. */
enum class KeyProtection {
    /** Windows Credential Manager, macOS Keychain or the Secret Service (GNOME Keyring, KWallet). */
    OsKeychain,

    /** A file only this user can read, because the computer has no usable keychain. */
    KeyFile,
}

/** Somewhere to keep the AES key of a [DesktopSecretCipher]. */
interface KeyStorage {
    /** The saved key, or null if there is none (or it can't be read). */
    fun load(): ByteArray?

    /** Whether a key saved now can be read back, that is, whether this storage is usable at all. */
    fun works(): Boolean

    /** @throws IOException if the key can't be saved. */
    fun save(key: ByteArray)
}

/**
 * AES-256-GCM with a key that lives outside the app's data: in the OS keychain when the computer
 * has one that works ([keychain]), in [keyFile] otherwise. The key is generated on first use, and
 * ciphertexts have the same layout as the Android cipher's: `version (1) | IV length (1) | IV |
 * ciphertext + tag`.
 *
 * A key that is found is never replaced. A keychain that can't be used (nothing answers, or a
 * probe entry can't be saved and read back) is skipped for the key file, so a keychain that is
 * merely locked never causes a new key to be made over the one it holds. Secrets made with a key
 * that has since been lost read back as unreadable, and the user enters them again (see `SecretStore`).
 */
class DesktopSecretCipher(
    private val keychain: KeyStorage?,
    private val keyFile: KeyStorage,
) : SecretCipher {
    private val random = SecureRandom()
    private var cached: SecretKeySpec? = null

    /** Where the key is, or will be saved on first use. */
    val protection: KeyProtection
        get() = when {
            keychain?.load() != null -> KeyProtection.OsKeychain
            keyFile.load() != null -> KeyProtection.KeyFile
            keychain?.works() == true -> KeyProtection.OsKeychain
            else -> KeyProtection.KeyFile
        }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData)
        val sealed = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(2 + iv.size + sealed.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(sealed)
            .array()
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size < 2 || ciphertext[0] != VERSION) throw GeneralSecurityException("Unknown secret format")
        val ivLength = ciphertext[1].toInt()
        if (ivLength <= 0 || ciphertext.size < 2 + ivLength + TAG_BYTES) throw GeneralSecurityException("Truncated secret")
        val key = existingKey() ?: throw GeneralSecurityException("No key")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, ciphertext, 2, ivLength))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext, 2 + ivLength, ciphertext.size - 2 - ivLength)
    }

    @Synchronized
    private fun existingKey(): SecretKeySpec? {
        cached?.let { return it }
        val bytes = keychain?.load() ?: keyFile.load() ?: return null
        return SecretKeySpec(bytes, "AES").also { cached = it }
    }

    @Synchronized
    private fun key(): SecretKeySpec {
        existingKey()?.let { return it }
        val bytes = ByteArray(KEY_BYTES).also(random::nextBytes)
        val savedInKeychain = keychain?.let { it.works() && runCatching { it.save(bytes) }.isSuccess } ?: false
        if (!savedInKeychain) {
            try {
                keyFile.save(bytes)
            } catch (e: IOException) {
                throw GeneralSecurityException("Can't save the encryption key", e)
            }
        }
        return SecretKeySpec(bytes, "AES").also { cached = it }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val VERSION: Byte = 1
    }
}
