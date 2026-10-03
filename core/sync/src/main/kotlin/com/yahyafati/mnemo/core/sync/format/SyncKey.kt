package com.yahyafati.mnemo.core.sync.format

import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import kotlinx.serialization.Serializable
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The key a location's files are encrypted with: AES-256-GCM, derived from the passphrase (ADR 0013). Only the
 * derived key is kept on a device ([toBytes], in `SecretStore`), never the passphrase.
 *
 * A sealed file is `nonce (12 bytes) + ciphertext + tag`. The associated data names the file, so a file that was
 * moved or renamed on the backend doesn't decrypt.
 */
class SyncKey private constructor(private val bytes: ByteArray) {
    /** A copy of the key, for `SecretStore`. Treat it as a secret. */
    fun toBytes(): ByteArray = bytes.copyOf()

    internal fun encrypt(plain: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(bytes, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return nonce + cipher.doFinal(plain)
    }

    /** The plain bytes, or null if the key, the data or the file's name is wrong (or the file was changed). */
    internal fun decrypt(sealed: ByteArray, associatedData: ByteArray): ByteArray? {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(bytes, "AES"), GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, NONCE_BYTES)))
            cipher.updateAAD(associatedData)
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    companion object {
        const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val random = SecureRandom()

        fun fromBytes(bytes: ByteArray): SyncKey {
            require(bytes.size == KEY_BYTES) { "A sync key is $KEY_BYTES bytes" }
            return SyncKey(bytes.copyOf())
        }

        internal fun derive(passphrase: CharArray, salt: ByteArray, iterations: Int): SyncKey =
            SyncKey(pbkdf2(prepare(passphrase), salt, iterations, KEY_BYTES * 8))

        /**
         * PBKDF2-HMAC-SHA256 as the JDK has it. What PBKDF2 does with the characters of a password is up to the
         * security provider (the JDK uses UTF-8, Android's providers have differed), so a passphrase with an
         * accent could give two devices two keys. [prepare] turns it into plain ASCII first.
         */
        internal fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bits: Int): ByteArray {
            val spec = PBEKeySpec(password, salt, iterations, bits)
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        /** NFKC (so é typed as one character or two is the same passphrase), UTF-8, then hex. */
        internal fun prepare(passphrase: CharArray): CharArray {
            val normalized = Normalizer.normalize(String(passphrase), Normalizer.Form.NFKC)
            val hex = "0123456789abcdef"
            val utf8 = normalized.toByteArray(Charsets.UTF_8)
            return CharArray(utf8.size * 2) { i ->
                val byte = utf8[i / 2].toInt() and 0xFF
                hex[if (i % 2 == 0) byte ushr 4 else byte and 0x0F]
            }
        }
    }
}

/**
 * How a location's passphrase is turned into a key, and a check value so a wrong passphrase fails at once, in
 * `sync.json`, and not as a corrupted file later. Not secret: the salt and the check reveal nothing without the
 * passphrase (beyond letting someone guess it, which the iteration count makes slow).
 */
@Serializable
data class EncryptionParams(
    val kdf: String = KDF,
    /** Stored, so the count can rise later without breaking old locations. */
    val iterations: Int,
    /** Base64. */
    val salt: String,
    /** Base64: [CHECK_TEXT] sealed with the key. */
    val check: String,
) {
    /** The key for [passphrase], or [SyncPassphraseException] if it is wrong. */
    fun unlock(passphrase: CharArray): SyncKey {
        validate()
        val key = SyncKey.derive(passphrase, Base64.getDecoder().decode(salt), iterations)
        if (!verify(key)) throw SyncPassphraseException(required = false)
        return key
    }

    /** True if [key] is the one this location was made with, e.g. a key kept from an earlier session. */
    fun verify(key: SyncKey): Boolean {
        val sealed = runCatching { Base64.getDecoder().decode(check) }.getOrNull() ?: return false
        val plain = key.decrypt(sealed, CHECK_AAD) ?: return false
        return MessageDigest.isEqual(plain, CHECK_TEXT)
    }

    internal fun validate() {
        if (kdf != KDF) throw SyncCorruptException("Unknown key derivation: $kdf")
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw SyncCorruptException("Unusable iteration count: $iterations")
        if (runCatching { Base64.getDecoder().decode(salt) }.getOrNull()?.isNotEmpty() != true) throw SyncCorruptException("Unusable salt")
    }

    companion object {
        const val KDF = "pbkdf2-hmac-sha256"
        const val DEFAULT_ITERATIONS = 600_000
        const val MIN_ITERATIONS = 1_000
        const val MAX_ITERATIONS = 10_000_000
        private const val SALT_BYTES = 16
        private val CHECK_TEXT = "mnemo-sync-check".toByteArray()
        private val CHECK_AAD = "mnemo-sync/check".toByteArray()
        private val random = SecureRandom()

        /** New parameters and the key they give for [passphrase]. */
        fun create(passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): Pair<EncryptionParams, SyncKey> {
            require(passphrase.isNotEmpty()) { "The passphrase is empty" }
            require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "iterations must be $MIN_ITERATIONS..$MAX_ITERATIONS" }
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val key = SyncKey.derive(passphrase, salt, iterations)
            val encoder = Base64.getEncoder()
            val params = EncryptionParams(
                iterations = iterations,
                salt = encoder.encodeToString(salt),
                check = encoder.encodeToString(key.encrypt(CHECK_TEXT, CHECK_AAD)),
            )
            return params to key
        }
    }
}
