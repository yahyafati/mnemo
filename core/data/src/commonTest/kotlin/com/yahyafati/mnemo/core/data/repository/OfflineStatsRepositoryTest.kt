package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.time.StudyDay
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviews
import com.yahyafati.mnemo.core.model.DueForecast
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.ReviewLog
import com.yahyafati.mnemo.core.model.ReviewPassCounts
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** [OfflineStatsRepository] against Room: study-day boundaries in a real time zone, and card lookups. */
class OfflineStatsRepositoryTest : PlatformTest() {
    private val db = inMemoryDatabase()
    private val zone = ZoneId.of("America/New_York")

    // 10:00 local on 2026-03-10: the study day started at 04:00.
    private val clock = TestClock(Instant.parse("2026-03-10T14:00:00Z"), zone)
    private val transaction = RoomTransactionRunner(db)
    private val decks = OfflineDeckRepository(db.deckDao(), db.noteDao(), db.cardDao(), transaction, clock, Dispatchers.Unconfined)
    private val cards = OfflineCardRepository(db.noteDao(), db.cardDao(), db.deckDao(), transaction, clock)
    private val reviews = OfflineReviewRepository(db.cardDao(), db.reviewLogDao(), transaction, clock)
    private val stats = OfflineStatsRepository(db.statsDao(), cards, clock)

    @After
    fun tearDown() = db.close()

    private val today = LocalDate.of(2026, 3, 10)

    private fun local(date: LocalDate, hour: Int): Instant = date.atTime(hour, 0).atZone(zone).toInstant()

    private suspend fun newCard(): Card {
        val deck = decks.getDecks().firstOrNull()?.id ?: decks.saveDeck("Deck")
        val note = cards.addNote(deck, NoteKind.Basic, listOf("q-${UUID.randomUUID()}", "a"), emptyList())
        return checkNotNull(db.cardDao().getCardsForNote(note.id).single()).let { cards.getCard(it.id)!! }
    }

    private suspend fun answer(card: Card, at: Instant, rating: Rating = Rating.Good, stateBefore: CardState = CardState.Review): Card {
        val updated = card.copy(
            state = CardState.Review, stability = 10.0, difficulty = 5.0, lastReview = at,
            due = at.plus(Duration.ofDays(10)), lapses = card.lapses + if (rating == Rating.Again) 1 else 0,
        )
        reviews.recordAnswer(
            updated,
            ReviewLog(UUID.randomUUID().toString(), card.id, rating, stateBefore, at, 0, 10, 4_000, 10.0, 5.0),
        )
        return updated
    }

    @Test
    fun dailyReviewsFollowTheFourAmRollover() = runTest {
        val card = newCard()
        answer(card, local(today.minusDays(2), 23))
        // 02:00 on the 9th still belongs to the 8th's study day.
        answer(card, local(today.minusDays(1), 2))
        answer(card, local(today.minusDays(1), 9))
        answer(card, local(today, 5))

        assertEquals(
            listOf(DailyReviews(today.minusDays(2), 2), DailyReviews(today.minusDays(1), 1), DailyReviews(today, 1)),
            stats.observeDailyReviews(today.minusDays(7)).first(),
        )
        assertEquals(listOf(DailyReviews(today, 1)), stats.observeDailyReviews(today).first())
    }

    @Test
    fun undoneReviewsAreLeftOutOfEveryStatistic() = runTest {
        val card = newCard()
        val kept = answer(card, local(today, 5))
        val log = ReviewLog("undone", card.id, Rating.Again, CardState.Review, local(today, 6), 0, 10, 4_000, 10.0, 5.0)
        reviews.recordAnswer(kept.copy(lapses = 1), log)
        assertEquals(listOf(DailyReviews(today, 2)), stats.observeDailyReviews(today).first())

        reviews.undoAnswer(kept, log.id)
        assertEquals(listOf(DailyReviews(today, 1)), stats.observeDailyReviews(today).first())
        assertEquals(ReviewPassCounts(1, 1), stats.observePassCounts(StudyDay.start(today, zone), clock.now()).first())
        assertEquals(1, reviews.getTodayCounts().total)
    }

    @Test
    fun passCountsUseTheWindow() = runTest {
        val card = newCard()
        answer(card, local(today, 5), Rating.Again)
        answer(card, local(today, 6), Rating.Good)
        answer(card, local(today, 7), Rating.Good, stateBefore = CardState.Learning)
        assertEquals(ReviewPassCounts(2, 1), stats.observePassCounts(StudyDay.start(today, zone), clock.now()).first())
    }

    @Test
    fun forecastPutsOverdueCardsToday() = runTest {
        answer(newCard(), local(today.minusDays(20), 9)) // due 10 days ago
        answer(newCard(), local(today.minusDays(9), 9)) // due tomorrow at 09:00
        // Due tomorrow at 03:00 EDT (240 h after 02:00 EST, across the DST change): still today's study day.
        answer(newCard(), local(today.minusDays(9), 2))
        newCard() // new cards aren't forecast

        assertEquals(
            listOf(DueForecast(today, 2), DueForecast(today.plusDays(1), 1)),
            stats.observeDueForecast(days = 7).first(),
        )
    }

    @Test
    fun mostLapsedComeBackAsStudyCards() = runTest {
        var hard = newCard()
        repeat(3) { hard = answer(hard, clock.now().minus(Duration.ofDays(3L - it)), Rating.Again) }
        var easy = newCard()
        easy = answer(easy, clock.now(), Rating.Again)

        val lapsed = stats.observeMostLapsed(minLapses = 1, limit = 5).first()
        assertEquals(listOf(hard.id, easy.id), lapsed.map { it.card.id })
        assertEquals(listOf(3, 1), lapsed.map { it.card.lapses })
        assertEquals("Deck", lapsed.first().deckName)
        assertEquals(listOf(hard.id), stats.observeMostLapsed(minLapses = 2, limit = 5).first().map { it.card.id })
    }
}
