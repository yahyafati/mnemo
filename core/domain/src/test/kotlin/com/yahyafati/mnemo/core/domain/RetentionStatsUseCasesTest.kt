package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.model.RetrievabilityBucket
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeStatsRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetentionStatsUseCasesTest {
    private val clock = TestClock(T0) // 2026-01-01 09:00 UTC
    private val today = LocalDate.of(2026, 1, 1)
    private val stats = FakeStatsRepository()
    private val reviews = FakeReviewRepository()
    private val decks = FakeDeckRepository()
    private val settings = FakeUserSettingsRepository(UserSettings(desiredRetention = 0.9))
    private val compute = ComputeRetentionStatsUseCase(stats, reviews, decks, settings, clock)
    private val overview = GetRetentionOverviewUseCase(stats, settings, clock)

    private val windowStart = StudyDay.start(today.minusDays(29), ZoneOffset.UTC)
    private val previousStart = StudyDay.start(today.minusDays(59), ZoneOffset.UTC)

    private fun deck(id: String, name: String, parentId: String? = null) =
        Deck(id = id, name = name, parentId = parentId, createdAt = T0, updatedAt = T0)

    @Test
    fun `no reviews yet`() = runTest {
        val result = compute().first()
        assertFalse(result.hasReviews)
        assertNull(result.retention.rate)
        assertNull(result.averageStability)
        assertEquals(Duration.ZERO, result.timeSaved)
        assertEquals(42, result.activity.size)
        assertEquals(today, result.activity.last().date)
        assertEquals(14, result.forecast.size)
    }

    @Test
    fun `retention, volume, stability and time saved`() = runTest {
        stats.passCounts.value = mapOf(windowStart to ReviewPassCounts(200, 188), previousStart to ReviewPassCounts(100, 90))
        stats.dailyReviews.value = listOf(
            DailyReviews(today.minusDays(40), 1_000), // outside the 30-day window
            DailyReviews(today.minusDays(3), 40),
            DailyReviews(today, 60),
        )
        stats.dailyReviewBaseline.value = 1_100
        reviews.averageAnswerMs.value = 8_000.0
        reviews.studyDates.value = listOf(today, today.minusDays(1), today.minusDays(3))
        stats.deckMaturity.value = listOf(
            DeckMaturity("a", newCards = 5, learning = 1, young = 2, mature = 1, reviewStabilitySum = 36.0),
            DeckMaturity("b", newCards = 0, learning = 0, young = 1, mature = 0, reviewStabilitySum = 4.0),
        )

        val result = compute().first()
        assertTrue(result.hasReviews)
        assertEquals(0.94, result.retention.rate!!, 1e-9)
        assertEquals(0.9, result.previousRetention.rate!!, 1e-9)
        assertEquals(100, result.reviewsLast30Days)
        assertEquals(10.0, result.averageStability!!, 1e-9)
        // (1,100 daily-routine answers − 100 given) × 8 s
        assertEquals(Duration.ofSeconds(8_000), result.timeSaved)
        assertEquals(2, result.streakDays)
        assertEquals(60, result.activity.last().reviews)
        assertEquals(0, result.activity[result.activity.size - 2].reviews)
    }

    @Test
    fun `subdecks roll up into their top-level deck`() = runTest {
        decks.addDeck(deck("lang", "Languages"))
        decks.addDeck(deck("ja", "Japanese", parentId = "lang"))
        decks.addDeck(deck("bio", "Biology"))
        stats.deckMaturity.value = listOf(
            DeckMaturity("lang", 1, 0, 1, 0, 3.0),
            DeckMaturity("ja", 0, 2, 3, 4, 100.0),
            DeckMaturity("bio", 3, 0, 0, 0, 0.0),
        )
        // A ratio of 1 is recall at 90% (one stability after the review); 0 is 100%.
        stats.retrievability.value = listOf(
            RetrievabilityBucket("lang", cards = 1, meanElapsedRatio = 0.0),
            RetrievabilityBucket("ja", cards = 3, meanElapsedRatio = 1.0),
        )

        val result = compute().first().decks
        assertEquals(listOf("Languages", "Biology"), result.map { it.name })
        val languages = result.first().maturity
        assertEquals(listOf(1, 2, 4, 4), listOf(languages.newCards, languages.learning, languages.young, languages.mature))
        assertEquals((1.0 + 3 * 0.9) / 4, result.first().recallNow!!, 1e-9)
        assertNull(result.last().recallNow)
    }

    @Test
    fun `forecast fills empty days`() = runTest {
        stats.dueForecast.value = listOf(DueForecast(today, 12), DueForecast(today.plusDays(2), 5))
        val forecast = compute().first().forecast
        assertEquals(listOf(12, 0, 5, 0), forecast.take(4).map { it.cards })
        assertEquals(today.plusDays(13), forecast.last().date)
    }

    @Test
    fun `hardest cards need two lapses`() = runTest {
        val once = studyCard("once", CardState.Review).let { it.copy(card = it.card.copy(lapses = 1)) }
        val often = studyCard("often", CardState.Review).let { it.copy(card = it.card.copy(lapses = 9)) }
        stats.mostLapsed.value = listOf(often, once)
        assertEquals(listOf("often"), compute().first().hardestCards.map { it.card.id })
    }

    @Test
    fun `forgetting curve keeps recall near the target and resets at each review`() = runTest {
        val curve = compute().first().forgettingCurve
        // Default weights at 90%: reviews on days 2, 13 and 59, as in py-fsrs's test vectors.
        assertEquals(listOf(2, 13, 59), curve.reviewDays)
        assertEquals(1.0, curve.scheduled.first().recall, 1e-12)
        assertTrue(curve.scheduled.all { it.recall >= 0.88 }, "recall stays near the 90% target")
        // Left alone after a first Good (stability 2.3065 days), recall is 60.3% by day 60.
        assertEquals(0.6033, curve.passive.last().recall, 1e-4)
        assertTrue(curve.scheduledAverage > curve.passiveAverage)
        curve.reviewDays.forEach { day ->
            // Just before the review the curve is at its lowest; at the review it is back to 1.
            val atReview = curve.scheduled.filter { it.day == day.toDouble() }.map { it.recall }
            assertEquals(2, atReview.size)
            assertEquals(1.0, atReview.last(), 1e-12)
        }
    }

    @Test
    fun `fitted weights change the model`() = runTest {
        val defaults = compute().first().forgettingCurve
        settings.setFsrsWeights(
            FsrsWeights(
                values = FsrsParameters.DEFAULT_WEIGHTS.toMutableList().also { it[2] = 10.0 },
                optimizedAt = T0, trainingReviews = 600, previousLoss = 0.5, loss = 0.4,
            ),
        )
        val fitted = compute().first()
        assertEquals(600, fitted.fsrsWeights?.trainingReviews)
        // A stronger first Good pushes the first review later.
        assertTrue(fitted.forgettingCurve.reviewDays.first() > defaults.reviewDays.first())
    }

    @Test
    fun `overview for the decks screen`() = runTest {
        stats.passCounts.value = mapOf(windowStart to ReviewPassCounts(50, 45))
        stats.deckMaturity.value = listOf(DeckMaturity("a", 0, 0, 3, 7, 300.0), DeckMaturity("b", 0, 0, 0, 2, 50.0))
        stats.retrievability.value = listOf(
            RetrievabilityBucket("a", cards = 2, meanElapsedRatio = 1.0),
            RetrievabilityBucket("a", cards = 2, meanElapsedRatio = 0.0),
        )

        val result = overview().first()
        assertEquals(0.9, result.retention!!, 1e-9)
        assertEquals(9, result.matureCards)
        assertEquals(0.95, result.deckRecall.getValue("a").average!!, 1e-9)
        assertNull(result.deckRecall["b"])
    }
}
