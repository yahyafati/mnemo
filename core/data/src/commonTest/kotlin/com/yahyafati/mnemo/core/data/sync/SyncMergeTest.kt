package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.database.entity.MediaEntity
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.data.repository.NewNote
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import com.yahyafati.mnemo.core.sync.store.InMemorySyncStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The merge, one scenario at a time (docs/sync/ROADMAP.md S3): devices with their own database and clock, one shared
 * location, and the question after every scenario is whether they hold the same rows.
 */
class SyncMergeTest : PlatformTest() {
    private val store = InMemorySyncStore()
    private val devices = ArrayList<SyncDevice>()
    private val t0 = Instant.parse("2026-03-01T09:00:00Z")

    private suspend fun device(name: String, on: com.yahyafati.mnemo.core.sync.SyncStore = store): SyncDevice = SyncDevice(name, store).also {
        it.turnOn()
        devices += it
    }

    @After
    fun tearDown() = devices.forEach { it.close() }

    /** Syncs the devices in turn until a whole round has nothing to send or apply. */
    private suspend fun syncAll(vararg synced: SyncDevice, announce: SyncDevice? = null, location: com.yahyafati.mnemo.core.sync.SyncStore = store) {
        if (runCatching { SyncRemote.readManifest(location) }.isFailure) SyncRemote.create(location, "collection", 0)
        repeat(10) { round ->
            val reports = synced.map { it.sync(SyncRemote.open(location), announceSettings = round == 0 && it == announce) }
            if (reports.all { it.isQuiet }) return
        }
        error("The devices never settled")
    }

    private suspend fun assertSame(vararg synced: SyncDevice) {
        val first = synced.first().dump()
        for (other in synced.drop(1)) {
            val second = other.dump()
            val differences = (first - second.toSet()).map { "${synced.first().name}: $it" } + (second - first.toSet()).map { "${other.name}: $it" }
            assertTrue(differences.isEmpty(), "The devices differ:\n" + differences.joinToString("\n"))
        }
    }

    @Test
    fun aCollectionCreatedOnOneDeviceArrivesOnTheOther() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Spanish")
        a.note(deck, "hola", "hello")

        syncAll(a, b)

        assertSame(a, b)
        assertTrue(b.dump().any { it.startsWith("notes/") })
    }

    // --- the scenarios of the roadmap -----------------------------------------------------------------------

    @Test
    fun theSameCardReviewedOnBothDevicesKeepsBothReviewsAndReplaysTheSchedule() = runTest {
        val a = device("A")
        val b = device("B")
        val (_, card) = a.note(a.deck("Deck"), "q", "a")
        syncAll(a, b)

        a.clock.set(t0.plusSeconds(3_600))
        b.clock.set(t0.plusSeconds(3_900))
        val first = a.answer(card, Rating.Good)
        val second = b.answer(card, Rating.Again)
        syncAll(a, b)

        assertSame(a, b)
        assertEquals(setOf(first.log.id, second.log.id), a.liveLogs(card).toSet())
        // What one device would have: the later answer on top of the earlier one.
        val scheduler = TestScheduler()
        val afterFirst = scheduler.answer(first.previous, Rating.Good, first.log.reviewedAt).first
        val expected = scheduler.answer(afterFirst, Rating.Again, second.log.reviewedAt).first
        for (device in listOf(a, b)) {
            val actual = device.cards.getCard(card)!!
            assertEquals(2, actual.reps)
            assertEquals(expected.state, actual.state)
            assertEquals(expected.due, actual.due)
            assertEquals(expected.stability, actual.stability)
            assertEquals(expected.difficulty, actual.difficulty)
            assertEquals(expected.lastReview, actual.lastReview)
            assertEquals(expected.lapses, actual.lapses)
        }
    }

    @Test
    fun theFrontEditedOnOneDeviceAndTheBackOnTheOtherKeepBothEdits() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, _) = a.note(deck, "front", "back")
        syncAll(a, b)

        a.cards.updateNote(note, deck, listOf("front edited on A", "back"), emptyList(), null)
        b.cards.updateNote(note, deck, listOf("front", "back edited on B"), emptyList(), null)
        syncAll(a, b)

        assertSame(a, b)
        assertEquals(listOf("front edited on A", "back edited on B"), a.cards.getNote(note)!!.fields)
    }

    @Test
    fun theSameFieldEditedOnBothDevicesTakesTheLaterClockOnBoth() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, _) = a.note(deck, "front", "back")
        syncAll(a, b)

        b.clock.set(t0.plusSeconds(600))
        a.cards.updateNote(note, deck, listOf("from A", "back"), emptyList(), null)
        b.cards.updateNote(note, deck, listOf("from B", "back"), emptyList(), null)
        syncAll(a, b)
        assertSame(a, b)
        assertEquals("from B", a.cards.getNote(note)!!.fields[0])

        // The other way round: now A's clock is the later one.
        a.clock.set(t0.plusSeconds(7_200))
        a.cards.updateNote(note, deck, listOf("from A again", "back"), emptyList(), null)
        b.cards.updateNote(note, deck, listOf("from B again", "back"), emptyList(), null)
        syncAll(a, b)
        assertSame(a, b)
        assertEquals("from A again", b.cards.getNote(note)!!.fields[0])
    }

    @Test
    fun aClockADayAheadDoesNotLetItsEditOutlastALaterOne() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, _) = a.note(deck, "front", "back")
        syncAll(a, b)

        a.clock.set(t0.plus(Duration.ofDays(1)))
        a.cards.updateNote(note, deck, listOf("A, a day ahead", "back"), emptyList(), null)
        syncAll(a, b)
        assertEquals("A, a day ahead", b.cards.getNote(note)!!.fields[0])

        // B's own clock is a day behind A's, but it has seen A's change, so what it does next orders after it.
        b.cards.updateNote(note, deck, listOf("B, afterwards", "back"), emptyList(), null)
        syncAll(a, b)

        assertSame(a, b)
        assertEquals("B, afterwards", a.cards.getNote(note)!!.fields[0])
    }

    @Test
    fun deletedOnOneDeviceAndEditedOnTheOtherIsDeletedOnBoth() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, card) = a.note(deck, "front", "back")
        syncAll(a, b)

        a.cards.deleteNote(note)
        b.cards.updateNote(note, deck, listOf("edited", "back"), listOf("tag"), null)
        syncAll(a, b)

        assertSame(a, b)
        assertNull(a.cards.getNote(note))
        assertNull(b.cards.getNote(note))
        assertNull(b.cards.getCard(card))
    }

    @Test
    fun aDeckCreatedOnBothDevicesBecomesOneWithAllItsNotes() = runTest {
        val a = device("A")
        val b = device("B")
        val onA = a.deck("Spanish")
        val onB = b.deck("spanish")
        assertTrue(onA != onB)
        val (noteA, _) = a.note(onA, "uno", "one")
        val (noteB, _) = b.note(onB, "dos", "two")

        syncAll(a, b)

        assertSame(a, b)
        for (device in listOf(a, b)) {
            val live = device.decks.getDecks()
            assertEquals(1, live.size, "${device.name}: ${live.map { it.name }}")
            assertEquals(minOf(onA, onB), live.single().id)
            assertEquals(live.single().id, device.cards.getNote(noteA)!!.deckId)
            assertEquals(live.single().id, device.cards.getNote(noteB)!!.deckId)
        }
    }

    @Test
    fun anUndoAfterTheReviewWasSyncedUndoesItOnTheOtherDevice() = runTest {
        val a = device("A")
        val b = device("B")
        val (_, card) = a.note(a.deck("Deck"), "q", "a")
        syncAll(a, b)
        val original = b.cards.getCard(card)!!

        a.clock.set(t0.plusSeconds(3_600))
        val answered = a.answer(card, Rating.Good)
        syncAll(a, b)
        assertEquals(CardState.Learning, b.cards.getCard(card)!!.state)
        assertEquals(listOf(answered.log.id), b.liveLogs(card))

        a.undo(answered)
        syncAll(a, b)

        assertSame(a, b)
        assertEquals(emptyList(), b.liveLogs(card))
        assertEquals(original.state, b.cards.getCard(card)!!.state)
        assertEquals(original.due, b.cards.getCard(card)!!.due)
        assertEquals(0, b.cards.getCard(card)!!.reps)
    }

    /**
     * The known limit of the replay: B answers on top of A's answer while A undoes it before syncing. Both reviews are
     * kept (the undone one marked undone), and the devices agree, but the schedule is the one B computed on top of the
     * undone answer, since the schedule that A restored is older than it.
     */
    @Test
    fun anAnswerGivenOnTopOfAnUndoneOneKeepsEveryLogAndTheDevicesAgree() = runTest {
        val a = device("A")
        val b = device("B")
        val (_, card) = a.note(a.deck("Deck"), "q", "a")
        syncAll(a, b)

        a.clock.set(t0.plusSeconds(3_600))
        val first = a.answer(card, Rating.Good)
        syncAll(a, b)
        b.clock.set(t0.plusSeconds(7_200))
        val second = b.answer(card, Rating.Good)
        a.undo(first)
        syncAll(a, b)

        assertSame(a, b)
        assertEquals(listOf(second.log.id), a.liveLogs(card))
        assertEquals(setOf(first.log.id, second.log.id), a.db.queryStrings("SELECT id FROM review_logs").toSet())
    }

    @Test
    fun aCrashBetweenWritingTheFileAndClearingTheOutboxLosesNothingAndDoublesNothing() = runTest {
        val a = device("A")
        val b = device("B")
        val flaky = InterceptingStore(store)
        SyncRemote.create(flaky, "collection", 0)
        val deck = a.deck("Deck")
        val (note, _) = a.note(deck, "q", "a")

        flaky.failAfterNextChangeWrite = true
        assertFailsWith<SyncOfflineException> { a.sync(SyncRemote.open(flaky)) }
        assertTrue(a.db.syncDao().countChanges() > 0, "the changes are still waiting to be sent")

        syncAll(a, b, location = flaky)

        assertSame(a, b)
        assertEquals(listOf("q", "a"), b.cards.getNote(note)!!.fields)
        assertEquals(1, b.decks.getDecks().size)
        assertEquals(0, a.db.syncDao().countChanges())
    }

    @Test
    fun twelveThousandCardsImportedOnOneDeviceReachTheOther() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Big")
        a.cards.addNotes(deck, List(12_000) { NewNote(NoteKind.Basic, listOf("question $it", "answer $it")) }, NoteSource.Import)
        SyncRemote.create(store, "collection", 0)

        val sending = System.nanoTime()
        val sent = a.sync(SyncRemote.open(store))
        val receiving = System.nanoTime()
        val received = b.sync(SyncRemote.open(store))
        val done = System.nanoTime()
        syncAll(a, b)

        assertEquals(24_001, sent.sentChanges)
        assertEquals(24_001, received.receivedChanges)
        assertSame(a, b)
        assertEquals(12_000, b.db.queryStrings("SELECT COUNT(*) FROM notes").single().toInt())
        assertEquals(12_000, b.db.queryStrings("SELECT COUNT(*) FROM cards").single().toInt())
        println("sync: 12,000 cards: packed and written in ${(receiving - sending) / 1_000_000} ms in ${sent.sentFiles} files, applied in ${(done - receiving) / 1_000_000} ms")
    }

    // --- more than the roadmap lists ---------------------------------------------------------------------------

    @Test
    fun syncingAgainChangesNothing() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (_, card) = a.note(deck, "q", "a")
        a.answer(card, Rating.Good)
        syncAll(a, b)
        val before = b.dump()

        // B forgets what it applied, so it reads and applies every file again.
        b.db.syncDao().clearSeqs()
        val report = b.sync(SyncRemote.open(store))

        assertEquals(before, b.dump())
        assertTrue(report.receivedChanges > 0)
        assertEquals(0, b.db.syncDao().countChanges())
    }

    @Test
    fun anEditThatArrivesBeforeTheRowItEditsIsKeptUntilTheRowArrives() = runTest {
        val a = device("A")
        val b = device("B")
        val seen = InterceptingStore(store)
        SyncRemote.create(seen, "collection", 0)
        val deck = a.deck("Deck")
        val (note, card) = a.note(deck, "q", "a")
        a.sync(SyncRemote.open(seen))
        val firstFile = seen.list("devices/").single { it.endsWith("1.mnc") }

        a.clock.set(t0.plusSeconds(60))
        a.cards.updateNote(note, deck, listOf("q", "a, edited"), listOf("tag"), null)
        a.cards.setFlagged(card, true)
        a.sync(SyncRemote.open(seen))

        // The folder has delivered A's second file but not its first.
        seen.hidden += firstFile
        b.sync(SyncRemote.open(seen))
        assertNull(b.cards.getNote(note))
        seen.hidden.clear()
        // The first file turns up after the second was applied, as a sync tool may deliver them.
        syncAll(a, b, location = seen)

        assertSame(a, b)
        assertEquals(listOf("q", "a, edited"), b.cards.getNote(note)!!.fields)
        assertEquals(true, b.cards.getCard(card)!!.flagged)
    }

    @Test
    fun aChangeMadeWhileTheRunWasInProgressIsNotOverwrittenByTheRemoteOne() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, _) = a.note(deck, "q", "a")
        val spy = InterceptingStore(store)
        syncAll(a, b, location = spy)

        b.clock.set(t0.plusSeconds(60))
        b.cards.updateNote(note, deck, listOf("q", "a"), listOf("from-b"), null)
        syncAll(b, location = spy)
        // A edits the tags just after it has sent its changes and before it reads B's.
        spy.onListDevices = { runBlocking { a.cards.updateNote(note, deck, listOf("q", "a"), listOf("typed-on-a"), null) } }
        a.sync(SyncRemote.open(spy))
        assertEquals(listOf("typed-on-a"), a.cards.getNote(note)!!.tags)

        syncAll(a, b, location = spy)
        assertSame(a, b)
        assertEquals(listOf("typed-on-a"), b.cards.getNote(note)!!.tags)
    }

    @Test
    fun schedulingSettingsTravelAndAJoiningDeviceDoesNotOverwriteThem() = runTest {
        val a = device("A")
        val b = device("B")
        a.settings.setDesiredRetention(0.85)
        a.settings.setNewCardsPerDay(7)
        a.settings.setFsrsWeights(FsrsWeights(List(21) { 0.5 + it / 100.0 }, Instant.parse("2026-02-01T00:00:00Z"), 400, 0.5, 0.4))

        syncAll(a, b, announce = a)
        assertEquals(0.85, b.settings.settings.value.desiredRetention)
        assertEquals(7, b.settings.settings.value.newCardsPerDay)
        assertEquals(a.settings.settings.value.fsrsWeights, b.settings.settings.value.fsrsWeights)

        b.settings.setReviewsPerDay(50)
        b.settings.setLearningSteps(listOf(Duration.ofMinutes(2), Duration.ofMinutes(20)))
        syncAll(a, b)
        assertEquals(50, a.settings.settings.value.reviewsPerDay)
        assertEquals(listOf(Duration.ofMinutes(2), Duration.ofMinutes(20)), a.settings.settings.value.learningSteps)
        assertEquals(0.85, a.settings.settings.value.desiredRetention)
        assertSame(a, b)
    }

    @Test
    fun mediaFilesGoUpBeforeTheRowsAndComeDownOnTheOtherDevice() = runTest {
        val a = device("A")
        val b = device("B")
        val bytes = ByteArray(4_000) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        a.media.write(hash, bytes)
        a.db.mediaDao().upsert(listOf(MediaEntity(hash, "photo.png", "image/png", bytes.size.toLong(), 1, 1)))

        syncAll(a, b)

        assertSame(a, b)
        assertTrue(bytes.contentEquals(b.media.read(hash)))
    }

    @Test
    fun aNewerFormatStopsTheRunWithoutChangingAnything() = runTest {
        val a = device("A")
        val b = device("B")
        a.note(a.deck("Deck"), "q", "a")
        syncAll(a, b)
        a.cards.updateNote(a.cards.getNotesInDecks(a.decks.getDecks().map { it.id }, 1).single().id, a.decks.getDecks().single().id, listOf("q2", "a"), emptyList(), null)
        a.sync(SyncRemote.open(store))
        val newest = store.list("devices/").filter { "changes" in it && a.deviceId() in it }.max()
        // The envelope starts with "MNSY" and a 16-bit format version; make it one this version doesn't know.
        val bytes = store.read(newest).also { it[4] = 0x7f }
        store.overwrite(newest, bytes)
        val before = b.dump()

        assertFailsWith<SyncUnsupportedVersionException> { b.sync(SyncRemote.open(store)) }

        assertEquals(before, b.dump())
    }

    @Test
    fun workAddedToADeckThatAnotherDeviceDeletedIsKeptInARecoveredDeck() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Biology")
        val (older, _) = a.note(deck, "old", "note")
        syncAll(a, b)

        a.decks.deleteDeck(deck)
        val (newer, newerCard) = b.note(deck, "added", "meanwhile")
        syncAll(a, b)

        assertSame(a, b)
        for (device in listOf(a, b)) {
            val live = device.decks.getDecks()
            assertEquals(listOf("Biology (recovered)"), live.map { it.name })
            assertNull(device.cards.getNote(older), "the note that was in the deleted deck stays deleted")
            assertEquals(live.single().id, device.cards.getNote(newer)!!.deckId)
            assertEquals(live.single().id, device.cards.getCard(newerCard)!!.deckId)
        }
    }

    @Test
    fun aCardAddedToANoteThatAnotherDeviceDeletedIsDeletedWithIt() = runTest {
        val a = device("A")
        val b = device("B")
        val deck = a.deck("Deck")
        val (note, card) = a.note(deck, "q", "a")
        syncAll(a, b)

        a.cards.deleteNote(note)
        b.db.cardDao().insert(listOf(b.cards.getCard(card)!!.copy(id = "second-card", templateOrd = 1).toEntity()))
        syncAll(a, b)

        assertSame(a, b)
        assertNull(a.cards.getCard("second-card"))
        assertNull(b.cards.getCard("second-card"))
    }
}
