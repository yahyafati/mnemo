package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteSource
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** The Room-backed repositories against an in-memory database. */
class OfflineRepositoriesTest : PlatformTest() {
    private val db = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val transaction = RoomTransactionRunner(db)
    private val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    private val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    private val reviews = OfflineReviewRepository(db.cardDao(), db.reviewLogDao(), transaction, clock)

    @After
    fun tearDown() = db.close()

    @Test
    fun nestedPathCreatesParentsOnce() = runTest {
        val child = decks.saveDeck("Languages::Japanese")
        val sibling = decks.saveDeck("languages :: Spanish")
        val all = decks.getDecks()
        assertEquals(3, all.size)
        val parent = all.single { it.name == "Languages" }
        assertEquals(parent.id, decks.getDeck(child)?.parentId)
        assertEquals(parent.id, decks.getDeck(sibling)?.parentId)
        assertEquals(
            setOf("Languages", "Languages::Japanese", "Languages::Spanish"),
            decks.observeDeckSummaries().first().map { it.path }.toSet(),
        )
    }

    @Test
    fun deletingADeckDeletesSubdecksNotesAndCards() = runTest {
        val child = decks.saveDeck("Parent::Child")
        val parent = decks.getDeck(child)!!.parentId!!
        cards.addNote(child, NoteKind.Basic, listOf("q", "a"), emptyList())
        decks.deleteDeck(parent)
        assertTrue(decks.getDecks().isEmpty())
        assertEquals(0, cards.observeTotalCardCount().first())
    }

    @Test
    fun addNotesWritesAllNotesAndCardsOrNone() = runTest {
        val deck = decks.saveDeck("Deck")
        val notes = cards.addNotes(
            deck,
            listOf(NewNote(NoteKind.Basic, listOf("q", "a"), listOf("ai")), NewNote(NoteKind.Cloze, listOf("{{c1::x}} {{c2::y}}", ""))),
            NoteSource.Ai,
        )
        assertEquals(3, cards.observeTotalCardCount().first())
        assertEquals(listOf(NoteSource.Ai, NoteSource.Ai), notes.map { cards.getNote(it.id)?.source })
        assertEquals(listOf(listOf("q", "a"), listOf("{{c1::x}} {{c2::y}}", "")), cards.getNoteFields(deck))
        assertEquals(emptyList(), cards.getNoteFields(decks.saveDeck("Other")))

        // A note that makes no cards fails the whole batch before anything is written.
        runCatching { cards.addNotes(deck, listOf(NewNote(NoteKind.Basic, listOf("ok", "a")), NewNote(NoteKind.Cloze, listOf("none", ""))), NoteSource.Ai) }
        assertEquals(3, cards.observeTotalCardCount().first())
    }

    @Test
    fun clozeEditsAddAndRemoveCardsButKeepSchedules() = runTest {
        val deck = decks.saveDeck("Deck")
        val note = cards.addNote(deck, NoteKind.Cloze, listOf("{{c1::a}} {{c2::b}}", ""), listOf("tag"))
        assertEquals(2, cards.observeTotalCardCount().first())

        cards.updateNote(note.id, deck, listOf("{{c1::a}} {{c3::c}}", ""), emptyList(), hint = "h")
        val candidates = cards.getQueueCandidates(listOf(deck), clock.now(), clock.now().plusSeconds(3600), 10, 10)
        assertEquals(listOf(0, 2), candidates.new.map { it.card.templateOrd }.sorted())
        assertEquals(emptyList(), cards.getNote(note.id)?.tags)
        assertEquals("h", cards.getNote(note.id)?.hint)
    }

    @Test
    fun newKindsHintsExamDatesAndDeletes() = runTest {
        val deck = decks.saveDeck("Deck", examDate = java.time.LocalDate.of(2026, 12, 1))
        assertEquals(java.time.LocalDate.of(2026, 12, 1), decks.getDeck(deck)?.examDate)
        val typed = cards.addNote(deck, NoteKind.TypeIn, listOf("Capital of Peru?", "Lima"), emptyList(), hint = "L…")
        val choice = cards.addNote(deck, NoteKind.MultipleChoice, listOf("2 + 2?", "4", "3\n5"), emptyList())
        assertEquals("L…", cards.getNote(typed.id)?.hint)
        assertEquals(listOf("2 + 2?", "4", "3\n5"), cards.getNote(choice.id)?.fields)
        assertEquals(2, cards.observeTotalCardCount().first())

        cards.deleteNote(typed.id)
        assertEquals(null, cards.getNote(typed.id))
        assertEquals(1, cards.observeTotalCardCount().first())
        assertEquals(listOf(choice.id), cards.getNotesInDecks(listOf(deck), 10).map { it.id })

        // Saving the deck by path again keeps the exam date; an edit by id sets it.
        decks.saveDeck("Deck")
        assertEquals(java.time.LocalDate.of(2026, 12, 1), decks.getDeck(deck)?.examDate)
        decks.saveDeck("Deck", id = deck, examDate = null)
        assertEquals(null, decks.getDeck(deck)?.examDate)
    }

    @Test
    fun answerAndUndoAreAtomicPairs() = runTest {
        val deck = decks.saveDeck("Deck")
        cards.addNote(deck, NoteKind.Basic, listOf("q", "a"), emptyList())
        val card = cards.getQueueCandidates(listOf(deck), clock.now(), clock.now(), 0, 10).new.single().card
        val answered = card.copy(state = CardState.Learning, step = 1, due = clock.now().plus(Duration.ofMinutes(10)), reps = 1)
        val log = ReviewLog("log", card.id, Rating.Good, CardState.New, clock.now(), 0, 0, 3_000, 2.3, 5.0)

        reviews.recordAnswer(answered, log)
        assertEquals(CardState.Learning, cards.getCard(card.id)?.state)
        assertEquals(1, reviews.getTodayCounts().newStudied)
        assertEquals(1, decks.observeDeckSummaries().first().single().learningCount)

        reviews.undoAnswer(card, log.id)
        assertEquals(CardState.New, cards.getCard(card.id)?.state)
        assertEquals(0, reviews.getTodayCounts().total)
    }

    @Test
    fun buriedCardsReturnTheNextDay() = runTest {
        val deck = decks.saveDeck("Deck")
        cards.addNote(deck, NoteKind.Basic, listOf("q", "a"), emptyList())
        val card = cards.getQueueCandidates(listOf(deck), clock.now(), clock.now(), 0, 10).new.single().card
        cards.bury(card.id, clock.now().plus(Duration.ofHours(18)))
        assertTrue(cards.getQueueCandidates(listOf(deck), clock.now(), clock.now(), 0, 10).new.isEmpty())
        val tomorrow = clock.now().plus(Duration.ofDays(1))
        assertEquals(1, cards.getQueueCandidates(listOf(deck), tomorrow, tomorrow, 0, 10).new.size)
        assertNull(cards.getNote("missing"))
    }
}
