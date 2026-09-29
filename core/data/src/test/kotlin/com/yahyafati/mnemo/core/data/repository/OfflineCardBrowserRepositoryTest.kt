package com.yahyafati.mnemo.core.data.repository

import androidx.paging.testing.asSnapshot
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.CardQuery
import com.yahyafati.mnemo.core.model.CardSort
import com.yahyafati.mnemo.core.model.CardStatus
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OfflineCardBrowserRepositoryTest {
    private val db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    private val clock = TestClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val transaction = RoomTransactionRunner(db)
    private val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    private val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    private val browser = OfflineCardBrowserRepository(db.cardDao(), db.noteDao(), db.deckDao(), transaction, clock)

    @After
    fun tearDown() = db.close()

    private suspend fun fronts(query: CardQuery) = browser.browse(query).asSnapshot().map { it.sides.front }

    @Test
    fun searchFilterAndSort() = runTest {
        val bio = decks.saveDeck("Science::Biology")
        val jp = decks.saveDeck("Japanese")
        cards.addNote(bio, NoteKind.Basic, listOf("Mitochondria", "ATP"), listOf("cells"))
        clock.advanceBy(Duration.ofMinutes(1))
        cards.addNote(jp, NoteKind.Reversed, listOf("猫", "cat"), listOf("animals"))

        assertEquals(listOf("猫", "cat", "Mitochondria"), fronts(CardQuery()))
        assertEquals(listOf("Mitochondria", "猫", "cat"), fronts(CardQuery(sort = CardSort.Oldest)))
        assertEquals(listOf("Mitochondria"), fronts(CardQuery(text = "atp")))
        // A parent deck includes its subdecks.
        val science = decks.observeDeckSummaries().first().single { it.path == "Science" }.deck.id
        assertEquals(listOf("Mitochondria"), fronts(CardQuery(deckId = science)))
        assertEquals(listOf("猫", "cat"), fronts(CardQuery(tag = "animals")))
        assertEquals(3, browser.count(CardQuery()).first())
        assertEquals(listOf("animals", "cells"), browser.tags())
        val first = browser.browse(CardQuery(text = "mito")).asSnapshot().single()
        assertEquals("Biology", first.deckName)
    }

    @Test
    fun bulkEdits() = runTest {
        val bio = decks.saveDeck("Biology")
        val jp = decks.saveDeck("Japanese")
        cards.addNote(bio, NoteKind.Basic, listOf("Mitochondria", "ATP"), listOf("cells"))
        cards.addNote(bio, NoteKind.Reversed, listOf("猫", "cat"), emptyList())
        val all = browser.cardIds(CardQuery())
        val cat = browser.cardIds(CardQuery(text = "猫"))
        assertEquals(3, all.size)

        browser.setSuspended(cat, true)
        assertEquals(listOf("cat", "猫").toSet(), fronts(CardQuery(status = CardStatus.Suspended)).toSet())
        browser.setFlagged(all, true)
        assertEquals(3, browser.count(CardQuery(status = CardStatus.Flagged)).first())

        browser.moveToDeck(cat, jp)
        assertEquals(2, browser.count(CardQuery(deckId = jp)).first())
        val note = cards.getNote(cards.getStudyCards(cat).first().note.id)!!
        assertEquals(jp, note.deckId)

        browser.addTag(all, "exam")
        browser.addTag(all, "EXAM")
        browser.removeTag(all, "cells")
        assertEquals(listOf("exam"), browser.tags())

        browser.deleteNotes(cat)
        assertEquals(listOf("Mitochondria"), fronts(CardQuery()))
        assertTrue(cards.getStudyCards(cat).isEmpty())
    }
}
