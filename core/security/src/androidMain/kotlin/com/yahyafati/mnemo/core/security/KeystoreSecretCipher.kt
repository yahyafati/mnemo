package com.yahyafati.mnemo.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key generated inside the Android Keystore. The key can't be exported: it
 * never exists in app memory, and it doesn't survive an uninstall, a restore to another device or
 * a "clear data". Ciphertexts made with it are useless anywhere else, which is why they are also
 * kept out of every backup.
 *
 * Output: `version (1) | IV length (1) | IV | ciphertext + tag`.
 */
class KeystoreSecretCipher() : SecretCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // The Keystore picks a random IV; supplying one is refused (randomized encryption).
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(associatedData)
        val iv = cipher.iv
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
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: throw GeneralSecurityException("No key"), GCMParameterSpec(TAG_BITS, ciphertext, 2, ivLength))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext, 2 + ivLength, ciphertext.size - 2 - ivLength)
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    @Synchronized
    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mnemo.secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
        const val VERSION: Byte = 1
    }
}
