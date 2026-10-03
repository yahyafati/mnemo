package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.security.SecretIds
import com.yahyafati.mnemo.core.security.StoredSecret
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Credentials
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
 * The WebDAV account behind the location (docs/sync/ROADMAP.md S7): the password is kept only in [FileSecretStore], is
 * checked before it is kept, and goes when the device stops syncing. The server is a MockWebServer that answers `PROPFIND`,
 * `MKCOL` and file requests for one folder.
 */
class WebDavAccessTest : PlatformTest() {
    private val server = MockWebServer()
    private val directories = TestAppDirectories()
    private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)
    private val requests = ArrayList<String>()
    private var folderExists = true
    private var folder = "/dav/Mnemo/"

    @Before
    fun up() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath}"
                if (request.headers["Authorization"] != Credentials.basic("alice", "right", Charsets.UTF_8)) {
                    return MockResponse.Builder().code(401).build()
                }
                return when (request.method) {
                    "PROPFIND" -> if (folderExists) {
                        MockResponse.Builder().code(207).body("""<d:multistatus xmlns:d="DAV:"><d:response><d:href>$folder</d:href></d:response></d:multistatus>""").build()
                    } else {
                        MockResponse.Builder().code(404).build()
                    }
                    "MKCOL" -> {
                        folderExists = true
                        MockResponse.Builder().code(201).build()
                    }
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @After
    fun down() {
        server.close()
        directories.delete()
    }

    private fun access() = WebDavAccess(secrets, OkHttpClient(), Dispatchers.Unconfined)

    private fun address() = server.url(folder).toString().trimEnd('/')

    @Test
    fun testingSaysWhetherTheFolderIsThereAndKeepsAndChangesNothing() = runTest {
        assertEquals(WebDavTestResult.FolderFound, access().test(address(), "alice", "right"))
        folderExists = false
        assertEquals(WebDavTestResult.FolderWillBeCreated, access().test(address(), "alice", "right"))
        assertFalse(folderExists)
        assertTrue(requests.none { it.startsWith("MKCOL") })
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.WEBDAV_PASSWORD))
    }

    @Test
    fun aWrongPasswordIsRefusedByTestAndConnectAndNothingIsKept() = runTest {
        assertFailsWith<SyncAuthException> { access().test(address(), "alice", "wrong") }
        assertFailsWith<SyncAuthException> { access().connect(address(), "alice", "wrong") }
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.WEBDAV_PASSWORD))
    }

    @Test
    fun connectingMakesTheFolderKeepsThePasswordAndNamesTheBackend() = runTest {
        folderExists = false
        val backend = access().connect("  ${address()}  ", " alice ", "right")

        assertTrue(folderExists)
        assertEquals(SyncBackend.WebDav(server.url(folder).toString(), "alice"), backend)
        assertEquals(StoredSecret.Present("right"), secrets.get(SecretIds.WEBDAV_PASSWORD))
        // The password is a reserved id: clearing the AI keys of deleted providers must not take it.
        assertTrue(SecretIds.WEBDAV_PASSWORD in SecretIds.RESERVED)
    }

    @Test
    fun aMissingParentIsNotFoundAndKeepsNothing() = runTest {
        folderExists = false
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "PROPFIND" -> MockResponse.Builder().code(404).build()
                else -> MockResponse.Builder().code(409).build()
            }.let { if (request.headers["Authorization"] == Credentials.basic("alice", "right", Charsets.UTF_8)) it else MockResponse.Builder().code(401).build() }
        }
        assertFailsWith<SyncNotFoundException> { access().connect(address(), "alice", "right") }
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.WEBDAV_PASSWORD))
    }

    @Test
    fun anAddressThatPlainHttpMayNotUseIsRefused() = runTest {
        assertFailsWith<IllegalArgumentException> { access().connect("http://cloud.example.org/dav", "alice", "right") }
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.WEBDAV_PASSWORD))
    }

    @Test
    fun theStoreUsesTheKeptPasswordAndWithoutOneItIsAnAuthProblem() = runTest {
        val backend = access().connect(address(), "alice", "right")
        assertEquals(emptyList(), access().openStore(backend).list(""))

        secrets.remove(SecretIds.WEBDAV_PASSWORD)
        assertFailsWith<SyncAuthException> { access().openStore(backend).list("") }

        // A new password mends it.
        access().connect(address(), "alice", "right")
        assertEquals(emptyList(), access().openStore(backend).list(""))
    }

    @Test
    fun givingTheLocationUpForgetsThePassword() = runTest {
        val access = access()
        val backend = access.connect(address(), "alice", "right")
        DocumentSyncStores(FakeDocumentAccess(), GoogleDriveAccess(NoGoogleClientSource, NoAuthorizer, secrets, OkHttpClient(), Dispatchers.Unconfined), access).release(backend)
        assertEquals(StoredSecret.Missing, secrets.get(SecretIds.WEBDAV_PASSWORD))
    }

    @Test
    fun anUnusableStoredAddressIsAnIoProblemNotACrash() = runTest {
        val failure = runCatching { access().openStore(SyncBackend.WebDav("http://cloud.example.org/dav/", "alice")) }.exceptionOrNull()
        assertIs<com.yahyafati.mnemo.core.sync.SyncIoException>(failure)
    }

    @Test
    fun twoWebDavLocationsShareTheOnePasswordSoMovingBetweenThemKeepsIt() {
        val a = SyncBackend.WebDav("https://a.example/dav/", "alice")
        val b = SyncBackend.WebDav("https://b.example/dav/", "alice")
        assertTrue(a.sharesAccessWith(b))
        assertFalse(a.sharesAccessWith(SyncBackend.GoogleDrive))
        assertFalse(a.sharesAccessWith(SyncBackend.Folder("/x")))
        assertFalse(SyncBackend.Folder("/x").sharesAccessWith(SyncBackend.Folder("/y")))
        assertTrue(SyncBackend.GoogleDrive.sharesAccessWith(SyncBackend.GoogleDrive))
    }

    private companion object {
        val NoGoogleClientSource = GoogleClientConfigSource { null }
        val NoAuthorizer = object : com.yahyafati.mnemo.core.sync.oauth.OAuthAuthorizer {
            override suspend fun authorize(authorizationUrl: (redirectUri: String) -> String): com.yahyafati.mnemo.core.sync.oauth.OAuthResponse =
                throw UnsupportedOperationException()
        }
    }
}
