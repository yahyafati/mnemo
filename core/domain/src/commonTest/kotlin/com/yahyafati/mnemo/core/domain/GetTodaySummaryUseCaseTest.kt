package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository.DeckCounts
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import org.junit.Test
import kotlin.test.assertEquals

class GetTodaySummaryUseCaseTest {
    private val clock = TestClock(T0) // 2026-01-01 09:00 UTC, a study day of 2026-01-01
    private val decks = FakeDeckRepository()
    private val reviews = FakeReviewRepository()
    private val summary = GetTodaySummaryUseCase(
        decks, FakeCardRepository(), reviews, FakeUserSettingsRepository(UserSettings(newCardsPerDay = 10)), clock,
    )
    private val today = LocalDate.of(2026, 1, 1)

    @Test
    fun `sums decks and caps new cards by the daily limit`() = runTest {
        decks.addDeck(Deck(id = "a", name = "A", createdAt = T0, updatedAt = T0))
        decks.addDeck(Deck(id = "b", name = "B", createdAt = T0, updatedAt = T0))
        decks.setCounts("a", DeckCounts(due = 5, new = 8, learning = 2))
        decks.setCounts("b", DeckCounts(due = 1, new = 8))
        reviews.averageAnswerMs.value = 6_000.0

        val result = summary().first()
        assertEquals(4, result.dueCount)
        assertEquals(2, result.learningCount)
        assertEquals(10, result.newCount)
        // (4 + 2 + 10 × 3) answers × 6 s = 3.6 min → 4
        assertEquals(4, result.estimatedMinutes)
    }

    @Test
    fun `streak counts consecutive days and survives until today ends`() = runTest {
        reviews.studyDates.value = listOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, summary().first().streakDays)

        reviews.studyDates.value = listOf(today, today.minusDays(1))
        assertEquals(2, summary().first().streakDays)

        reviews.studyDates.value = listOf(today.minusDays(2))
        assertEquals(0, summary().first().streakDays)
    }
}
