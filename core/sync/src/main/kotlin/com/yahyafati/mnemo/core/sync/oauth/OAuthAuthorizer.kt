package com.yahyafati.mnemo.core.sync.oauth

/**
 * What the browser sent back to the app after the user answered Google's consent page: the query parameters of the
 * redirect (`code` and `state`, or `error`) and the [redirectUri] they arrived at, which the token request must repeat.
 */
data class OAuthResponse(val redirectUri: String, val parameters: Map<String, String>)

/**
 * The part of sign-in that is different on every platform (docs/sync/ROADMAP.md S6): send the user to the system
 * browser, and get the redirect back. Android opens the browser and receives a custom-scheme link in an activity; the
 * desktop listens on a loopback port. Everything else (PKCE, the `state` check, the token request) is shared.
 *
 * An implementation never sees or keeps tokens; it only carries one URL out and one redirect back.
 */
interface OAuthAuthorizer {
    /**
     * Opens `authorizationUrl(redirectUri)` in the browser, where [redirectUri] is the one this platform receives, and
     * suspends until the browser is sent there. Throws `SyncSignInCancelledException` if the user gives up (or the
     * wait times out) and `SyncIoException` if no browser can be opened. Cancelling the coroutine stops the wait.
     */
    suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse
}
