package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.data.repository.OfflineCardRepository
import com.yahyafati.mnemo.core.data.repository.OfflineDeckRepository
import com.yahyafati.mnemo.core.data.repository.OfflineReviewRepository
import com.yahyafati.mnemo.core.data.transfer.TransferTestCollection
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/**
 * The outbox through the real repositories (docs/sync/ROADMAP.md S1): whatever they write while sync is
 * on lands in `sync_changes`, with the columns that changed, without any of them knowing about sync.
 */
class SyncRecordingTest : PlatformTest() {
    private val db = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val transaction = RoomTransactionRunner(db)
    private val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    private val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    private val reviews = OfflineReviewRepository(db.cardDao(), db.reviewLogDao(), transaction, clock)

    @After
    fun tearDown() = db.close()

    /** What was recorded since the last call, as `table:fields` (the row ids are checked where they matter). */
    private suspend fun drain(): List<String> {
        val changes = db.syncDao().getChanges(10_000)
        changes.lastOrNull()?.let { db.syncDao().deleteChangesUpTo(it.seq) }
        return changes.map { "${it.tbl}:${it.fields}" }
    }

    @Test
    fun repositoryWritesAreRecordedWithTheColumnsTheyChanged() = runTest {
        db.syncDao().setEnabled(true)

        val deck = decks.saveDeck("Deck")
        assertEquals(listOf("decks:*"), drain())

        val note = cards.addNote(deck, NoteKind.Basic, listOf("q", "a"), emptyList())
        val card = db.cardDao().getCardsForNote(note.id).single()
        assertEquals(listOf("notes:*", "cards:*"), drain())

        clock.advanceBy(Duration.ofMinutes(1))
        cards.updateNote(note.id, deck, listOf("q2", "a"), listOf("t"), hint = null)
        assertEquals(listOf("notes:fields,tags"), drain())

        cards.setFlagged(card.id, true)
        cards.setStarred(card.id, true)
        cards.setSuspended(card.id, true)
        cards.bury(card.id, clock.now().plus(Duration.ofHours(8)))
        assertEquals(listOf("cards:flagged", "cards:starred", "cards:suspended", "cards:buriedUntil"), drain())

        decks.setStarred(deck, true)
        decks.saveDeck("Renamed", id = deck)
        assertEquals(listOf("decks:starred", "decks:name"), drain())

        val before = cards.getCard(card.id)!!
        val answered = before.copy(
            state = CardState.Learning, step = 1, due = clock.now().plus(Duration.ofMinutes(10)), stability = 2.3, difficulty = 5.0,
            lastReview = clock.now(), reps = 1,
        )
        val log = ReviewLog("log", card.id, Rating.Good, CardState.New, clock.now(), 0, 0, 3_000, 2.3, 5.0)
        reviews.recordAnswer(answered, log)
        assertEquals(listOf("cards:state,due,stability,difficulty,step,lastReview,reps", "review_logs:*"), drain())

        reviews.undoAnswer(before, log.id)
        assertEquals(listOf("cards:state,due,stability,difficulty,step,lastReview,reps", "review_logs:deletedAt"), drain())
        assertEquals(listOf("log"), db.queryStrings("SELECT id FROM review_logs WHERE deletedAt IS NOT NULL"))

        cards.deleteNote(note.id)
        assertEquals(listOf("notes:deletedAt", "cards:deletedAt"), drain())

        decks.deleteDeck(deck)
        assertEquals(listOf("decks:deletedAt"), drain())
    }

    @Test
    fun nothingIsRecordedUntilSyncIsTurnedOn() = runTest {
        val deck = decks.saveDeck("Deck")
        cards.addNote(deck, NoteKind.Basic, listOf("q", "a"), emptyList())
        assertEquals(emptyList(), drain())
    }

    @Test
    fun anAnkiImportIsRecordedRowByRow() = runTest {
        TransferTestCollection(tmp.newFolder()).use { c ->
            val rowsBefore = c.db.syncedRowIds()
            c.db.syncDao().setEnabled(true)
            c.importer.import(TransferTestCollection.fixture("modern.apkg"), tmp.newFolder())

            val recorded = c.db.syncDao().getChanges(100_000).map { "${it.tbl}/${it.rowId}" }.toSet()
            val written = c.db.syncedRowIds() - rowsBefore
            assertTrue(written.size > 20, "the import wrote ${written.size} rows")
            assertEquals(emptySet(), written - recorded, "rows that were written without a change")
            assertTrue(written.any { it.startsWith("review_logs/") } && written.any { it.startsWith("media/") })
        }
    }
}
