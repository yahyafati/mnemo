package com.yahyafati.mnemo.core.sync.google

import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import com.yahyafati.mnemo.core.sync.oauth.OAuthResponse
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PkceTest {
    @Test
    fun theChallengeIsTheRfc7636Example() {
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test
    fun verifiersAndStatesAreUrlSafeUniqueAndLongEnough() {
        val verifiers = List(20) { Pkce.verifier() }
        assertEquals(20, verifiers.toSet().size)
        assertTrue(verifiers.all { it.length in 43..128 && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' } })
        assertNotEquals(Pkce.state(), Pkce.state())
    }
}

class GoogleOAuthTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()

    @Before fun up() = server.start()

    @After fun down() = server.close()

    private val endpoints get() = GoogleEndpoints(
        authorization = server.url("/auth").toString(),
        token = server.url("/token").toString(),
        revoke = server.url("/revoke").toString(),
    )

    private fun oauth(secret: String? = null) = GoogleOAuth(http, GoogleClientConfig("client-1", secret), endpoints)

    private fun tokens(refresh: Boolean = true) = MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
        .body("""{"access_token":"at-1","expires_in":3599,"scope":"$DRIVE_APPDATA_SCOPE","token_type":"Bearer"${if (refresh) ",\"refresh_token\":\"rt-1\"" else ""}}""")
        .build()

    @Test
    fun theAuthorizationUrlAsksForOfflineAccessToTheAppFolderWithPkceAndState() {
        val url = oauth().authorizationUrl("http://127.0.0.1:5000", "state-1", "challenge-1").toHttpUrl()
        assertEquals("/auth", url.encodedPath)
        assertEquals("client-1", url.queryParameter("client_id"))
        assertEquals("http://127.0.0.1:5000", url.queryParameter("redirect_uri"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(DRIVE_APPDATA_SCOPE, url.queryParameter("scope"))
        assertEquals("challenge-1", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("state-1", url.queryParameter("state"))
        assertEquals("offline", url.queryParameter("access_type"))
        assertEquals("consent", url.queryParameter("prompt"))
    }

    @Test
    fun theCodeExchangeSendsTheVerifierAndTheRedirectAndReadsTheTokens() {
        server.enqueue(tokens())
        val granted = oauth().exchange("code-1", "verifier-1", "http://127.0.0.1:5000")
        assertEquals("at-1", granted.accessToken)
        assertEquals("rt-1", granted.refreshToken)
        assertEquals(3599, granted.expiresInSeconds)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val form = request.body!!.utf8().split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals(
            mapOf(
                "client_id" to "client-1",
                "grant_type" to "authorization_code",
                "code" to "code-1",
                "code_verifier" to "verifier-1",
                "redirect_uri" to "http://127.0.0.1:5000",
            ),
            form,
        )
    }

    @Test
    fun theDesktopClientSendsItsSecretAndTheAndroidClientDoesNot() {
        server.enqueue(tokens())
        oauth(secret = "s3cret").refresh("rt-1")
        assertTrue("client_secret=s3cret" in server.takeRequest().body!!.utf8())
        server.enqueue(tokens(refresh = false))
        oauth().refresh("rt-1")
        assertFalse("client_secret" in server.takeRequest().body!!.utf8())
    }

    @Test
    fun aRefreshReturnsAnAccessTokenAndNoNewRefreshToken() {
        server.enqueue(tokens(refresh = false))
        val granted = oauth().refresh("rt-1")
        assertEquals("at-1", granted.accessToken)
        assertNull(granted.refreshToken)
        assertTrue("grant_type=refresh_token" in server.takeRequest().body!!.utf8())
    }

    @Test
    fun aRevokedTokenIsAnAuthError() {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_grant","error_description":"Token has been expired or revoked."}""").build())
        assertFailsWith<SyncAuthException> { oauth().refresh("rt-1") }
    }

    @Test
    fun otherRefusalsAndOutagesAreIoErrorsThatNameNoSecret() {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":"invalid_client"}""").build())
        val refused = assertFailsWith<SyncIoException> { oauth(secret = "s3cret").refresh("rt-1") }
        assertTrue("invalid_client" in refused.message.orEmpty())
        assertFalse("s3cret" in refused.message.orEmpty() || "rt-1" in refused.message.orEmpty())
        server.enqueue(MockResponse.Builder().code(503).build())
        assertFailsWith<SyncIoException> { oauth().refresh("rt-1") }
        server.enqueue(MockResponse.Builder().code(200).body("not json").build())
        assertFailsWith<SyncIoException> { oauth().refresh("rt-1") }
    }

    @Test
    fun noNetworkIsOffline() {
        server.close()
        assertFailsWith<SyncOfflineException> { oauth().refresh("rt-1") }
    }

    @Test
    fun aRedirectFromTheTokenEndpointIsNotFollowed() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/elsewhere").toString()).build())
        assertFailsWith<SyncIoException> { oauth(secret = "s3cret").refresh("rt-1") }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun revokingForgivesATokenGoogleAlreadyForgot() {
        server.enqueue(MockResponse.Builder().code(200).build())
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":"invalid_token"}""").build())
        oauth().revoke("rt-1")
        oauth().revoke("rt-1")
        assertEquals("token=rt-1", server.takeRequest().body!!.utf8())
        server.enqueue(MockResponse.Builder().code(500).build())
        server.takeRequest()
        assertFailsWith<SyncIoException> { oauth().revoke("rt-1") }
    }
}

class GoogleAccessTokensTest {
    private val server = MockWebServer()
    private var now = 1_000_000L
    private var refreshToken: String? = "rt-1"

    @Before fun up() = server.start()

    @After fun down() = server.close()

    private fun tokens() = GoogleAccessTokens(
        GoogleOAuth(OkHttpClient(), GoogleClientConfig("c"), GoogleEndpoints(token = server.url("/token").toString())),
        { refreshToken },
        { now },
    )

    private fun grant(token: String, expiresIn: Int = 3600) = MockResponse.Builder().code(200)
        .body("""{"access_token":"$token","expires_in":$expiresIn,"scope":"$DRIVE_APPDATA_SCOPE"}""").build()

    @Test
    fun aTokenIsKeptUntilItIsAboutToExpire() {
        server.enqueue(grant("a-1", expiresIn = 3600))
        server.enqueue(grant("a-2"))
        val tokens = tokens()
        assertEquals("a-1", tokens.accessToken(false))
        now += 3_000_000
        assertEquals("a-1", tokens.accessToken(false))
        assertEquals(1, server.requestCount)
        now += 600_000 // 3,600 s after the grant, less the one-minute margin
        assertEquals("a-2", tokens.accessToken(false))
    }

    @Test
    fun aRefusedTokenCanBeReplacedAtOnce() {
        server.enqueue(grant("a-1"))
        server.enqueue(grant("a-2"))
        val tokens = tokens()
        assertEquals("a-1", tokens.accessToken(false))
        assertEquals("a-2", tokens.accessToken(true))
        assertEquals("a-2", tokens.accessToken(false))
    }

    @Test
    fun withoutARefreshTokenTheUserIsNotSignedIn() {
        refreshToken = null
        assertFailsWith<SyncAuthException> { tokens().accessToken(false) }
        assertEquals(0, server.requestCount)
    }
}

class GoogleSignInTest {
    private val server = MockWebServer()

    @Before fun up() = server.start()

    @After fun down() = server.close()

    private val oauth get() = GoogleOAuth(
        OkHttpClient(),
        GoogleClientConfig("client-1"),
        GoogleEndpoints(authorization = "https://accounts.example/auth", token = server.url("/token").toString()),
    )

    /** An authorizer that answers with what [respond] makes of the authorization URL the sign-in asked for. */
    private class FakeAuthorizer(val respond: (url: okhttp3.HttpUrl) -> Map<String, String>) : OAuthAuthorizer {
        var asked: okhttp3.HttpUrl? = null

        override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse {
            val url = authorizationUrl("com.example:/cb").toHttpUrl().also { asked = it }
            return OAuthResponse("com.example:/cb", respond(url))
        }
    }

    private fun grant(scope: String = DRIVE_APPDATA_SCOPE, refresh: String? = "rt-1") = MockResponse.Builder().code(200).body(
        """{"access_token":"at","expires_in":3600,"scope":"$scope"${if (refresh != null) ",\"refresh_token\":\"$refresh\"" else ""}}""",
    ).build()

    private fun answer(url: okhttp3.HttpUrl, vararg more: Pair<String, String>) = mapOf("state" to url.queryParameter("state")!!, "code" to "code-1") + more

    @Test
    fun signingInReturnsTheRefreshTokenAfterAnExchangeThatProvesTheVerifier() = runTest {
        server.enqueue(grant())
        val authorizer = FakeAuthorizer { answer(it) }
        assertEquals("rt-1", GoogleSignIn(oauth, authorizer).signIn())
        val form = server.takeRequest().body!!.utf8().split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("code-1", form["code"])
        assertEquals("com.example:/cb", form["redirect_uri"])
        // The verifier sent with the code is the one whose challenge the browser was given.
        assertEquals(authorizer.asked!!.queryParameter("code_challenge"), Pkce.challenge(form.getValue("code_verifier")))
    }

    @Test
    fun anAnswerForAnotherRequestIsRefusedBeforeAnythingIsSent() = runTest {
        val authorizer = FakeAuthorizer { mapOf("state" to "someone-elses", "code" to "code-1") }
        assertFailsWith<SyncAuthException> { GoogleSignIn(oauth, authorizer).signIn() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun anAnswerWithNoStateIsRefused() = runTest {
        assertFailsWith<SyncAuthException> { GoogleSignIn(oauth, FakeAuthorizer { mapOf("code" to "code-1") }).signIn() }
    }

    @Test
    fun refusingAccessIsACancelAndAnythingElseAnAuthError() = runTest {
        assertFailsWith<SyncSignInCancelledException> {
            GoogleSignIn(oauth, FakeAuthorizer { mapOf("state" to it.queryParameter("state")!!, "error" to "access_denied") }).signIn()
        }
        assertFailsWith<SyncAuthException> {
            GoogleSignIn(oauth, FakeAuthorizer { mapOf("state" to it.queryParameter("state")!!, "error" to "invalid_scope") }).signIn()
        }
        assertFailsWith<SyncAuthException> { GoogleSignIn(oauth, FakeAuthorizer { mapOf("state" to it.queryParameter("state")!!) }).signIn() }
    }

    @Test
    fun aGrantWithoutTheDriveFolderIsRefused() = runTest {
        server.enqueue(grant(scope = "openid"))
        assertFailsWith<SyncAuthException> { GoogleSignIn(oauth, FakeAuthorizer { answer(it) }).signIn() }
    }

    @Test
    fun aGrantWithoutARefreshTokenIsRefused() = runTest {
        server.enqueue(grant(refresh = null))
        assertFailsWith<SyncAuthException> { GoogleSignIn(oauth, FakeAuthorizer { answer(it) }).signIn() }
    }

    @Test
    fun aCancelFromThePlatformPassesThrough() = runTest {
        val authorizer = object : OAuthAuthorizer {
            override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse = throw SyncSignInCancelledException()
        }
        assertFailsWith<SyncSignInCancelledException> { GoogleSignIn(oauth, authorizer).signIn() }
    }
}
