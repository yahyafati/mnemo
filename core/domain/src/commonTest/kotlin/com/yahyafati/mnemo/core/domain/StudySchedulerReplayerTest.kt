package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.UserSettings
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

class StudySchedulerReplayerTest {
    @Test
    fun `a replay gives the card the study screen would have saved`() {
        val settings = UserSettings(desiredRetention = 0.85, learningSteps = listOf(Duration.ofMinutes(2), Duration.ofMinutes(15)))
        val scheduler = StudyScheduler(settings)
        val answerer = StudySchedulerReplayer().answerer(settings)

        for (state in CardState.entries) {
            var expected = studyCard("replay-$state", state).card
            var replayed = expected
            var at = T0
            for (rating in listOf(Rating.Good, Rating.Again, Rating.Good, Rating.Easy, Rating.Hard)) {
                at = at.plus(Duration.ofHours(30))
                expected = scheduler.answer(expected, rating, at).card
                replayed = answerer.answer(replayed, rating, at)
                assertEquals(expected, replayed, "$state after $rating")
            }
        }
    }
}
