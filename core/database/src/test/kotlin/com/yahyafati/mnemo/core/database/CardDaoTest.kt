package com.yahyafati.mnemo.core.database

import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.entity.CardEntity
import com.yahyafati.mnemo.core.database.entity.DeckEntity
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CardDaoTest {
    private lateinit var db: MnemoDatabase
    private val now = 1_000_000_000L
    private val dayEnd = now + 3_600_000L

    @Before
    fun setUp() {
        db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    }

    @After
    fun tearDown() = db.close()

    private fun card(
        id: String,
        state: Int,
        due: Long,
        deckId: String = "d1",
        suspended: Boolean = false,
        buriedUntil: Long? = null,
        createdAt: Long = 0,
    ) = CardEntity(
        id = id, noteId = "n-$id", deckId = deckId, templateOrd = 0, state = state, due = due,
        stability = null, difficulty = null, step = null, lastReview = null, reps = 0, lapses = 0,
        flagged = false, starred = false, suspended = suspended, buriedUntil = buriedUntil,
        createdAt = createdAt, updatedAt = 0,
    )

    @Test
    fun seedsBuiltInNoteTypes() = runTest {
        assertEquals(NoteType.BuiltIns.map { it.id }.toSet(), db.noteDao().getNoteTypes().map { it.id }.toSet())
    }

    @Test
    fun queueQueriesRespectStateDueAndHiddenCards() = runTest {
        db.cardDao().insert(
            listOf(
                card("review-due", state = 2, due = now - 10),
                card("review-later-today", state = 2, due = dayEnd - 1),
                card("review-tomorrow", state = 2, due = dayEnd + 1),
                card("learning", state = 1, due = now + 60_000),
                card("new-2", state = 0, due = now, createdAt = 2),
                card("new-1", state = 0, due = now, createdAt = 1),
                card("suspended", state = 2, due = now, suspended = true),
                card("buried", state = 2, due = now, buriedUntil = now + 1),
                card("other-deck", state = 2, due = now, deckId = "d2"),
            ),
        )
        val cards = db.cardDao()
        assertEquals(
            listOf("review-due", "review-later-today"),
            cards.getReviewCards(listOf("d1"), now, dayEnd, limit = 10).map { it.id },
        )
        assertEquals(listOf("review-due"), cards.getReviewCards(listOf("d1"), now, dayEnd, limit = 1).map { it.id })
        assertEquals(listOf("learning"), cards.getLearningCards(listOf("d1"), now, dayEnd).map { it.id })
        assertEquals(listOf("new-1", "new-2"), cards.getNewCards(listOf("d1"), now, limit = 10).map { it.id })
    }

    @Test
    fun deckCountsAggregatePerDeck() = runTest {
        db.deckDao().upsert(DeckEntity("d1", null, "Deck", "", null, false, 0, 0))
        db.deckDao().upsert(DeckEntity("d2", null, "Empty", "", null, false, 0, 0))
        db.cardDao().insert(
            listOf(
                card("a", state = 2, due = now),
                card("b", state = 3, due = now),
                card("c", state = 0, due = now),
                card("d", state = 2, due = now, suspended = true),
            ),
        )
        val counts = db.deckDao().observeDeckCounts(now, dayEnd).first().associateBy { it.deckId }
        with(counts.getValue("d1")) {
            assertEquals(1, reviewDue)
            assertEquals(1, learningDue)
            assertEquals(1, newCount)
            assertEquals(4, total)
        }
        assertEquals(0, counts.getValue("d2").total)
    }

    @Test
    fun reviewCountsAndDays() = runTest {
        val day = 86_400_000L
        listOf(
            Triple("r1", 0, 10 * day + 5),
            Triple("r2", 2, 10 * day + 6),
            Triple("r3", 2, 9 * day + 1),
        ).forEach { (id, stateBefore, at) ->
            db.reviewLogDao().insert(ReviewLogEntity(id, "c", 3, stateBefore, at, 0, 1, 1000, 1.0, 5.0, at, at))
        }
        assertEquals(listOf(10L, 9L), db.reviewLogDao().observeReviewDays(offsetMs = 0).first())
        val today = db.reviewLogDao().getCountsSince(10 * day)
        assertEquals(1, today.newStudied)
        assertEquals(1, today.reviewsDone)
        assertEquals(2, today.total)
    }
}
