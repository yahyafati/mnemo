package com.yahyafati.mnemo.core.data.scheduling

import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.entity.ReviewLogEntity
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.scheduler.Fsrs
import com.yahyafati.mnemo.core.scheduler.FsrsCard
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.FsrsRating
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class FsrsOptimizationTest : PlatformTest() {
    private val db = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-06-01T10:00:00Z"))
    private val settings = FakeUserSettingsRepository()
    private val optimization = FsrsOptimization(db.reviewLogDao(), settings, clock, Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    /**
     * A learner whose memory decays faster than the defaults assume, studying [cards] cards on
     * schedule for a few months; recall is drawn from that learner's own forgetting curve.
     */
    private suspend fun simulateReviews(cards: Int) {
        val learner = Fsrs(
            FsrsParameters(
                weights = FsrsParameters.DEFAULT_WEIGHTS.toMutableList().also {
                    it[2] = 1.0 // weaker first Good
                    it[20] = 0.4 // steeper forgetting curve
                },
                desiredRetention = 0.85,
            ),
        )
        val random = Random(7)
        val start = clock.now().minus(Duration.ofDays(200))
        val logs = mutableListOf<ReviewLogEntity>()
        repeat(cards) { i ->
            var card = FsrsCard(due = start)
            var time = start.plus(Duration.ofHours(random.nextLong(0, 24L * 60)))
            repeat(12) { n ->
                if (time > clock.now()) return@repeat
                val rating = when {
                    card.lastReview == null -> FsrsRating.Good
                    random.nextDouble() < learner.retrievability(card, time) -> FsrsRating.Good
                    else -> FsrsRating.Again
                }
                card = learner.review(card, rating, time, random)
                logs += ReviewLogEntity(
                    id = "l$i-$n", cardId = "c$i", rating = rating.value, stateBefore = 2, reviewedAt = time.toEpochMilli(),
                    elapsedDays = 0, scheduledDays = 0, durationMs = 5_000, stabilityAfter = card.stability!!,
                    difficultyAfter = card.difficulty!!, createdAt = 0, updatedAt = 0,
                )
                time = card.due.plus(Duration.ofHours(random.nextLong(0, 30)))
            }
        }
        db.reviewLogDao().insertAll(logs)
    }

    @Test
    fun appliesBetterWeightsAndKeepsThemAfterward() = runTest {
        simulateReviews(cards = 220)
        val progress = mutableListOf<Float>()

        val outcome = assertIs<FsrsOptimizationOutcome.Applied>(optimization.run { progress += it })
        assertTrue(outcome.loss < outcome.previousLoss)
        assertTrue(outcome.trainingReviews >= 512)
        assertEquals(1f, progress.last())

        val saved = assertNotNull(settings.settings.first().fsrsWeights)
        assertEquals(clock.now(), saved.optimizedAt)
        assertEquals(outcome.loss, saved.loss)
        assertEquals(outcome.trainingReviews, saved.trainingReviews)
        // Valid weights: the scheduler accepts them.
        FsrsParameters(weights = saved.values)

        // Fitting the same history again can't beat what is already in use.
        assertIs<FsrsOptimizationOutcome.NoImprovement>(optimization.run())
        assertEquals(saved, settings.settings.first().fsrsWeights)
    }

    @Test
    fun tooFewReviewsChangesNothing() = runTest {
        simulateReviews(cards = 20)
        val outcome = assertIs<FsrsOptimizationOutcome.NotEnoughReviews>(optimization.run())
        assertEquals(512, outcome.required)
        assertTrue(outcome.trainingReviews in 1 until 512)
        assertNull(settings.settings.first().fsrsWeights)
    }
}
