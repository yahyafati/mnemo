package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.UserSettings
import java.time.Duration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudySessionTest {
    private val dayEnd = T0.plus(Duration.ofHours(19))
    private val scheduler = StudyScheduler(UserSettings())

    @Test
    fun `new cards are spread through reviews`() {
        assertEquals(listOf("n", "r", "n", "r"), StudySession.interleave(listOf("r", "r"), listOf("n", "n")))
        assertEquals(listOf("r", "n", "r", "r"), StudySession.interleave(listOf("r", "r", "r"), listOf("n")))
        assertEquals(listOf("n"), StudySession.interleave(emptyList(), listOf("n")))
    }

    @Test
    fun `due learning cards come first`() {
        val session = StudySession.create(
            learning = listOf(studyCard("l", CardState.Learning, due = T0.minusSeconds(1))),
            review = listOf(studyCard("r", CardState.Review)),
            new = emptyList(),
            dayEnd = dayEnd,
        )
        assertEquals("l", session.next(T0)?.card?.id)
    }

    @Test
    fun `again sends a card back into learning and it returns once due`() {
        val card = studyCard("a")
        var session = StudySession.create(emptyList(), emptyList(), listOf(card, studyCard("b")), dayEnd)

        session = session.afterAnswer(scheduler.answer(card.card, Rating.Again, T0))
        assertEquals(1, session.answeredCount)
        assertEquals(listOf("a"), session.learning.map { it.card.id })
        // "a" is due in a minute, so "b" comes first...
        assertEquals("b", session.next(T0)?.card?.id)

        val b = session.next(T0)!!
        session = session.afterAnswer(scheduler.answer(b.card, Rating.Easy, T0))
        // ...then "a" is shown early: it is due within the learn-ahead window.
        assertEquals("a", session.next(T0)?.card?.id)
        assertEquals(1, session.remainingCount)
        assertEquals(2, session.answeredCount)
    }

    @Test
    fun `graduated cards leave the session`() {
        val card = studyCard("a")
        val session = StudySession.create(emptyList(), emptyList(), listOf(card), dayEnd)
            .afterAnswer(scheduler.answer(card.card, Rating.Easy, T0))
        assertNull(session.next(T0))
        assertEquals(0, session.remainingCount)
    }

    @Test
    fun `learning cards far in the future wait`() {
        val later = studyCard("l", CardState.Learning, due = T0.plus(Duration.ofHours(2)))
        val session = StudySession.create(listOf(later), emptyList(), emptyList(), dayEnd)
        assertNull(session.next(T0))
        assertEquals(1, session.laterCount(T0))
        assertEquals("l", session.next(T0.plus(Duration.ofHours(2)))?.card?.id)
    }

    @Test
    fun `refresh swaps note content but keeps schedule`() {
        val card = studyCard("a")
        val session = StudySession.create(emptyList(), emptyList(), listOf(card), dayEnd)
        val edited = card.copy(note = card.note.copy(fields = listOf("new", "text")), card = card.card.copy(reps = 99))
        val refreshed = session.refreshed(listOf(edited))
        assertEquals(listOf("new", "text"), refreshed.queue.single().note.fields)
        assertEquals(0, refreshed.queue.single().card.reps)
    }
}
