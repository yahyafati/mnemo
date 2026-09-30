package com.yahyafati.mnemo.core.security

import java.security.GeneralSecurityException

/**
 * Authenticated encryption for small secrets. [associatedData] binds a ciphertext to what it
 * belongs to (e.g. the provider id), so it can't be moved to another record and still decrypt.
 *
 * The platform decides where the key lives: the Android Keystore on the phone (`KeystoreSecretCipher`),
 * the OS keychain or a key file on the desktop (`DesktopSecretCipher`).
 */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    /** @throws GeneralSecurityException if the data was tampered with, or the key is gone. */
    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}
