package com.yahyafati.mnemo.core.sync.google

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636) and the `state` value of an OAuth sign-in. Google requires PKCE of native apps. */
object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** A new code verifier: 32 random bytes, 43 characters of base64url. */
    fun verifier(): String = token(32)

    /** The `S256` challenge for [verifier]. */
    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** An unguessable value that ties the redirect to the request that started it. */
    fun state(): String = token(24)

    private fun token(bytes: Int): String = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))
}
