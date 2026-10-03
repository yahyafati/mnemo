package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.security.SecretIds
import com.yahyafati.mnemo.core.security.StoredSecret
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.google.DRIVE_APPDATA_SCOPE
import com.yahyafati.mnemo.core.sync.google.GoogleEndpoints
import com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer
import com.yahyafati.mnemo.core.sync.oauth.OAuthResponse
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The Google account behind the Drive location (docs/sync/ROADMAP.md S6): sign-in keeps only a refresh token, in
 * [FileSecretStore]; the store it hands out refreshes access tokens from it; signing out forgets it and tells Google.
 * Google is a MockWebServer that behaves like the token endpoint and an empty app-data folder.
 */
class GoogleDriveAccessTest : PlatformTest() {
    private val server = MockWebServer()
    private val directories = TestAppDirectories()
    private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)

    /** Form bodies the token and revoke endpoints received. */
    private val tokenForms = ArrayList<String>()
    private val revokes = ArrayList<String>()
    private var refreshAnswer = 200

    @Before
    fun up() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/token" -> {
                    val form = request.body!!.utf8()
                    tokenForms += form
                    if ("grant_type=refresh_token" in form && refreshAnswer != 200) {
                        MockResponse.Builder().code(refreshAnswer).body("""{"error":"invalid_grant"}""").build()
                    } else {
                        MockResponse.Builder().code(200).body(
                            """{"access_token":"at-${tokenForms.size}","expires_in":3600,"scope":"$DRIVE_APPDATA_SCOPE","refresh_token":"rt-new"}""",
                        ).build()
                    }
                }
                "/revoke" -> {
                    revokes += request.body!!.utf8()
                    MockResponse.Builder().code(200).build()
                }
                "/drive/v3/files" -> {
                    // The Authorization header proves which access token the store used.
                    tokenForms += "list:${request.headers["Authorization"]}"
                    MockResponse.Builder().code(200).body("""{"files":[]}""").build()
                }
                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @After
    fun down() {
        server.close()
        directories.delete()
    }

    private fun endpoints() = GoogleEndpoints(
        authorization = "https://accounts.example/auth",
        token = server.url("/token").toString(),
        revoke = server.url("/revoke").toString(),
        api = server.url("/drive/v3").toString().trimEnd('/'),
        upload = server.url("/upload/drive/v3").toString().trimEnd('/'),
    )

    private class Answering(val parameters: (state: String) -> Map<String, String>) : OAuthAuthorizer {
        override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse {
            val url = authorizationUrl("com.example:/cb")
            val state = Regex("state=([^&]+)").find(url)!!.groupValues[1]
            return OAuthResponse("com.example:/cb", parameters(state))
        }
    }

    private val approving = Answering { mapOf("state" to it, "code" to "code-1") }

    private fun access(authorizer: OAuthAuthorizer = approving, client: GoogleClient? = GoogleClient("client-1")) =
        GoogleDriveAccess(GoogleClientConfigSource { client }, authorizer, secrets, OkHttpClient(), Dispatchers.Unconfined, endpoints())

    @Test
    fun withoutAGoogleClientTheBuildDoesNotOfferDrive() = runTest {
        val access = access(client = null)
        assertFalse(access.isAvailable)
        assertFailsWith<SyncAuthException> { access.openStore() }
        assertFalse(DocumentSyncStores(com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess(), access).googleDriveAvailable)
    }

    @Test
    fun signingInKeepsTheRefreshTokenInTheSecretStoreAndNothingElse() = runTest {
        access().signIn()
        assertEquals(StoredSecret.Present("rt-new"), secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN))
        // The refresh token is a reserved id: clearing the AI keys of deleted providers must not take it.
        assertTrue(SecretIds.GOOGLE_REFRESH_TOKEN in SecretIds.RESERVED)
        // The code exchange is the only call: no access token was kept or even asked for.
        assertEquals(1, tokenForms.size)
    }

    @Test
    fun aCancelledOrRefusedSignInKeepsNothing() = runTest {
        val cancelled = Answering { mapOf("state" to it, "error" to "access_denied") }
        assertFailsWith<SyncSignInCancelledException> { access(cancelled).signIn() }
        val forged = Answering { mapOf("state" to "not-ours", "code" to "c") }
        assertFailsWith<SyncAuthException> { access(forged).signIn() }
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN))
        assertTrue(tokenForms.isEmpty())
    }

    @Test
    fun theStoreAsksGoogleForAnAccessTokenFromTheStoredRefreshTokenAndReusesIt() = runTest {
        val access = access()
        access.signIn()
        tokenForms.clear()

        val store = access.openStore()
        store.list("")
        store.list("")

        // One refresh for two calls, and the Drive calls carry what it returned.
        assertEquals(1, tokenForms.count { "grant_type=refresh_token" in it && "refresh_token=rt-new" in it })
        assertEquals(2, tokenForms.count { it.startsWith("list:Bearer at-") })
        // Another store from the same access shares the token.
        access.openStore().list("")
        assertEquals(1, tokenForms.count { "grant_type=refresh_token" in it })
    }

    @Test
    fun aRevokedRefreshTokenIsTheAuthError() = runTest {
        val access = access()
        access.signIn()
        refreshAnswer = 400
        assertFailsWith<SyncAuthException> { access.openStore().list("") }
    }

    @Test
    fun withoutASignInTheStoreIsTheAuthError() = runTest {
        assertFailsWith<SyncAuthException> { access().openStore().list("") }
        assertTrue(tokenForms.isEmpty())
    }

    @Test
    fun signingOutForgetsTheTokenAndTellsGoogle() = runTest {
        val access = access()
        access.signIn()
        access.signOut()
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN))
        assertEquals(listOf("token=rt-new"), revokes)
        // Signing out twice is fine.
        access.signOut()
        assertEquals(1, revokes.size)
    }

    @Test
    fun signingOutWorksWhenGoogleCantBeReached() = runTest {
        val access = access()
        access.signIn()
        server.close()
        access.signOut()
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.GOOGLE_REFRESH_TOKEN))
    }

    @Test
    fun theSignInStatesWhatItAskedFor() = runTest {
        var url = ""
        val spying = object : OAuthAuthorizer {
            override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): OAuthResponse {
                url = authorizationUrl("com.example:/cb")
                throw SyncSignInCancelledException()
            }
        }
        assertIs<SyncSignInCancelledException>(runCatching { access(spying).signIn() }.exceptionOrNull())
        assertTrue("client_id=client-1" in url && "code_challenge_method=S256" in url && "scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fdrive.appdata" in url, url)
    }
}
