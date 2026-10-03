package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.UserSettings
import java.time.Duration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `the log holds the schedule the answer produced`() {
        for (state in CardState.entries) {
            val card = studyCard("c-$state", state, due = T0).card
            for (rating in Rating.entries) {
                val answer = scheduler.answer(card, rating, T0)
                val log = answer.log
                assertEquals(answer.card.state, log.stateAfter, "$state $rating state")
                assertEquals(answer.card.step, log.stepAfter, "$state $rating step")
                assertEquals(answer.card.due, log.dueAfter, "$state $rating due")
                assertEquals(answer.card.reps, log.repsAfter, "$state $rating reps")
                assertEquals(answer.card.lapses, log.lapsesAfter, "$state $rating lapses")
                assertEquals(answer.card.stability, log.stabilityAfter, "$state $rating stability")
                assertEquals(answer.card.difficulty, log.difficultyAfter, "$state $rating difficulty")
            }
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

    @Test
    fun `fitted weights are used, and invalid ones fall back to the defaults`() {
        val card = studyCard("n").card
        val fitted = FsrsWeights(
            values = FsrsParameters.DEFAULT_WEIGHTS.toMutableList().also { it[3] = 30.0 },
            optimizedAt = T0, trainingReviews = 600, previousLoss = 0.5, loss = 0.4,
        )
        val default = scheduler.answer(card, Rating.Easy, T0).interval
        assertTrue(StudyScheduler(UserSettings(fsrsWeights = fitted)).answer(card, Rating.Easy, T0).interval > default)

        val broken = fitted.copy(values = FsrsParameters.DEFAULT_WEIGHTS.toMutableList().also { it[4] = -1.0 })
        assertEquals(default, StudyScheduler(UserSettings(fsrsWeights = broken)).answer(card, Rating.Easy, T0).interval)
    }
}
