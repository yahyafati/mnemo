package com.yahyafati.mnemo.core.sync.google

import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The scope Mnemo asks for: a hidden folder in the user's Drive that only this app can see (ADR 0013). */
const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/**
 * The OAuth client of this build. [clientSecret] is only for the desktop client, which Google's token endpoint does not
 * accept without one (ADR 0013, "Desktop (spike run 2026-10-03)"); the Android client has none. Both come from build
 * configuration, never from the repository.
 */
class GoogleClientConfig(val clientId: String, val clientSecret: String? = null) {
    // A secret must not end up in a log by way of a data class's toString.
    override fun toString() = "GoogleClientConfig(clientId=$clientId)"
}

/** The addresses of Google's services; tests point them at a MockWebServer. */
data class GoogleEndpoints(
    val authorization: String = "https://accounts.google.com/o/oauth2/v2/auth",
    val token: String = "https://oauth2.googleapis.com/token",
    val revoke: String = "https://oauth2.googleapis.com/revoke",
    val api: String = "https://www.googleapis.com/drive/v3",
    val upload: String = "https://www.googleapis.com/upload/drive/v3",
)

/** What the token endpoint granted. [refreshToken] is only there after the first sign-in. */
class GoogleTokens(val accessToken: String, val refreshToken: String?, val expiresInSeconds: Long, val scope: String) {
    override fun toString() = "GoogleTokens(expiresInSeconds=$expiresInSeconds, scope=$scope)"
}

/**
 * Google's OAuth 2.0 endpoints for an installed app: the authorization URL, the code exchange, refresh and revoke.
 * Calls block (run them on an IO dispatcher). Failures are `SyncException`s: no network is [SyncOfflineException], a
 * code or refresh token Google no longer accepts is [SyncAuthException]. No token or secret ever goes into a message.
 */
class GoogleOAuth(
    http: OkHttpClient,
    private val config: GoogleClientConfig,
    private val endpoints: GoogleEndpoints = GoogleEndpoints(),
) {
    // A token must never be sent to wherever a redirect points.
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** The page the user is sent to. `access_type=offline` + `prompt=consent` is what makes Google return a refresh token. */
    fun authorizationUrl(redirectUri: String, state: String, challenge: String): String =
        endpoints.authorization.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", config.clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", DRIVE_APPDATA_SCOPE)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .build()
            .toString()

    /** Trades the authorization [code] for tokens. */
    fun exchange(code: String, verifier: String, redirectUri: String): GoogleTokens = tokenRequest(
        "grant_type" to "authorization_code",
        "code" to code,
        "code_verifier" to verifier,
        "redirect_uri" to redirectUri,
    )

    /** A new access token for [refreshToken]. [SyncAuthException] once Google says the token was revoked or has expired. */
    fun refresh(refreshToken: String): GoogleTokens = tokenRequest(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
    )

    /** Tells Google to forget [token] (a refresh token ends the whole grant). */
    fun revoke(token: String) {
        val request = Request.Builder().url(endpoints.revoke).post(FormBody.Builder().add("token", token).build()).build()
        val (code, body) = send(request)
        // An already revoked or expired token is 400: it is what we wanted.
        if (code !in 200..299 && code != 400) throw failure(code, body)
    }

    private fun tokenRequest(vararg fields: Pair<String, String>): GoogleTokens {
        val form = FormBody.Builder().add("client_id", config.clientId)
        config.clientSecret?.let { form.add("client_secret", it) }
        fields.forEach { (name, value) -> form.add(name, value) }
        val (code, body) = send(Request.Builder().url(endpoints.token).post(form.build()).build())
        if (code !in 200..299) throw failure(code, body)
        val response = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw SyncIoException("Google's token response couldn't be read", e)
        }
        val access = response["access_token"]?.jsonPrimitive?.contentOrNull ?: throw SyncIoException("Google's token response had no access token")
        return GoogleTokens(
            accessToken = access,
            refreshToken = response["refresh_token"]?.jsonPrimitive?.contentOrNull,
            expiresInSeconds = response["expires_in"]?.jsonPrimitive?.longOrNull ?: 3_600,
            scope = response["scope"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun send(request: Request): Pair<Int, String> = try {
        http.newCall(request).execute().use { it.code to it.body.string() }
    } catch (e: IOException) {
        throw SyncOfflineException("Couldn't reach Google", e)
    }

    private fun failure(code: Int, body: String): Exception {
        val error = try {
            json.parseToJsonElement(body).jsonObject.errorName()
        } catch (_: Exception) {
            null
        }
        return when {
            error == "invalid_grant" -> SyncAuthException("Google no longer accepts this sign-in (revoked or expired)")
            code == 429 || code >= 500 -> SyncIoException("Google's sign-in service is unavailable ($code)")
            else -> SyncIoException("Google refused the sign-in request: ${error ?: code}")
        }
    }

    private fun JsonObject.errorName(): String? = this["error"]?.jsonPrimitive?.contentOrNull
}
