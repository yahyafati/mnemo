package com.yahyafati.mnemo.core.data.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import com.yahyafati.mnemo.core.sync.oauth.OAuthResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Hands the redirect that the browser sends to the app (received by `OAuthRedirectActivity` in `:app`) to the sign-in
 * that is waiting for it. At most one sign-in waits; a redirect that arrives with none waiting (the process was killed
 * while the user was in the browser, or an app sent us a link out of the blue) is dropped. The `state` check that makes
 * a stray redirect harmless is the sign-in's, not this class's.
 */
object OAuthRedirectHub {
    private val waiting = AtomicReference<CompletableDeferred<Map<String, String>>?>()

    /** Called by the redirect activity. True if a sign-in was waiting for it. */
    fun deliver(redirect: Uri): Boolean {
        val parameters = redirect.queryParameterNames.associateWith { redirect.getQueryParameter(it).orEmpty() }
        return waiting.getAndSet(null)?.complete(parameters) == true
    }

    internal fun expect(answer: CompletableDeferred<Map<String, String>>) {
        waiting.getAndSet(answer)?.cancel()
    }

    internal fun stopWaiting(answer: CompletableDeferred<Map<String, String>>) {
        waiting.compareAndSet(answer, null)
    }
}

/**
 * Google sign-in on a phone (docs/sync/ROADMAP.md S6): the system browser opens the consent page, and Google sends the
 * browser back to a custom-scheme link, `<package name>:/oauth2redirect`, which `OAuthRedirectActivity` receives. This
 * is the scheme Google documents for an Android OAuth client with the custom URI scheme switched on (ADR 0013).
 * No library: the request is one `ACTION_VIEW`. The system browser, not a WebView, because Google refuses embedded ones.
 */
internal class AndroidOAuthAuthorizer(
    private val context: Context,
    private val timeoutMillis: Long = TIMEOUT_MILLIS,
) : OAuthAuthorizer {
    override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse {
        val redirectUri = redirectUri(context.packageName)
        val answer = CompletableDeferred<Map<String, String>>()
        OAuthRedirectHub.expect(answer)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl(redirectUri))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                throw SyncIoException("There is no browser to sign in with", e)
            }
            val parameters = withTimeoutOrNull(timeoutMillis) { answer.await() }
                ?: throw SyncSignInCancelledException("Sign-in took too long")
            return OAuthResponse(redirectUri, parameters)
        } finally {
            OAuthRedirectHub.stopWaiting(answer)
        }
    }

    companion object {
        const val TIMEOUT_MILLIS = 5 * 60_000L

        /** One slash after the colon: Google's rule for a custom-scheme redirect. */
        fun redirectUri(packageName: String) = "$packageName:/oauth2redirect"
    }
}
