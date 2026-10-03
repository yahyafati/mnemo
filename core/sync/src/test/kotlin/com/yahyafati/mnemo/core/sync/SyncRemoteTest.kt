package com.yahyafati.mnemo.core.sync

import com.yahyafati.mnemo.core.sync.format.Change
import com.yahyafati.mnemo.core.sync.format.DeviceInfo
import com.yahyafati.mnemo.core.sync.format.SyncFormat
import com.yahyafati.mnemo.core.sync.format.SyncKey
import com.yahyafati.mnemo.core.sync.store.FolderSyncStore
import com.yahyafati.mnemo.core.sync.store.InMemorySyncStore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The S2 behaviour of [SyncRemote], on both stores. [store] is shared by everyone who "syncs" in a test. */
abstract class SyncRemoteTest {
    abstract val store: SyncStore

    /** Cuts the file at [path] to [length] bytes behind the store's back, like a crash during its write. */
    abstract fun truncate(path: String, length: Int)

    /** Changes a byte of the file at [path] behind the store's back. */
    abstract fun damage(path: String, at: Int)

    private fun create(passphrase: String? = null) =
        SyncRemote.create(store, "collection-1", 1_000, passphrase?.toCharArray(), FAST_ITERATIONS)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- the manifest, create and open ---------------------------------------------------------------------

    @Test
    fun aLocationIsCreatedOnceAndOpenedByTheOthers() {
        val first = create()
        assertFalse(first.isEncrypted)
        assertFailsWith<SyncAlreadyExistsException> { create() }
        val second = SyncRemote.open(store)
        assertEquals(first.manifest, second.manifest)
        assertEquals("collection-1", second.manifest.collectionId)
        assertEquals(SyncFormat.VERSION, second.manifest.formatVersion)
        assertNull(second.manifest.encryption)
    }

    @Test
    fun openingAnEmptyLocationIsNotFound() {
        assertFailsWith<SyncNotFoundException> { SyncRemote.open(store) }
        assertFailsWith<SyncNotFoundException> { SyncRemote.readManifest(store) }
    }

    @Test
    fun theManifestIsPlainJsonAThirdPartyCanRead() {
        create("secret")
        val text = store.read(SyncPaths.MANIFEST).decodeToString()
        assertTrue("\"collectionId\": \"collection-1\"" in text, text)
        assertTrue("pbkdf2-hmac-sha256" in text)
        assertFalse("secret" in text)
    }

    @Test
    fun anEncryptedLocationOpensWithItsPassphraseAndRefusesAWrongOneAtOnce() {
        val created = create("open sesame")
        assertTrue(created.isEncrypted)
        val opened = SyncRemote.open(store, "open sesame".toCharArray())
        assertContentEquals(created.key!!.toBytes(), opened.key!!.toBytes())
        val wrong = assertFailsWith<SyncPassphraseException> { SyncRemote.open(store, "open sesamE".toCharArray()) }
        assertFalse(wrong.required)
        assertTrue(assertFailsWith<SyncPassphraseException> { SyncRemote.open(store) }.required)
    }

    @Test
    fun aKeptKeyReopensTheLocationAndAWrongKeyDoesNot() {
        val created = create("open sesame")
        SyncRemote.openWithKey(store, SyncKey.fromBytes(created.key!!.toBytes()))
        assertFalse(assertFailsWith<SyncPassphraseException> { SyncRemote.openWithKey(store, SyncKey.fromBytes(ByteArray(32))) }.required)
        assertTrue(assertFailsWith<SyncPassphraseException> { SyncRemote.openWithKey(store, null) }.required)
    }

    @Test
    fun aWrongPassphraseNeverGetsAsFarAsAFile() {
        create("right").writeChanges(DEVICE_A, 1, sampleChanges())
        assertFailsWith<SyncPassphraseException> { SyncRemote.open(store, "wrong".toCharArray()) }
    }

    @Test
    fun aManifestFromANewerVersionIsRefusedAndLeftAlone() {
        val newer = """{"formatVersion": ${SyncFormat.VERSION + 1}, "somethingNew": [1, 2], "collectionId": "c", "createdAt": 1}"""
        store.write(SyncPaths.MANIFEST, newer.toByteArray())
        val e = assertFailsWith<SyncUnsupportedVersionException> { SyncRemote.open(store) }
        assertEquals(SyncFormat.VERSION + 1, e.found)
        assertEquals(newer, store.read(SyncPaths.MANIFEST).decodeToString())
    }

    @Test
    fun aManifestWithUnknownFieldsOfTheSameVersionStillOpens() {
        store.write(SyncPaths.MANIFEST, """{"formatVersion": 1, "collectionId": "c", "createdAt": 5, "futureField": true}""".toByteArray())
        assertEquals("c", SyncRemote.open(store).manifest.collectionId)
    }

    @Test
    fun anUnreadableManifestIsCorrupt() {
        store.write(SyncPaths.MANIFEST, "{\"formatVer".toByteArray())
        assertFailsWith<SyncCorruptException> { SyncRemote.open(store) }
        store.delete(SyncPaths.MANIFEST)
        store.write(SyncPaths.MANIFEST, """{"collectionId": "c"}""".toByteArray())
        assertFailsWith<SyncCorruptException> { SyncRemote.open(store) }
    }

    @Test
    fun aFailedCreateLeavesNoManifestBehind() {
        val failing = object : SyncStore by store {
            override fun write(path: String, bytes: ByteArray) {
                store.write(path, bytes.copyOf(3))
                throw SyncIoException("cut off")
            }
        }
        assertFailsWith<SyncIoException> { SyncRemote.create(failing, "c", 1) }
        assertEquals(emptyList(), store.list(""))
        create()
    }

    @Test
    fun aCreateThatFailsBeforeWritingNeverTouchesAnotherDevicesManifest() {
        create()
        val offline = object : SyncStore by store {
            override fun write(path: String, bytes: ByteArray) = throw SyncOfflineException("no network")
        }
        assertFailsWith<SyncOfflineException> { SyncRemote.create(offline, "other", 1) }
        assertEquals("collection-1", SyncRemote.open(store).manifest.collectionId)
    }

    // --- change files --------------------------------------------------------------------------------------

    @Test
    fun everyKindOfChangeRoundTripsPlainAndEncrypted() {
        for (passphrase in listOf(null, "pass")) {
            val remote = create(passphrase)
            val changes = sampleChanges()
            assertEquals(listOf(1L), remote.writeChanges(DEVICE_A, 1, changes))
            val batch = SyncRemote.open(store, passphrase?.toCharArray()).readChanges(DEVICE_A, 1)
            assertEquals(changes, batch.changes)
            assertEquals(DEVICE_A, batch.deviceId)
            assertEquals(1L, batch.seq)
            assertEquals(100L, batch.clockFrom)
            assertEquals(104L, batch.clockTo)
            assertTrue(changes.last() is Change.Delete)
            tearDown()
        }
    }

    protected open fun tearDown() {
        store.list("").forEach(store::delete)
    }

    @Test
    fun anEncryptedChangeFileHoldsNothingReadable() {
        create("pass").writeChanges(DEVICE_A, 1, sampleChanges())
        val raw = String(store.read(SyncPaths.changeFile(DEVICE_A, 1)), Charsets.ISO_8859_1)
        assertFalse("ratio" in raw || "notes" in raw || "frac" in raw)
    }

    @Test
    fun changeFilesAreListedByDeviceInOrderAndAfterASeq() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        remote.writeChanges(DEVICE_A, 2, sampleChanges(200))
        remote.writeChanges(DEVICE_A, 10, sampleChanges(300))
        remote.writeChanges(DEVICE_B, 1, sampleChanges(400))
        store.write("devices/$DEVICE_A/changes/readme.txt", byteArrayOf(1))
        assertEquals(listOf(1L, 2L, 10L), remote.listChangeSeqs(DEVICE_A))
        assertEquals(listOf(10L), remote.listChangeSeqs(DEVICE_A, after = 2))
        assertEquals(listOf(1L), remote.listChangeSeqs(DEVICE_B))
        assertEquals(emptyList(), remote.listChangeSeqs("0a1b2c3d-0000-4000-8000-0000000000ff"))
    }

    @Test
    fun noChangesWriteNoFile() {
        assertEquals(emptyList(), create().writeChanges(DEVICE_A, 1, emptyList()))
        assertEquals(listOf(SyncPaths.MANIFEST), store.list(""))
    }

    @Test
    fun aBigImportBecomesSeveralFilesInOrderEachUnderTheCap() {
        val remote = create("pass")
        val changes = randomChanges(count = 1_000, chars = 100)
        val seqs = remote.writeChanges(DEVICE_A, 5, changes, maxFileBytes = 20_000)
        assertTrue(seqs.size > 3, "expected several files, got ${seqs.size}")
        assertEquals(List(seqs.size) { 5L + it }, seqs)
        seqs.forEach { assertTrue(store.read(SyncPaths.changeFile(DEVICE_A, it)).size <= 20_000) }
        val back = seqs.flatMap { remote.readChanges(DEVICE_A, it).changes }
        assertEquals(changes, back)
        // The clock range of each file covers exactly its own changes.
        seqs.map { remote.readChanges(DEVICE_A, it) }.forEach { b ->
            assertEquals(b.changes.minOf { it.clock }, b.clockFrom)
            assertEquals(b.changes.maxOf { it.clock }, b.clockTo)
        }
    }

    @Test
    fun theDefaultCapIsAboutTwoMegabytes() {
        assertEquals(2_000_000, SyncFormat.MAX_CHANGE_FILE_BYTES)
        val remote = create()
        // 20,000 incompressible 400-character notes: several MB, so at least three files.
        val seqs = remote.writeChanges(DEVICE_A, 1, randomChanges(20_000, 400))
        assertTrue(seqs.size >= 3, "${seqs.size} files")
        seqs.forEach { assertTrue(store.read(SyncPaths.changeFile(DEVICE_A, it)).size <= SyncFormat.MAX_CHANGE_FILE_BYTES) }
        assertEquals(20_000, seqs.sumOf { remote.readChanges(DEVICE_A, it).changes.size })
    }

    @Test
    fun oneChangeBiggerThanTheCapStaysInOneFile() {
        val remote = create()
        val big = randomChanges(1, 100_000).single()
        assertEquals(listOf(1L), remote.writeChanges(DEVICE_A, 1, listOf(big), maxFileBytes = 20_000))
        assertEquals(listOf(big), remote.readChanges(DEVICE_A, 1).changes)
    }

    @Test
    fun aWholeFileIsNeverRewritten() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        val before = store.read(SyncPaths.changeFile(DEVICE_A, 1))
        assertFailsWith<SyncAlreadyExistsException> { remote.writeChanges(DEVICE_A, 1, sampleChanges(500)) }
        assertContentEquals(before, store.read(SyncPaths.changeFile(DEVICE_A, 1)))
    }

    @Test
    fun aTruncatedChangeFileIsNotAppliedAndSaysItMayStillBeComing() {
        val remote = create("pass")
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        val path = SyncPaths.changeFile(DEVICE_A, 1)
        truncate(path, store.read(path).size / 2)
        val e = assertFailsWith<SyncCorruptException> { remote.readChanges(DEVICE_A, 1) }
        assertTrue(e.maybeIncomplete)
    }

    @Test
    fun aDamagedChangeFileIsNotApplied() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        damage(SyncPaths.changeFile(DEVICE_A, 1), 30)
        assertFalse(assertFailsWith<SyncCorruptException> { remote.readChanges(DEVICE_A, 1) }.maybeIncomplete)
    }

    @Test
    fun theHalfWrittenFileACrashLeftIsReplacedByTheRetry() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        val path = SyncPaths.changeFile(DEVICE_A, 1)
        truncate(path, 20)
        assertEquals(listOf(1L), remote.writeChanges(DEVICE_A, 1, sampleChanges(700)))
        assertEquals(sampleChanges(700), remote.readChanges(DEVICE_A, 1).changes)
    }

    @Test
    fun aChangeFileCopiedUnderAnotherNameIsRefused() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        store.write(SyncPaths.changeFile(DEVICE_B, 1), store.read(SyncPaths.changeFile(DEVICE_A, 1)))
        // Plain location: the envelope doesn't know its name, the batch does.
        assertFailsWith<SyncCorruptException> { remote.readChanges(DEVICE_B, 1) }
        store.write(SyncPaths.changeFile(DEVICE_A, 2), store.read(SyncPaths.changeFile(DEVICE_A, 1)))
        assertFailsWith<SyncCorruptException> { remote.readChanges(DEVICE_A, 2) }
    }

    @Test
    fun aChangeFileMovedInAnEncryptedLocationDoesNotDecrypt() {
        val remote = create("pass")
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        store.write(SyncPaths.changeFile(DEVICE_B, 1), store.read(SyncPaths.changeFile(DEVICE_A, 1)))
        assertFailsWith<SyncCorruptException> { remote.readChanges(DEVICE_B, 1) }
    }

    @Test
    fun aPlainFileInAnEncryptedLocationIsRefused() {
        val plain = create()
        plain.writeChanges(DEVICE_A, 1, sampleChanges())
        val bytes = store.read(SyncPaths.changeFile(DEVICE_A, 1))
        tearDown()
        val encrypted = create("pass")
        store.write(SyncPaths.changeFile(DEVICE_A, 1), bytes)
        assertFailsWith<SyncCorruptException> { encrypted.readChanges(DEVICE_A, 1) }
    }

    @Test
    fun aChangeFileFromANewerVersionIsRefusedAndNotRewritten() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        val path = SyncPaths.changeFile(DEVICE_A, 1)
        val newer = store.read(path).also { it[5] = (SyncFormat.VERSION + 1).toByte() }
        store.delete(path)
        store.write(path, newer)
        assertFailsWith<SyncUnsupportedVersionException> { remote.readChanges(DEVICE_A, 1) }
        // Not even a retry of the same number may replace what this version can't read.
        assertFailsWith<SyncUnsupportedVersionException> { remote.writeChanges(DEVICE_A, 1, sampleChanges()) }
        assertContentEquals(newer, store.read(path))
    }

    @Test
    fun deletedChangeFilesAreGone() {
        val remote = create()
        remote.writeChanges(DEVICE_A, 1, sampleChanges())
        remote.deleteChanges(DEVICE_A, 1)
        remote.deleteChanges(DEVICE_A, 1)
        assertEquals(emptyList(), remote.listChangeSeqs(DEVICE_A))
        assertFailsWith<SyncNotFoundException> { remote.readChanges(DEVICE_A, 1) }
    }

    // --- devices -------------------------------------------------------------------------------------------

    private fun deviceInfo(id: String, lastSeq: Long = 3) =
        DeviceInfo(id, "Yahya's phone", "android", "0.9.0", lastSeq, mapOf(DEVICE_B to 7L), updatedAt = 5_000)

    @Test
    fun aDeviceDescribesItselfAndMayReplaceItsOwnDescription() {
        for (passphrase in listOf(null, "pass")) {
            val remote = create(passphrase)
            assertNull(remote.readDevice(DEVICE_A))
            remote.writeDevice(deviceInfo(DEVICE_A, lastSeq = 3))
            remote.writeDevice(deviceInfo(DEVICE_A, lastSeq = 4))
            assertEquals(deviceInfo(DEVICE_A, lastSeq = 4), remote.readDevice(DEVICE_A))
            tearDown()
        }
    }

    @Test
    fun devicesAreListedOnceEachWhateverTheyHaveWritten() {
        val remote = create()
        remote.writeChanges(DEVICE_B, 1, sampleChanges())
        remote.writeChanges(DEVICE_B, 2, sampleChanges())
        remote.writeDevice(deviceInfo(DEVICE_A))
        remote.writeDevice(deviceInfo(DEVICE_B))
        assertEquals(listOf(DEVICE_A, DEVICE_B), remote.listDevices())
    }

    @Test
    fun anUnreadableDeviceFileIsCorruptNotEmptyAndAnotherDevicesFileIsRefused() {
        val remote = create()
        remote.writeDevice(deviceInfo(DEVICE_A))
        truncate(SyncPaths.deviceInfo(DEVICE_A), 15)
        assertFailsWith<SyncCorruptException> { remote.readDevice(DEVICE_A) }
        store.overwrite(SyncPaths.deviceInfo(DEVICE_A), run {
            remote.writeDevice(deviceInfo(DEVICE_B))
            store.read(SyncPaths.deviceInfo(DEVICE_B))
        })
        assertFailsWith<SyncCorruptException> { remote.readDevice(DEVICE_A) }
    }

    // --- snapshots and media ----------------------------------------------------------------------------

    @Test
    fun snapshotsRoundTripAndAreListedByClock() {
        for (passphrase in listOf(null, "pass")) {
            val remote = create(passphrase)
            val payload = ByteArray(100_000) { (it % 13).toByte() }
            val old = remote.writeSnapshot(DEVICE_A, 50, payload)
            val newer = remote.writeSnapshot(DEVICE_B, 400, "newer".toByteArray())
            assertContentEquals(payload, remote.readSnapshot(old))
            assertEquals("newer", remote.readSnapshot(newer).decodeToString())
            assertEquals(listOf(old, newer), remote.listSnapshots())
            assertTrue(store.read(old.path).size < payload.size / 4)
            remote.deleteSnapshot(old)
            assertEquals(listOf(newer), remote.listSnapshots())
            assertFailsWith<SyncAlreadyExistsException> { remote.writeSnapshot(DEVICE_B, 400, "again".toByteArray()) }
            tearDown()
        }
    }

    @Test
    fun mediaIsStoredByHashAndCheckedWhenRead() {
        for (passphrase in listOf(null, "pass")) {
            val remote = create(passphrase)
            val image = ByteArray(10_000) { (it * 7).toByte() }
            val sha = sha256(image)
            remote.writeMedia(sha, image)
            remote.writeMedia(sha, image)
            assertContentEquals(image, remote.readMedia(sha))
            assertEquals(listOf(sha), remote.listMedia())
            assertFailsWith<IllegalArgumentException> { remote.writeMedia(sha, byteArrayOf(1)) }
            remote.deleteMedia(sha)
            assertEquals(emptyList(), remote.listMedia())
            assertFailsWith<SyncNotFoundException> { remote.readMedia(sha) }
            tearDown()
        }
    }

    @Test
    fun aMediaFileUnderTheWrongHashIsCorrupt() {
        val remote = create()
        val a = "a".toByteArray()
        val b = "b".toByteArray()
        remote.writeMedia(sha256(a), a)
        remote.writeMedia(sha256(b), b)
        store.delete(SyncPaths.media(sha256(a)))
        store.write(SyncPaths.media(sha256(a)), store.read(SyncPaths.media(sha256(b))))
        // Plain location, so the envelope checks out: only the hash can tell.
        assertFailsWith<SyncCorruptException> { remote.readMedia(sha256(a)) }
    }

    @Test
    fun aHalfUploadedMediaFileIsReplacedByTheNextUpload() {
        val remote = create()
        val image = ByteArray(5_000) { it.toByte() }
        val sha = sha256(image)
        remote.writeMedia(sha, image)
        truncate(SyncPaths.media(sha), 100)
        assertFailsWith<SyncCorruptException> { remote.readMedia(sha) }
        remote.writeMedia(sha, image)
        assertContentEquals(image, remote.readMedia(sha))
    }

    // --- failures ----------------------------------------------------------------------------------------

    @Test
    fun anOfflineStoreSurfacesAsOfflineEverywhere() {
        create().writeChanges(DEVICE_A, 1, sampleChanges())
        val offline = object : SyncStore by store {
            override fun list(prefix: String) = throw SyncOfflineException("no network")
            override fun read(path: String) = throw SyncOfflineException("no network")
            override fun write(path: String, bytes: ByteArray) = throw SyncOfflineException("no network")
            override fun overwrite(path: String, bytes: ByteArray) = throw SyncOfflineException("no network")
        }
        assertFailsWith<SyncOfflineException> { SyncRemote.readManifest(offline) }
        assertFailsWith<SyncOfflineException> { SyncRemote.create(offline, "x", 1) }
        val cut = SyncRemote.openWithKey(object : SyncStore by store {}, null)
        assertEquals(listOf(1L), cut.listChangeSeqs(DEVICE_A))
        assertEquals(1, cut.readChanges(DEVICE_A, 1).seq.toInt())
    }
}

class InMemorySyncRemoteTest : SyncRemoteTest() {
    private val memory = InMemorySyncStore()
    override val store: SyncStore get() = memory
    override fun truncate(path: String, length: Int) = memory.truncate(path, length)
    override fun damage(path: String, at: Int) = memory.damage(path, at)
}

class FolderSyncRemoteTest : SyncRemoteTest() {
    @get:Rule
    val folder = TemporaryFolder()

    override val store: SyncStore by lazy { FolderSyncStore(DirectoryDocumentAccess(), folder.root.absolutePath) }

    private fun file(path: String) = File(folder.root, path.replace("/", "__"))

    override fun truncate(path: String, length: Int) {
        val file = file(path)
        file.writeBytes(file.readBytes().copyOf(length))
    }

    override fun damage(path: String, at: Int) {
        val file = file(path)
        file.writeBytes(file.readBytes().also { it[at] = (it[at].toInt() xor 0x55).toByte() })
    }
}
