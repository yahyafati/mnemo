package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.UserSettings
import java.time.Duration
import org.junit.Test
import kotlin.test.assertEquals

class StudySchedulerTest {
    private val scheduler = StudyScheduler(UserSettings())

    @Test
    fun `preview matches the saved answer, fuzz included`() {
        val card = studyCard("r", CardState.Review, due = T0)
        val preview = scheduler.preview(card.card, T0)
        Rating.entries.forEach { rating ->
            val answer = scheduler.answer(card.card, rating, T0)
            assertEquals(preview.getValue(rating).card, answer.card)
        }
    }

    @Test
    fun `new card follows the learning steps`() {
        val card = studyCard("n").card
        val preview = scheduler.preview(card, T0)
        assertEquals(Duration.ofMinutes(1), preview.getValue(Rating.Again).interval)
        assertEquals(Duration.ofMinutes(10), preview.getValue(Rating.Good).interval)
        assertEquals(CardState.Learning, preview.getValue(Rating.Good).card.state)
        assertEquals(CardState.Review, preview.getValue(Rating.Easy).card.state)
    }

    @Test
    fun `forgetting a review card counts a lapse and logs the state before`() {
        val card = studyCard("r", CardState.Review, due = T0).card
        val answer = scheduler.answer(card, Rating.Again, T0, durationMs = 4_000)
        assertEquals(1, answer.card.lapses)
        assertEquals(1, answer.card.reps)
        assertEquals(CardState.Relearning, answer.card.state)
        assertEquals(CardState.Review, answer.log.stateBefore)
        assertEquals(1, answer.log.elapsedDays)
        assertEquals(4_000, answer.log.durationMs)
    }
}
