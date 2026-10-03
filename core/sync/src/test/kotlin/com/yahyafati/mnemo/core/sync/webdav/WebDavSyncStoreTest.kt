package com.yahyafati.mnemo.core.sync.webdav

import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.sync.DEVICE_A
import com.yahyafati.mnemo.core.sync.FAST_ITERATIONS
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.SyncStoreContract
import com.yahyafati.mnemo.core.sync.sampleChanges
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake WebDAV server on a MockWebServer, and stores pointed at it. The address is plain HTTP on localhost, which the rule allows. */
internal class WebDavFixture(val dav: FakeWebDavServer = FakeWebDavServer()) {
    val server = MockWebServer()
    val pauses = ArrayList<Long>()
    var password: String? = dav.password
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    val address: String get() = server.url(dav.folderPath).toString()

    fun newStore(url: String = address, user: String = dav.user): WebDavSyncStore =
        WebDavSyncStore(http, url, user, { password }, pause = { pauses += it })

    fun start() {
        server.dispatcher = dav
        server.start()
    }

    fun stop() = server.close()
}

internal class WebDavSyncStoreContractTest : SyncStoreContract() {
    private val fixture = WebDavFixture()
    override val store: SyncStore by lazy { fixture.newStore() }

    @Before fun up() = fixture.start()

    @After fun down() = fixture.stop()
}

class WebDavSyncStoreTest {
    private val fixture = WebDavFixture()
    private val dav get() = fixture.dav

    @Before fun up() = fixture.start()

    @After fun down() = fixture.stop()

    private val hash = "a".repeat(64)

    @Test
    fun filesLiveFlatInTheOneFolderAndAreClaimedWithIfNoneMatch() {
        val store = fixture.newStore()
        store.write("devices/$DEVICE_A/changes/1.mnc", byteArrayOf(1))
        store.write("media/$hash", byteArrayOf(2))
        assertEquals(setOf("devices__${DEVICE_A}__changes__1.mnc", "media__$hash"), dav.files.keys)
        val put = dav.requests.indexOfFirst { it.startsWith("PUT") }
        assertEquals("*", dav.headers[put]["If-None-Match"])
        // A replacement has no condition.
        store.overwrite("devices/$DEVICE_A/device.json", byteArrayOf(3))
        store.overwrite("devices/$DEVICE_A/device.json", byteArrayOf(4))
        val last = dav.requests.indexOfLast { it.startsWith("PUT") }
        assertNull(dav.headers[last]["If-None-Match"])
    }

    @Test
    fun anExistingNameIsAlreadyExistsAndKeepsItsContents() {
        val store = fixture.newStore()
        store.write("sync.json", byteArrayOf(1))
        assertFailsWith<SyncAlreadyExistsException> { store.write("sync.json", byteArrayOf(2)) }
        assertContentEquals(byteArrayOf(1), store.read("sync.json"))
    }

    @Test
    fun aServerThatIgnoresIfNoneMatchStillWrites() {
        dav.ignoresIfNoneMatch = true
        val store = fixture.newStore()
        store.write("sync.json", byteArrayOf(1))
        store.write("sync.json", byteArrayOf(2))
        assertContentEquals(byteArrayOf(2), store.read("sync.json"))
    }

    @Test
    fun theCredentialsAreSentAsBasicAuthWithEveryRequest() {
        val store = fixture.newStore()
        store.write("sync.json", byteArrayOf(1))
        store.list("")
        store.read("sync.json")
        store.delete("sync.json")
        assertTrue(dav.headers.isNotEmpty())
        dav.headers.forEach { assertTrue(it["Authorization"]!!.startsWith("Basic "), "every request carries the credentials") }
    }

    @Test
    fun listingReadsNextcloudStyleAnswersWhateverThePrefixOrEncoding() {
        for (prefix in listOf("d", "D", "ns0")) {
            dav.prefix = prefix
            dav.files.clear()
            dav.files["sync.json"] = byteArrayOf(1)
            dav.files["media__$hash"] = byteArrayOf(1)
            assertEquals(listOf("media/$hash", "sync.json"), fixture.newStore().list(""), "prefix $prefix")
        }
    }

    @Test
    fun listingLeavesOutForeignFilesAndSubfolders() {
        dav.files["sync.json"] = byteArrayOf(1)
        dav.files["holiday photo.jpg"] = byteArrayOf(1)
        dav.files["notes.txt"] = byteArrayOf(1)
        dav.files[".hidden"] = byteArrayOf(1)
        assertEquals(listOf("sync.json"), fixture.newStore().list(""))
    }

    @Test
    fun listingWorksWhenTheServerSendsFullAddressesOnAnotherHost() {
        dav.absoluteHrefs = "https://cloud.example.org"
        dav.files["sync.json"] = byteArrayOf(1)
        assertEquals(listOf("sync.json"), fixture.newStore().list(""))
    }

    @Test
    fun aTypedAddressWithoutTheTrailingSlashWorks() {
        dav.files["sync.json"] = byteArrayOf(1)
        assertEquals(listOf("sync.json"), fixture.newStore(fixture.address.trimEnd('/')).list(""))
    }

    @Test
    fun aWrongPasswordIsAuthAndAMissingOneToo() {
        fixture.password = "wrong"
        assertFailsWith<SyncAuthException> { fixture.newStore().list("") }
        assertFailsWith<SyncAuthException> { fixture.newStore().write("sync.json", byteArrayOf(1)) }
        fixture.password = null
        assertFailsWith<SyncAuthException> { fixture.newStore().list("") }
        // No request was sent without a password.
        assertEquals(2, dav.requests.size)
    }

    @Test
    fun aForbiddenAnswerIsAuth() {
        dav.injected.add(MockResponse.Builder().code(403).build())
        assertFailsWith<SyncAuthException> { fixture.newStore().read("sync.json") }
    }

    @Test
    fun aDeletedFolderIsNotFoundForListingAndWriting() {
        dav.folderExists = false
        val store = fixture.newStore()
        assertFailsWith<SyncNotFoundException> { store.list("") }
        // A server says 409 to a PUT whose folder is missing; the store never makes the folder by itself.
        assertFailsWith<SyncIoException> { store.write("sync.json", byteArrayOf(1)) }
        assertFalse(dav.folderExists)
    }

    @Test
    fun aFullServerIsQuota() {
        dav.injected.add(MockResponse.Builder().code(507).build())
        assertFailsWith<SyncQuotaException> { fixture.newStore().write("sync.json", byteArrayOf(1)) }
    }

    @Test
    fun rateLimitsAndMaintenanceAreRetriedWithAGrowingPause() {
        dav.files["sync.json"] = byteArrayOf(7)
        dav.injected.add(MockResponse.Builder().code(429).build())
        dav.injected.add(MockResponse.Builder().code(503).build())
        assertContentEquals(byteArrayOf(7), fixture.newStore().read("sync.json"))
        assertEquals(listOf(500L, 1000L), fixture.pauses)
    }

    @Test
    fun aServerThatStaysUnavailableIsAnIoError() {
        repeat(10) { dav.injected.add(MockResponse.Builder().code(503).build()) }
        assertFailsWith<SyncIoException> { fixture.newStore().read("sync.json") }
        assertEquals(4, fixture.pauses.size)
    }

    @Test
    fun anUnreachableServerIsOffline() {
        fixture.stop()
        assertFailsWith<SyncOfflineException> { fixture.newStore().list("") }
    }

    @Test
    fun aRedirectIsNeverFollowedAndNothingIsSentToItsTarget() {
        dav.injected.add(MockResponse.Builder().code(301).addHeader("Location", "https://elsewhere.example/dav/").build())
        assertFailsWith<SyncIoException> { fixture.newStore().list("") }
        assertEquals(1, dav.requests.size)
    }

    @Test
    fun aWebPageThatIsNotWebDavIsAnIoError() {
        dav.injected.add(MockResponse.Builder().code(200).body("<html><body>Welcome</body></html>").build())
        assertFailsWith<SyncIoException> { fixture.newStore().list("") }
        dav.injected.add(MockResponse.Builder().code(207).body("<html>nope</html>").build())
        assertFailsWith<SyncIoException> { fixture.newStore().list("") }
    }

    @Test
    fun aDocumentThatDeclaresEntitiesIsRefused() {
        dav.injected.add(
            MockResponse.Builder().code(207).body(
                """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><d:multistatus xmlns:d="DAV:"><d:response><d:href>&e;</d:href></d:response></d:multistatus>""",
            ).build(),
        )
        assertFailsWith<SyncIoException> { fixture.newStore().list("") }
    }

    // --- the folder: test connection and create ------------------------------------------------------------------------

    @Test
    fun probeSaysWhetherTheFolderIsThereWithoutChangingAnything() {
        assertEquals(WebDavFolder.Exists, fixture.newStore().probe())
        dav.folderExists = false
        assertEquals(WebDavFolder.Missing, fixture.newStore().probe())
        assertFalse(dav.folderExists)
        assertTrue(dav.requests.none { it.startsWith("MKCOL") })
        assertEquals("0", dav.headers[0]["Depth"])
    }

    @Test
    fun probeWithAWrongPasswordIsAuth() {
        fixture.password = "wrong"
        assertFailsWith<SyncAuthException> { fixture.newStore().probe() }
    }

    @Test
    fun ensureFolderMakesAMissingFolderOnceAndLeavesAnExistingOne() {
        dav.folderExists = false
        val store = fixture.newStore()
        store.ensureFolder()
        assertTrue(dav.folderExists)
        assertEquals(1, dav.requests.count { it.startsWith("MKCOL") })
        store.ensureFolder()
        assertEquals(1, dav.requests.count { it.startsWith("MKCOL") })
    }

    @Test
    fun ensureFolderWithoutAParentIsNotFound() {
        dav.folderExists = false
        dav.parentExists = false
        assertFailsWith<SyncNotFoundException> { fixture.newStore().ensureFolder() }
    }

    // --- addresses -----------------------------------------------------------------------------------------------------

    @Test
    fun addressesAreHttpsOrLocalHttpWithoutCredentialsInThem() {
        assertEquals(AiEndpoint.Check.Ok, WebDavUrl.check("https://cloud.example.org/remote.php/dav/files/alice/Mnemo"))
        assertEquals(AiEndpoint.Check.Ok, WebDavUrl.check("http://192.168.1.20:8080/dav/"))
        assertEquals(AiEndpoint.Check.Ok, WebDavUrl.check("http://nas.local/dav"))
        assertEquals(AiEndpoint.Check.Insecure(hostIsLocal = false), WebDavUrl.check("http://cloud.example.org/dav"))
        assertEquals(AiEndpoint.Check.Invalid, WebDavUrl.check("ftp://cloud.example.org/dav"))
        assertEquals(AiEndpoint.Check.Invalid, WebDavUrl.check("cloud.example.org/dav"))
        assertEquals(AiEndpoint.Check.Invalid, WebDavUrl.check("https://alice:secret@cloud.example.org/dav"))
        assertEquals(AiEndpoint.Check.Invalid, WebDavUrl.check("https://cloud.example.org/dav?x=1"))
        assertEquals("https://cloud.example.org/dav/", WebDavUrl.normalize("  https://cloud.example.org/dav ")?.toString())
        assertNull(WebDavUrl.normalize("http://cloud.example.org/dav"))
    }

    @Test
    fun theStoreRefusesAnInsecureAddressItself() {
        assertFailsWith<IllegalArgumentException> { fixture.newStore("http://cloud.example.org/dav/") }
    }

    // --- everything on top of it ---------------------------------------------------------------------------------------

    @Test
    fun anEncryptedLocationWorksOnWebDavEndToEnd() {
        val remote = SyncRemote.create(fixture.newStore(), "collection-1", 1_000, "correct horse".toCharArray(), FAST_ITERATIONS)
        val changes = sampleChanges()
        assertEquals(listOf(1L), remote.writeChanges(DEVICE_A, 1, changes))
        val bytes = ByteArray(2_000) { it.toByte() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        remote.writeMedia(hash, bytes)

        // Another device, another store on the same server, joins with the passphrase.
        val other = SyncRemote.open(fixture.newStore(), "correct horse".toCharArray())
        assertEquals(listOf(1L), other.listChangeSeqs(DEVICE_A))
        assertEquals(changes, other.readChanges(DEVICE_A, 1).changes)
        assertEquals(listOf(hash), other.listMedia())
        assertContentEquals(bytes, other.readMedia(hash))
        // The server holds only sealed files.
        assertTrue(dav.files.values.none { String(it, Charsets.ISO_8859_1).contains("math calc") })
    }
}
