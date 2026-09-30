package com.yahyafati.mnemo.feature.analytics

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.ComputeRetentionStatsUseCase
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.DeckMaturity
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeStatsRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The Analytics ViewModel on fake repositories, and the screen it drives. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AnalyticsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val clock = TestClock(Instant.parse("2026-03-12T10:00:00Z")) // a Thursday
    private val today = LocalDate.of(2026, 3, 12)
    private val stats = FakeStatsRepository()
    private val reviews = FakeReviewRepository()
    private val decks = FakeDeckRepository()
    private val viewModel by lazy {
        AnalyticsViewModel(
            ComputeRetentionStatsUseCase(stats, reviews, decks, FakeUserSettingsRepository(), clock),
            clock,
        )
    }

    private fun seed() {
        reviews.studyDates.value = listOf(today, today.minusDays(1))
        stats.dailyReviews.value = listOf(DailyReviews(today, 48), DailyReviews(today.minusDays(1), 90))
        val windowStart = StudyDay.start(today.minusDays(29), ZoneOffset.UTC)
        stats.passCounts.value = mapOf(windowStart to ReviewPassCounts(1_000, 948))
        decks.addDeck(Deck(id = "neuro", name = "Cognitive Neuroscience", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH))
        stats.deckMaturity.value = listOf(DeckMaturity("neuro", 5, 15, 47, 458, 9_000.0))
        stats.mostLapsed.value = listOf(leech())
    }

    private fun leech(): StudyCard {
        val note = Note("n1", "neuro", NoteType.Basic.id, listOf("What does the **hippocampus** consolidate?", "Memories"), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val card = Card(
            id = "c1", noteId = "n1", deckId = "neuro", templateOrd = 0, state = CardState.Review, due = clock.now(),
            stability = 3.0, difficulty = 9.0, lapses = 9, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        )
        return StudyCard(card, note, NoteKind.Basic, "Cognitive Neuroscience")
    }

    @Test
    fun emptyWithoutReviews() = runTest {
        assertIs<AnalyticsUiState.Empty>(viewModel.uiState.first { it !is AnalyticsUiState.Loading })
    }

    @Test
    fun calendarEndsWithThisWeekAndHidesTheFuture() = runTest {
        seed()
        val state = assertIs<AnalyticsUiState.Ready>(viewModel.uiState.first { it !is AnalyticsUiState.Loading })
        val days = state.calendar.flatten()
        assertEquals(35, days.size)
        assertEquals(today.with(TemporalAdjusters.previousOrSame(viewModel.firstDayOfWeek)).minusWeeks(4), days.first().date)
        assertEquals(viewModel.firstDayOfWeek, days.first().date.dayOfWeek)
        assertEquals(48, days.single { it.date == today }.reviews)
        assertEquals(90, days.single { it.date == today.minusDays(1) }.reviews)
        days.filter { it.date.isAfter(today) }.forEach { assertEquals(null, it.reviews) }
        assertEquals(2, state.activeDays)
        // Markdown is stripped for the list.
        assertEquals("What does the hippocampus consolidate?", state.hardestCards.single().front)
        assertEquals(true, state.hardestCards.single().isLeech)
    }

    @Test
    fun screenShowsTheSectionsAndOpensAHardCard() {
        seed()
        var edited: String? = null
        composeRule.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            MnemoTheme { AnalyticsScreen(uiState = state, onEditNote = { edited = it }) }
        }
        composeRule.onNodeWithText("94.8%").assertExists()
        composeRule.onNodeWithText("Forgetting curve").assertExists()
        val list = composeRule.onNode(hasScrollToNodeAction())
        list.performScrollToNode(hasText("Cognitive Neuroscience"))
        composeRule.onNodeWithText("88.1% mature").assertExists()
        list.performScrollToNode(hasText("Upcoming reviews"))
        list.performScrollToNode(hasText("What does the hippocampus consolidate?"))
        composeRule.onNodeWithText("Leech").assertExists()
        composeRule.onNodeWithText("What does the hippocampus consolidate?").performClick()
        assertEquals("n1", edited)
    }
}
