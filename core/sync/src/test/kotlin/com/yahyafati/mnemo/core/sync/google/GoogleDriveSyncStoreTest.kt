package com.yahyafati.mnemo.core.sync.google

import com.yahyafati.mnemo.core.sync.DEVICE_A
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncIoException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncStoreContract
import com.yahyafati.mnemo.core.sync.sampleChanges
import com.yahyafati.mnemo.core.sync.FAST_ITERATIONS
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
import kotlin.test.assertTrue

/** A fake Drive on a MockWebServer, a token source and stores pointed at it. */
internal class DriveFixture {
    val drive = FakeDriveServer()
    val server = MockWebServer()
    val pauses = ArrayList<Long>()

    /** Whether each token request was a forced refresh. */
    val tokenRequests = ArrayList<Boolean>()
    private var tokenCounter = 1

    private val tokens = AccessTokenSource { force ->
        tokenRequests += force
        if (force) tokenCounter++
        "token-$tokenCounter"
    }

    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()

    fun newStore(resumableThreshold: Int = 5 * 1024 * 1024): GoogleDriveSyncStore = GoogleDriveSyncStore(
        http = http,
        tokens = tokens,
        endpoints = GoogleEndpoints(api = server.url("/drive/v3").toString().trimEnd('/'), upload = server.url("/upload/drive/v3").toString().trimEnd('/')),
        resumableThreshold = resumableThreshold,
        chunkSize = 256 * 1024,
        pause = { pauses += it },
    )

    fun start() {
        server.dispatcher = drive
        server.start()
        drive.baseUrl = server.url("/").toString()
    }

    fun stop() = server.close()
}

internal class GoogleDriveSyncStoreContractTest : SyncStoreContract() {
    private val fixture = DriveFixture()
    override val store: SyncStore by lazy { fixture.newStore() }

    @Before fun up() = fixture.start()

    @After fun down() = fixture.stop()
}

/** The same contract with every file above 1 KB sent as a resumable upload. */
internal class GoogleDriveResumableContractTest : SyncStoreContract() {
    private val fixture = DriveFixture()
    override val store: SyncStore by lazy { fixture.newStore(resumableThreshold = 1_000) }

    @Before fun up() = fixture.start()

    @After fun down() = fixture.stop()
}

internal class GoogleDriveSyncStoreTest {
    private val fixture = DriveFixture()
    private val drive get() = fixture.drive
    private val server get() = fixture.server
    private val store by lazy { fixture.newStore() }
    private val media = "media/${"a".repeat(64)}"

    @Before fun up() = fixture.start()

    @After fun down() = fixture.stop()

    @Test
    fun theLayoutIsFlattenedIntoOneFolderAndEachFileKnowsItsGroup() {
        store.write("devices/$DEVICE_A/changes/3.mnc", byteArrayOf(1))
        store.write("sync.json", byteArrayOf(2))
        store.write(media, byteArrayOf(3))
        assertEquals(
            listOf("devices__${DEVICE_A}__changes__3.mnc", "media__${"a".repeat(64)}", "sync.json"),
            drive.files.values.map { it.name }.sorted(),
        )
        assertEquals(setOf("devices", "media", "root"), drive.files.values.map { it.group }.toSet())
    }

    @Test
    fun filesAreCreatedInTheAppDataFolderOnly() {
        // The fake rejects a create that doesn't name `appDataFolder` as the parent; nothing is created elsewhere.
        store.write("sync.json", byteArrayOf(1))
        assertTrue(drive.requests.none { it.contains("spaces=drive") })
        assertTrue(drive.requests.filter { it.startsWith("GET /drive/v3/files?") }.all { it.contains("spaces=appDataFolder") })
    }

    @Test
    fun aListingFollowsEveryPage() {
        drive.pageSize = 2
        repeat(7) { drive.seed("media__${it.toString().repeat(64).take(64)}") }
        assertEquals(7, store.list("media/").size)
        assertEquals(4, drive.requests.count { it.startsWith("GET /drive/v3/files?") })
    }

    @Test
    fun aListingOfOneGroupAsksDriveForOnlyThatGroup() {
        drive.seed("sync.json")
        drive.seed("media__${"b".repeat(64)}")
        assertEquals(listOf("media/${"b".repeat(64)}"), store.list("media/"))
        assertTrue(drive.requests.single { it.startsWith("GET /drive/v3/files?") }.contains("appProperties"))
        // A listing of everything has no filter.
        drive.requests.clear()
        assertEquals(2, store.list("").size)
        assertFalse(drive.requests.single { it.startsWith("GET /drive/v3/files?") }.contains("appProperties"))
    }

    @Test
    fun filesOfOtherNamesInTheFolderAreNotListed() {
        drive.seed("not-ours.txt")
        drive.seed("sync.json")
        assertEquals(listOf("sync.json"), store.list(""))
    }

    @Test
    fun twoFilesWithOneNameAreOneFileAndTheOlderIsRead() {
        drive.seed("sync.json", byteArrayOf(2), created = "2026-02-01T00:00:00.000Z")
        drive.seed("sync.json", byteArrayOf(1), created = "2026-01-01T00:00:00.000Z")
        assertEquals(listOf("sync.json"), store.list(""))
        assertContentEquals(byteArrayOf(1), store.read("sync.json"))
    }

    @Test
    fun deleteRemovesEveryCopyOfAName() {
        drive.seed("sync.json", byteArrayOf(2))
        drive.seed("sync.json", byteArrayOf(1))
        store.delete("sync.json")
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun theOlderOfTwoClaimsOnTheManifestWins() {
        drive.raceOnNextCreate = true
        assertFailsWith<SyncAlreadyExistsException> { store.write("sync.json", byteArrayOf(1)) }
        // Ours was removed; theirs stays and is what everyone reads.
        assertEquals(1, drive.files.size)
        assertContentEquals(byteArrayOf(9), store.read("sync.json"))
    }

    @Test
    fun aFileDeletedBehindOurBackIsNotFoundAndItsNameIsFreeAgain() {
        store.write("sync.json", byteArrayOf(1))
        drive.files.clear()
        assertFailsWith<SyncNotFoundException> { store.read("sync.json") }
        store.write("sync.json", byteArrayOf(2))
        assertContentEquals(byteArrayOf(2), store.read("sync.json"))
    }

    @Test
    fun aFileReplacedBehindOurBackIsFoundByNameAgain() {
        store.write("sync.json", byteArrayOf(1))
        drive.files.clear()
        drive.seed("sync.json", byteArrayOf(5))
        assertContentEquals(byteArrayOf(5), store.read("sync.json"))
    }

    @Test
    fun anEncryptedLocationWorksOnDriveEndToEnd() {
        val remote = SyncRemote.create(store, "collection-1", 1_000, "correct horse".toCharArray(), FAST_ITERATIONS)
        val changes = sampleChanges()
        assertEquals(listOf(1L), remote.writeChanges(DEVICE_A, 1, changes))
        val bytes = ByteArray(2_000) { it.toByte() }
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        remote.writeMedia(hash, bytes)
        remote.writeMedia(hash, bytes)

        // Another device, another store on the same Drive, joins with the passphrase.
        val other = SyncRemote.open(fixture.newStore(), "correct horse".toCharArray())
        assertEquals(listOf(1L), other.listChangeSeqs(DEVICE_A))
        assertEquals(changes, other.readChanges(DEVICE_A, 1).changes)
        assertEquals(listOf(hash), other.listMedia())
        assertContentEquals(bytes, other.readMedia(hash))
        // Drive holds only sealed files: neither the text nor the media bytes are in what it stores.
        assertTrue(drive.files.values.none { String(it.bytes, Charsets.ISO_8859_1).contains("math calc") })
    }

    // --- tokens and failures -----------------------------------------------------------------------------------

    @Test
    fun aRefusedTokenIsReplacedOnceAndTheCallIsRepeated() {
        drive.validToken = "token-2"
        drive.seed("sync.json", byteArrayOf(7))
        assertContentEquals(byteArrayOf(7), store.read("sync.json"))
        assertEquals(listOf(false, true), fixture.tokenRequests.take(2))
    }

    @Test
    fun aTokenThatIsRefusedAgainIsAnAuthError() {
        drive.validToken = "never"
        assertFailsWith<SyncAuthException> { store.list("") }
        assertEquals(2, fixture.tokenRequests.size)
    }

    @Test
    fun aDriveThatIsFullIsAQuotaError() {
        drive.quotaFull = true
        assertFailsWith<SyncQuotaException> { store.write("sync.json", byteArrayOf(1)) }
        assertTrue(drive.files.isEmpty())
    }

    @Test
    fun aForbiddenCallIsAnAuthError() {
        drive.injected += FakeDriveServer.error(403, "insufficientPermissions", "Insufficient Permission")
        assertFailsWith<SyncAuthException> { store.list("") }
    }

    @Test
    fun rateLimitsAndServerErrorsAreRetriedWithGrowingPauses() {
        drive.seed("sync.json", byteArrayOf(7))
        drive.injected += FakeDriveServer.error(403, "userRateLimitExceeded", "slow down")
        drive.injected += MockResponse.Builder().code(429).build()
        drive.injected += MockResponse.Builder().code(503).build()
        assertEquals(listOf("sync.json"), store.list(""))
        assertEquals(listOf(500L, 1_000L, 2_000L), fixture.pauses)
    }

    @Test
    fun aServerThatKeepsFailingIsAnIoErrorAfterAFewTries() {
        repeat(10) { drive.injected += MockResponse.Builder().code(500).build() }
        assertFailsWith<SyncIoException> { store.list("") }
        assertEquals(4, fixture.pauses.size)
    }

    @Test
    fun noNetworkIsOffline() {
        server.close()
        assertFailsWith<SyncOfflineException> { store.list("") }
    }

    @Test
    fun aRedirectIsNeverFollowed() {
        drive.injected += MockResponse.Builder().code(302).addHeader("Location", "http://127.0.0.1:1/elsewhere").build()
        assertFailsWith<SyncIoException> { store.list("") }
        assertEquals(1, drive.requests.size)
    }

    @Test
    fun theBearerTokenIsSentWithEveryCall() {
        store.write("sync.json", byteArrayOf(1))
        store.read("sync.json")
        assertTrue(drive.headers.all { it["Authorization"] == "Bearer token-1" })
    }

    // --- resumable uploads -------------------------------------------------------------------------------------

    private val big = ByteArray(700 * 1024) { (it * 7).toByte() }

    private val bigStore by lazy { fixture.newStore(resumableThreshold = 1_000) }

    @Test
    fun aBigFileGoesUpInChunksOfASession() {
        bigStore.write("snapshots/$DEVICE_A-1.mns", big)
        assertContentEquals(big, drive.files.values.single().bytes)
        // 700 KB in 256 KB chunks: three PUTs, after one request that opens the session.
        assertEquals(3, drive.requests.count { it.startsWith("PUT /upload/session/") })
        assertTrue(drive.requests.any { it.startsWith("POST /upload/drive/v3/files?") && it.contains("uploadType=resumable") })
    }

    @Test
    fun theSessionAddressGetsNoToken() {
        bigStore.write("snapshots/$DEVICE_A-1.mns", big)
        val chunks = drive.requests.indices.filter { drive.requests[it].startsWith("PUT /upload/session/") }
        assertTrue(chunks.isNotEmpty() && chunks.all { drive.headers[it]["Authorization"] == null })
    }

    @Test
    fun aChunkThatFailsIsResumedFromWhereDriveHasIt() {
        // The first chunk goes through; the second gets a 503. The store asks how much arrived and goes on from there.
        drive.failChunk = 2
        bigStore.write("snapshots/$DEVICE_A-1.mns", big)
        assertContentEquals(big, drive.files.values.single().bytes)
        assertEquals(1, drive.files.size)
        // It asked how much had arrived (a chunk with no bytes and `*` for the range) instead of starting over.
        assertTrue(drive.headers.any { it["Content-Range"]?.startsWith("bytes */") == true })
    }

    @Test
    fun anUploadSessionOnAnotherHostIsRefused() {
        drive.sessionBase = "http://localhost:1/"
        assertFailsWith<SyncIoException> { bigStore.write("snapshots/$DEVICE_A-1.mns", big) }
        assertTrue(drive.requests.none { it.startsWith("PUT /upload/session/") })
    }

    @Test
    fun aBigOverwriteReplacesTheContentOfTheFile() {
        bigStore.write("devices/$DEVICE_A/device.json", byteArrayOf(1))
        bigStore.overwrite("devices/$DEVICE_A/device.json", big)
        assertEquals(1, drive.files.size)
        assertContentEquals(big, drive.files.values.single().bytes)
        assertTrue(drive.requests.any { it.startsWith("PATCH /upload/drive/v3/files/") && it.contains("uploadType=resumable") })
    }
}
