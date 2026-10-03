package com.yahyafati.mnemo.core.sync.google

import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gives a Drive call its access token. Blocking: calls happen on an IO thread. */
fun interface AccessTokenSource {
    /** A token that is good for a while. [forceRefresh]: the last one was refused, so get a new one even if it hasn't expired. */
    fun accessToken(forceRefresh: Boolean): String
}

/**
 * Access tokens made from a refresh token with [GoogleOAuth.refresh] and kept in memory only (the refresh token is the
 * caller's to keep safe). A missing refresh token is [SyncAuthException]: the user has to sign in.
 */
class GoogleAccessTokens(
    private val oauth: GoogleOAuth,
    private val refreshToken: () -> String?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AccessTokenSource {
    private var token: String? = null
    private var expiresAt = 0L

    @Synchronized
    override fun accessToken(forceRefresh: Boolean): String {
        val cached = token
        if (!forceRefresh && cached != null && nowMillis() < expiresAt - EXPIRY_MARGIN_MS) return cached
        val refresh = refreshToken() ?: throw SyncAuthException("Not signed in to Google")
        val granted = oauth.refresh(refresh)
        token = granted.accessToken
        expiresAt = nowMillis() + granted.expiresInSeconds * 1_000
        return granted.accessToken
    }

    private companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}

/**
 * The sign-in dance (docs/sync/ROADMAP.md S6): PKCE, the browser through the platform's [OAuthAuthorizer], the `state`
 * check, and the code exchange. What comes back is a refresh token for the caller to keep; this class keeps nothing.
 */
class GoogleSignIn(private val oauth: GoogleOAuth, private val authorizer: OAuthAuthorizer) {
    /**
     * Signs the user in and returns the **refresh token**. [SyncSignInCancelledException] if they closed the browser or
     * refused; [SyncAuthException] if the answer can't be trusted or Google granted less than the Drive folder.
     */
    suspend fun signIn(): String {
        val verifier = Pkce.verifier()
        val state = Pkce.state()
        val challenge = Pkce.challenge(verifier)
        val response = authorizer.authorize { redirect -> oauth.authorizationUrl(redirect, state, challenge) }
        val parameters = response.parameters
        // Before anything else: a response that isn't an answer to our request is not ours to read.
        if (parameters["state"] != state) throw SyncAuthException("The sign-in answer doesn't belong to this request")
        parameters["error"]?.let { error ->
            if (error == "access_denied") throw SyncSignInCancelledException("Access to Google Drive was refused")
            throw SyncAuthException("Google refused the sign-in: $error")
        }
        val code = parameters["code"] ?: throw SyncAuthException("The sign-in answer had no code")
        val tokens = withContext(Dispatchers.IO) { oauth.exchange(code, verifier, response.redirectUri) }
        // Google lets people untick what an app asked for.
        if (DRIVE_APPDATA_SCOPE !in tokens.scope.split(' ')) throw SyncAuthException("Mnemo wasn't given access to its folder in Google Drive")
        return tokens.refreshToken ?: throw SyncAuthException("Google didn't return a token to stay signed in with")
    }
}
