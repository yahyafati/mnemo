package com.yahyafati.mnemo.core.testing.security

import com.yahyafati.mnemo.core.security.SecretCipher
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM with an in-memory key, for tests: Robolectric has no Android Keystore. Set [keyLost] to
 * act like a Keystore key that didn't survive a restore.
 */
class SoftwareSecretCipher : SecretCipher {
    private var key: SecretKey = newKey()
    var keyLost = false
        set(value) {
            field = value
            if (value) key = newKey()
        }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        if (ciphertext.size <= IV_BYTES) throw GeneralSecurityException("Truncated")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext, 0, IV_BYTES))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext, IV_BYTES, ciphertext.size - IV_BYTES)
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private companion object {
        const val IV_BYTES = 12
    }
}
