package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.security.SecretIds
import com.yahyafati.mnemo.core.security.SecretStore
import com.yahyafati.mnemo.core.security.StoredSecret
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.google.GoogleAccessTokens
import com.yahyafati.mnemo.core.sync.google.GoogleClientConfig
import com.yahyafati.mnemo.core.sync.google.GoogleDriveSyncStore
import com.yahyafati.mnemo.core.sync.google.GoogleEndpoints
import com.yahyafati.mnemo.core.sync.google.GoogleOAuth
import com.yahyafati.mnemo.core.sync.google.GoogleSignIn
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * The Google OAuth client this build signs in with, or null when it was built without one (a fork, F-Droid): Drive is
 * then not offered. It comes from build configuration (`MNEMO_GOOGLE_CLIENT_ID`, and for the desktop also
 * `MNEMO_GOOGLE_DESKTOP_CLIENT_SECRET`), never from the repository; each launcher binds its own.
 */
fun interface GoogleClientConfigSource {
    fun config(): GoogleClient?
}

/** A Google OAuth client: its id and, for the desktop's, the secret Google's token endpoint wants with it (ADR 0013). Never logged. */
class GoogleClient(val clientId: String, val clientSecret: String? = null) {
    override fun toString() = "GoogleClient(clientId=$clientId)"
}

/** What a build without a Google client binds. */
val NoGoogleClient = GoogleClientConfigSource { null }

/**
 * Everything about the Google account of the Drive sync location (docs/sync/ROADMAP.md S6): signing in through the
 * platform's [OAuthAuthorizer], keeping the **refresh token** in [SecretStore] (never in Room, a backup or a log),
 * handing out stores whose access tokens come from it, and signing out. The access token only ever lives in memory.
 * A token that Google revoked or expired is the "auth" error of the store: the user signs in again.
 */
internal class GoogleDriveAccess(
    private val configSource: GoogleClientConfigSource,
    private val authorizer: OAuthAuthorizer,
    private val secrets: SecretStore,
    private val http: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoints: GoogleEndpoints = GoogleEndpoints(),
) {
    private var tokens: GoogleAccessTokens? = null

    val isAvailable: Boolean get() = configSource.config() != null

    private fun oauth(): GoogleOAuth {
        val client = checkNotNull(configSource.config()) { "Google Drive isn't available in this build" }
        return GoogleOAuth(http, GoogleClientConfig(client.clientId, client.clientSecret), endpoints)
    }

    /** Signs in and keeps the new refresh token, replacing the old one (and its cached access token). */
    suspend fun signIn() {
        val refresh = GoogleSignIn(oauth(), authorizer).signIn()
        secrets.put(SecretIds.GOOGLE_REFRESH_TOKEN, refresh)
        synchronized(this) { tokens = null }
    }

    /**
     * Forgets the sign-in: the token goes first, so a failure to reach Google can't leave this device signed in, and
     * then Google is told to revoke it, which can fail without mattering.
     */
    suspend fun signOut() {
        val refresh = (secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN) as? StoredSecret.Present)?.value
        secrets.remove(SecretIds.GOOGLE_REFRESH_TOKEN)
        synchronized(this) { tokens = null }
        if (refresh == null || !isAvailable) return
        try {
            withContext(ioDispatcher) { oauth().revoke(refresh) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SyncException) {
            // Revoking is a courtesy; the token is gone from this device either way.
        }
    }

    /** A store on the signed-in account. It works only after [signIn]; before that its calls are the auth error. */
    fun openStore(): SyncStore {
        if (!isAvailable) throw SyncAuthException("Google Drive isn't available in this build")
        return GoogleDriveSyncStore(http, accessTokens(), endpoints)
    }

    @Synchronized
    private fun accessTokens(): GoogleAccessTokens = tokens ?: GoogleAccessTokens(oauth(), ::storedRefreshToken).also { tokens = it }

    /** Called by a store, on an IO thread, when it needs a new access token. */
    private fun storedRefreshToken(): String? = runBlocking {
        (secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN) as? StoredSecret.Present)?.value
    }
}
