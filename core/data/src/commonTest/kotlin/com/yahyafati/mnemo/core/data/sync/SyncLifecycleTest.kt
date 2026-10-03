package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.repository.FileMediaRepository
import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.database.sync.SYNCED_TABLES
import com.yahyafati.mnemo.core.database.sync.SyncRows
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncSignInCancelledException
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import com.yahyafati.mnemo.core.sync.store.InMemorySyncStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.fileDatabase
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.io.File
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The lifecycle around the merge (docs/sync/ROADMAP.md S4): create, join, leave, snapshots and clean-up, a device that
 * was away too long, restore. Every node is a whole device (database file, config, secrets, repository) and the
 * location is one [InMemorySyncStore], so nothing here touches a network.
 */
class SyncLifecycleTest : PlatformTest() {
    private val store = InMemorySyncStore()
    private val backend = SyncBackend.Folder("memory://location")
    private val nodes = ArrayList<Node>()

    /** What the stores were asked to give up, and how often the user signed in to Google (S6). */
    private val released = ArrayList<SyncBackend>()
    private var googleSignIns = 0
    private val webDavConnects = ArrayList<String>()
    private val t0 = Instant.parse("2026-03-01T09:00:00Z")

    private inner class Node(val name: String, val policy: SyncPolicy = POLICY) {
        val clock = TestClock(t0)
        val directories = TestAppDirectories()
        val db: MnemoDatabase = fileDatabase(directories.databaseFile(MnemoDatabase.NAME))
        val settings = FakeUserSettingsRepository()
        val media = InMemoryMediaFiles()
        private val transaction = RoomTransactionRunner(db)
        val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
        val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
        private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)
        var repository: DefaultSyncRepository = newRepository()

        /** A new repository on the same files, like the app started again: nothing is cached. */
        fun newRepository(): DefaultSyncRepository {
            val syncClock = SyncClock(db.syncDao(), transaction, clock)
            val engine = SyncEngine(db, transaction, syncClock, clock, media, settings, TestReplayer, Dispatchers.Unconfined)
            val snapshots = SyncSnapshots(db, transaction, syncClock, settings)
            val mediaRepository = FileMediaRepository(directories.media, db.mediaDao(), db.noteDao(), clock, Dispatchers.Unconfined)
            val backups = BackupManager(directories, FakeDocumentAccess(), DatabaseSnapshot(db), mediaRepository, clock, Dispatchers.Unconfined)
            return DefaultSyncRepository(
                database = db,
                transaction = transaction,
                engine = engine,
                snapshots = snapshots,
                maintenance = SyncMaintenance(db, snapshots, clock, policy),
                configs = SyncConfigStore(directories, Dispatchers.Unconfined),
                secrets = secrets,
                stores = object : SyncStores {
                    override fun open(backend: SyncBackend): SyncStore = store

                    override suspend fun release(backend: SyncBackend) {
                        released += backend
                    }

                    override val googleDriveAvailable = true

                    override suspend fun signInToGoogleDrive() {
                        googleSignIns++
                    }

                    override suspend fun connectWebDav(url: String, username: String, password: String): SyncBackend.WebDav {
                        webDavConnects += "$url|$username|$password"
                        return SyncBackend.WebDav(url, username)
                    }
                },
                backups = backups,
                directories = directories,
                media = media,
                describer = DeviceDescriber { DeviceDescription(name, "test", "1") },
                clock = clock,
                ioDispatcher = Dispatchers.Unconfined,
            ).also { repository = it }
        }

        suspend fun deviceId() = db.syncDao().getState()!!.deviceId

        suspend fun deck(path: String): String = decks.saveDeck(path)

        suspend fun note(deck: String, front: String, back: String): String =
            cards.addNote(deck, NoteKind.Basic, listOf(front, back), emptyList()).id

        suspend fun status() = repository.status.first()

        suspend fun sync() = repository.syncNow()

        suspend fun dump(): List<String> {
            val rows = SyncRows(db)
            return buildList {
                for (table in SYNCED_TABLES) {
                    val key = table.keyColumns.joinToString(" || '/' || ")
                    for (id in db.queryStrings("SELECT $key FROM ${table.name} ORDER BY $key")) add("${table.name}/$id ${rows.get(table.name, id)!!}")
                }
                val s = settings.settings.value
                add("settings ${s.desiredRetention} ${s.newCardsPerDay} ${s.reviewsPerDay}")
            }
        }

        fun close() {
            db.close()
            directories.delete()
        }
    }

    private fun node(name: String, policy: SyncPolicy = POLICY) = Node(name, policy).also { nodes += it }

    @After
    fun tearDown() = nodes.forEach { it.close() }

    private suspend fun assertSame(a: Node, b: Node) {
        val first = a.dump()
        val second = b.dump()
        val differences = (first - second.toSet()).map { "${a.name}: $it" } + (second - first.toSet()).map { "${b.name}: $it" }
        assertTrue(differences.isEmpty(), "The devices differ:\n" + differences.joinToString("\n"))
    }

    private fun assertSynced(result: SyncResult) = assertTrue(result is SyncResult.Done, "Expected a finished round, got $result")

    private suspend fun collection(a: Node): String {
        val deck = a.deck("Spanish")
        a.note(deck, "hola", "hello")
        return deck
    }

    // --- create and join --------------------------------------------------------------------------------------

    @Test
    fun aCollectionCreatedOnOneDeviceIsJoinedByAnEmptyOne() = runTest {
        val a = node("A")
        val b = node("B")
        a.settings.setDesiredRetention(0.8)
        collection(a)

        a.repository.create(backend, null)
        val joined = b.repository.join(backend, null)

        assertNull(joined.safetyBackup)
        assertSame(a, b)
        assertEquals(0.8, b.settings.settings.value.desiredRetention)
        assertTrue(a.status() is SyncStatus.Idle)
        assertTrue(b.status() is SyncStatus.Idle)
        // Every later change travels both ways.
        a.note(a.deck("Spanish"), "adios", "goodbye")
        b.note(b.deck("French"), "bonjour", "hello")
        assertSynced(a.sync())
        assertSynced(b.sync())
        assertSynced(a.sync())
        assertSame(a, b)
        assertEquals(3, a.db.queryStrings("SELECT id FROM notes").size)
    }

    @Test
    fun createRefusesALocationThatHoldsSyncData() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        val before = store.paths

        b.deck("Other")
        assertFailsWith<SyncAlreadyExistsException> { b.repository.create(backend, null) }

        assertEquals(before, store.paths)
        assertTrue(b.status() is SyncStatus.Off)
        assertFalse(b.db.syncDao().getState()!!.enabled)
    }

    @Test
    fun joiningWithDataAsksFirstAndSavesABackup() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        val mine = b.deck("Mine")
        b.note(mine, "q", "a")

        assertFailsWith<SyncReplacesLocalDataException> { b.repository.join(backend, null) }
        assertTrue(b.status() is SyncStatus.Off)
        assertEquals(1, b.db.queryStrings("SELECT id FROM notes").size)

        val result = b.repository.join(backend, null, replaceLocalData = true)

        val backup = File(URI(assertNotNull(result.safetyBackup)))
        assertTrue(backup.isFile && backup.length() > 0)
        assertSame(a, b)
        assertEquals(listOf("Spanish"), b.db.queryStrings("SELECT name FROM decks"))
    }

    @Test
    fun anEncryptedLocationNeedsItsPassphraseOnlyOnce() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, "correct horse".toCharArray())

        assertFailsWith<SyncPassphraseException> { b.repository.join(backend, null) }
        assertFailsWith<SyncPassphraseException> { b.repository.join(backend, "wrong".toCharArray()) }
        assertTrue(b.status() is SyncStatus.Off)
        b.repository.join(backend, "correct horse".toCharArray())

        assertSame(a, b)
        // The key was kept: a restarted app syncs without asking.
        b.note(b.deck("Spanish"), "uno", "one")
        assertSynced(b.newRepository().syncNow())
        assertSynced(a.sync())
        assertSame(a, b)
    }

    @Test
    fun aDeviceWithoutTheKeyIsToldToAskForIt() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(backend, "pw".toCharArray())
        // The key is gone (a restored Keystore, say).
        a.directories.secrets.deleteRecursively()

        val result = a.sync()

        assertEquals(SyncResult.Failed(SyncProblem.PassphraseRequired), result)
        assertTrue(a.status() is SyncStatus.Error)
        a.repository.unlock("pw".toCharArray())
        assertSynced(a.sync())
    }

    // --- leave ------------------------------------------------------------------------------------------------

    @Test
    fun leavingKeepsTheCollectionAndStopsRecording() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(backend, null)
        val before = a.dump()

        a.repository.leave()

        assertTrue(a.status() is SyncStatus.Off)
        assertEquals(before, a.dump())
        assertFalse(a.db.syncDao().getState()!!.enabled)
        a.note(a.deck("Spanish"), "x", "y")
        assertEquals(0, a.db.syncDao().countChanges())
        assertEquals(SyncResult.NotSyncing, a.sync())
        // The others stop waiting for it.
        assertEquals(0L, SyncRemote.open(store).readDevice(a.deviceId())!!.updatedAt)
        // And it can start again somewhere else, or join again.
        assertTrue(a.newRepository().status.first() is SyncStatus.Off)
    }

    @Test
    fun deletingSyncDataEmptiesTheLocationAndLeaves() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(backend, null)

        a.repository.deleteSyncData()

        assertTrue(store.paths.isEmpty())
        assertTrue(a.status() is SyncStatus.Off)
        assertEquals(1, a.db.queryStrings("SELECT id FROM notes").size)
        // The location is free again.
        a.repository.create(backend, null)
        assertTrue(a.status() is SyncStatus.Idle)
    }

    // --- snapshots and clean-up -------------------------------------------------------------------------------

    @Test
    fun cleanUpKeepsWhatARecentDeviceStillNeeds() = runTest {
        val a = node("A")
        val b = node("B")
        val deck = collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        val aId = a.deviceId()

        // A works on its own for a while; B is away, but not for long.
        repeat(5) { i ->
            a.note(deck, "q$i", "a$i")
            assertSynced(a.sync())
        }
        assertTrue(store.contains(SyncPaths.changeFile(aId, 2)), "B has not applied it, so it stays")
        assertTrue(store.paths.count { it.startsWith(SyncPaths.SNAPSHOTS) } >= 2, "A wrote a snapshot of its own")

        assertSynced(b.sync())
        assertSynced(a.sync())

        assertFalse(store.contains(SyncPaths.changeFile(aId, 2)), "everyone has it and a snapshot covers it")
        assertSame(a, b)

        // Cleaning up lost nobody anything: a device that joins now gets the same collection, and B goes on.
        b.note(b.deck("Spanish"), "from B", "b")
        assertSynced(b.sync())
        assertSynced(a.sync())
        val c = node("C")
        c.repository.join(backend, null)
        assertSame(a, c)
        assertSame(a, b)
    }

    @Test
    fun aDeviceUnknownToTheLocationStopsTheCleanUp() = runTest {
        val a = node("A")
        val deck = collection(a)
        a.repository.create(backend, null)
        // A device that has files here but no readable description may need any of them.
        store.write(SyncPaths.changeFile("ghost", 1), ByteArray(4))
        val aId = a.deviceId()

        repeat(5) { i ->
            a.note(deck, "q$i", "a$i")
            assertSynced(a.sync())
        }

        assertTrue(store.contains(SyncPaths.changeFile(aId, 2)))
    }

    @Test
    fun aDeviceAwayTooLongIsToldToJoinAgain() = runTest {
        val a = node("A")
        val b = node("B")
        val deck = collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        assertSynced(b.sync())

        // 100 days later only A is still around and cleans up.
        val later = t0.plus(Duration.ofDays(100))
        a.clock.set(later)
        repeat(5) { i ->
            a.note(deck, "q$i", "a$i")
            assertSynced(a.sync())
        }
        assertFalse(store.contains(SyncPaths.changeFile(a.deviceId(), 2)))

        b.clock.set(later)
        assertEquals(SyncResult.Failed(SyncProblem.MustRejoin), b.sync())
        val status = b.status()
        assertTrue(status is SyncStatus.Error && status.problem == SyncProblem.MustRejoin)

        b.repository.rejoin()
        assertSame(a, b)
        assertTrue(b.status() is SyncStatus.Idle)
        assertSynced(b.sync())
    }

    @Test
    fun aDeviceAwayLongButMissingNothingKeepsGoing() = runTest {
        val a = node("A")
        val b = node("B")
        val deck = collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        assertSynced(b.sync())

        val later = t0.plus(Duration.ofDays(100))
        a.clock.set(later)
        b.clock.set(later)
        a.note(deck, "new", "n")

        assertSynced(a.sync())
        assertSynced(b.sync())
        assertSame(a, b)
    }

    // --- restore ----------------------------------------------------------------------------------------------

    @Test
    fun aRestoreLeavesSyncOffAndAsksWhatToDo() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)

        // What PendingRestore does to the database it puts in place: sync off; the config file stays.
        b.db.syncDao().setEnabled(false)
        val restarted = b.newRepository()

        assertEquals(SyncStatus.Restored(backend), restarted.status.first())
        assertEquals(SyncResult.NotSyncing, restarted.syncNow())
        b.note(b.deck("Spanish"), "after restore", "x")
        assertEquals(0, b.db.syncDao().countChanges())
    }

    @Test
    fun afterARestoreTheSyncDataCanBeTakenAgain() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        b.db.syncDao().setEnabled(false)
        b.note(b.deck("Restored"), "old", "state")

        val result = b.newRepository().rejoin()

        assertNotNull(result.safetyBackup)
        assertSame(a, b)
        assertTrue(b.status() is SyncStatus.Idle)
    }

    @Test
    fun afterARestoreTheCollectionCanBecomeTheNewSyncData() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        b.db.syncDao().setEnabled(false)
        b.note(b.deck("Restored"), "old", "state")
        val repository = b.newRepository()

        repository.uploadAsNew(null)

        assertTrue(b.status() is SyncStatus.Idle)
        // A finds the sync data started again, and joins it (its own collection is replaced, after a backup).
        assertEquals(SyncResult.Failed(SyncProblem.Replaced), a.sync())
        a.repository.rejoin()
        assertSame(a, b)
        assertTrue(a.db.queryStrings("SELECT name FROM decks").contains("Restored"))
        assertSynced(a.sync())
    }

    @Test
    fun aDeviceThatJoinsAgainReadsItsOwnFilesTheSnapshotLacked() = runTest {
        val a = node("A")
        val b = node("B")
        val deck = collection(a)
        a.repository.create(backend, null)
        b.repository.join(backend, null)
        assertSynced(b.sync())

        // B's note reaches the location; A applies it but has written no snapshot since.
        b.note(b.deck("Spanish"), "from B", "b")
        assertSynced(b.sync())
        assertSynced(a.sync())
        a.note(deck, "from A", "a")
        assertSynced(a.sync())

        // B is replaced from the newest snapshot, which may not have its last file, and must still end up equal.
        b.repository.rejoin()
        assertSynced(b.sync())
        assertSynced(a.sync())
        assertSame(a, b)
        assertEquals(3, a.db.queryStrings("SELECT id FROM notes").size)
    }

    // --- what the Settings screen reads (S5) -----------------------------------------------------------------

    @Test
    fun aLocationIsInspectedBeforeCreatingOrJoining() = runTest {
        val a = node("A")
        val b = node("B")
        assertEquals(SyncLocation.Empty, b.repository.inspect(backend))

        collection(a)
        a.repository.create(backend, null)
        assertEquals(SyncLocation.SyncData(encrypted = false), b.repository.inspect(backend))

        // Inspecting changes nothing.
        val before = store.paths
        b.repository.inspect(backend)
        assertEquals(before, store.paths)
        assertTrue(b.status() is SyncStatus.Off)
    }

    @Test
    fun anEncryptedLocationSaysSoWithoutItsPassphrase() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, "correct horse".toCharArray())

        assertEquals(SyncLocation.SyncData(encrypted = true), b.repository.inspect(backend))
        assertTrue(a.repository.isEncrypted())
        assertFalse(b.repository.isEncrypted())
    }

    @Test
    fun aLocationWithLeftoversIsNotMnemos() = runTest {
        val a = node("A")
        store.write("media/${"0".repeat(64)}", byteArrayOf(1))

        assertEquals(SyncLocation.Leftovers, a.repository.inspect(backend))
    }

    @Test
    fun theDevicesOfALocationAreListedWithThisOneMarked() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)
        a.repository.create(backend, null)
        b.clock.advanceBy(Duration.ofMinutes(5))
        b.repository.join(backend, null)

        val seenByB = b.repository.devices()

        assertEquals(setOf("A", "B"), seenByB.map { it.name }.toSet())
        assertEquals(listOf("B"), seenByB.filter { it.isThisDevice }.map { it.name })
        // Newest first.
        assertEquals("B", seenByB.first().name)

        // A device that left is no longer listed.
        b.repository.leave()
        assertEquals(listOf("A"), a.repository.devices().map { it.name })
    }

    @Test
    fun theDevicesOfNoLocationCannotBeListed() = runTest {
        val a = node("A")
        assertFailsWith<IllegalStateException> { a.repository.devices() }
    }

    @Test
    fun failuresOfTheLocationComeToTheProblemTheScreenSays() {
        assertEquals(SyncProblem.Offline, SyncOfflineException("x").syncProblem())
        assertEquals(SyncProblem.Auth, SyncAuthException("x").syncProblem())
        assertEquals(SyncProblem.Quota, SyncQuotaException("x").syncProblem())
        assertEquals(SyncProblem.LocationGone, SyncNotFoundException("x").syncProblem())
        assertEquals(SyncProblem.LocationNotEmpty, SyncAlreadyExistsException("x").syncProblem())
        assertEquals(SyncProblem.PassphraseRequired, SyncPassphraseException(required = true).syncProblem())
        assertEquals(SyncProblem.PassphraseWrong, SyncPassphraseException(required = false).syncProblem())
        assertEquals(SyncProblem.UpdateRequired, SyncUnsupportedVersionException(9, 1).syncProblem())
        assertEquals(SyncProblem.Other, java.io.IOException("x").syncProblem())
    }

    @Test
    fun aStatusAsksForTheUserOnlyWhenSyncCantMendItself() {
        val folder = SyncBackend.Folder("/sync")
        val last = t0
        // Nothing to say while it works, or is off.
        assertNull(SyncStatus.Off.attentionFrom())
        assertNull(SyncStatus.Idle(folder, last).attentionFrom())
        assertNull(SyncStatus.Syncing(folder, last).attentionFrom())
        // Only a person can fix these: at once.
        assertEquals(Instant.EPOCH, SyncStatus.Restored(folder).attentionFrom())
        for (problem in listOf(SyncProblem.PassphraseRequired, SyncProblem.Replaced, SyncProblem.MustRejoin, SyncProblem.Auth, SyncProblem.LocationGone)) {
            assertEquals(Instant.EPOCH, SyncStatus.Error(folder, problem, last).attentionFrom(), "$problem")
        }
        // These may mend themselves: after a day without a good round.
        assertEquals(last.plus(Duration.ofDays(1)), SyncStatus.WaitingForNetwork(folder, last).attentionFrom())
        assertEquals(last.plus(Duration.ofDays(1)), SyncStatus.Error(folder, SyncProblem.Other, last).attentionFrom())
        // Never synced and failing: nothing to wait for.
        assertEquals(Instant.EPOCH, SyncStatus.WaitingForNetwork(folder, null).attentionFrom())
    }

    // --- Google Drive (S6) ----------------------------------------------------------------------------------------

    @Test
    fun googleDriveIsALocationLikeAFolder() = runTest {
        val drive = SyncBackend.GoogleDrive
        val a = node("A")
        val b = node("B")
        collection(a)

        a.repository.create(drive, "correct horse".toCharArray())
        b.repository.join(drive, "correct horse".toCharArray())
        a.note(a.deck("Spanish"), "adios", "goodbye")
        assertSynced(a.sync())
        assertSynced(b.sync())

        assertSame(a, b)
        assertEquals(drive, a.status().backend)
        assertEquals(drive, b.status().backend)
        assertTrue(a.repository.isEncrypted())
    }

    @Test
    fun theGoogleBackendIsRememberedAcrossARestart() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(SyncBackend.GoogleDrive, null)

        val status = a.newRepository().status.first()

        assertTrue(status is SyncStatus.Idle)
        assertEquals(SyncBackend.GoogleDrive, status.backend)
    }

    @Test
    fun aRefusedGoogleSignInIsAProblemTheUserFixesBySigningInAgain() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(SyncBackend.GoogleDrive, null)

        store.failure = SyncAuthException("Google no longer accepts this sign-in")
        assertEquals(SyncResult.Failed(SyncProblem.Auth), a.sync())
        val failed = a.status()
        assertTrue(failed is SyncStatus.Error && failed.problem == SyncProblem.Auth && failed.backend == SyncBackend.GoogleDrive)
        assertEquals(Instant.EPOCH, failed.attentionFrom())

        // Signing in again, then a round, mends it: nothing of the collection was touched.
        store.failure = null
        a.repository.signInToGoogleDrive()
        assertSynced(a.sync())
        assertEquals(1, googleSignIns)
        assertTrue(a.status() is SyncStatus.Idle)
    }

    @Test
    fun stoppingOrDeletingGivesTheGoogleSignInUp() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(SyncBackend.GoogleDrive, null)
        a.repository.leave()
        assertEquals(listOf<SyncBackend>(SyncBackend.GoogleDrive), released)
        assertTrue(a.status() is SyncStatus.Off)

        released.clear()
        val b = node("B")
        b.deck("Other")
        store.paths.forEach(store::delete)
        b.repository.create(SyncBackend.GoogleDrive, null)
        b.repository.deleteSyncData()
        assertEquals(listOf<SyncBackend>(SyncBackend.GoogleDrive), released)
        assertTrue(store.paths.isEmpty())
    }

    // --- WebDAV (S7) ----------------------------------------------------------------------------------------------

    private val dav = SyncBackend.WebDav("https://cloud.example.org/remote.php/dav/files/alice/Mnemo/", "alice")

    @Test
    fun webDavIsALocationLikeAFolderAndIsRememberedAcrossARestart() = runTest {
        val a = node("A")
        val b = node("B")
        collection(a)

        a.repository.create(dav, "correct horse".toCharArray())
        b.repository.join(dav, "correct horse".toCharArray())
        a.note(a.deck("Spanish"), "adios", "goodbye")
        assertSynced(a.sync())
        assertSynced(b.sync())

        assertSame(a, b)
        assertEquals(dav, b.status().backend)
        val restarted = a.newRepository().status.first()
        assertTrue(restarted is SyncStatus.Idle)
        assertEquals(dav, restarted.backend)
    }

    @Test
    fun aRefusedPasswordIsAProblemTheUserFixesWithANewPassword() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(dav, null)

        store.failure = SyncAuthException("The server no longer accepts this password")
        assertEquals(SyncResult.Failed(SyncProblem.Auth), a.sync())
        val failed = a.status()
        assertTrue(failed is SyncStatus.Error && failed.problem == SyncProblem.Auth && failed.backend == dav)
        assertEquals(Instant.EPOCH, failed.attentionFrom())

        // The new password is checked against the remembered address and user; a round then mends it.
        store.failure = null
        a.repository.updateWebDavPassword("new-app-password")
        assertEquals(listOf("${dav.url}|alice|new-app-password"), webDavConnects)
        assertSynced(a.sync())
        assertTrue(a.status() is SyncStatus.Idle)
    }

    @Test
    fun updatingThePasswordNeedsAWebDavLocation() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(SyncBackend.Folder("/x"), null)
        assertFailsWith<IllegalStateException> { a.repository.updateWebDavPassword("x") }
    }

    @Test
    fun stoppingGivesThePasswordUpAndDeletingTheSyncDataToo() = runTest {
        val a = node("A")
        collection(a)
        a.repository.create(dav, null)
        a.repository.leave()
        assertEquals(listOf<SyncBackend>(dav), released)
        assertTrue(a.status() is SyncStatus.Off)

        released.clear()
        val b = node("B")
        b.deck("Other")
        store.paths.forEach(store::delete)
        b.repository.create(dav, null)
        b.repository.deleteSyncData()
        assertEquals(listOf<SyncBackend>(dav), released)
        assertTrue(store.paths.isEmpty())
    }

    @Test
    fun aCancelledSignInIsNotAFailureOfTheLocation() {
        assertEquals(SyncProblem.SignInCancelled, SyncSignInCancelledException().syncProblem())
    }

    private companion object {
        val POLICY = SyncPolicy(snapshotAfterFiles = 3, maintenanceEvery = Duration.ZERO)
    }
}
