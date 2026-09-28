package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.DailyReviewCounts
import com.yahyafati.mnemo.core.model.Deck
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class BuildStudyQueueUseCaseTest {
    private val clock = TestClock(T0)
    private val decks = FakeDeckRepository()
    private val cards = FakeCardRepository { clock.now() }
    private val reviews = FakeReviewRepository(cards)
    private val settings = FakeUserSettingsRepository(UserSettings(newCardsPerDay = 3, reviewsPerDay = 2))
    private val buildQueue = BuildStudyQueueUseCase(decks, cards, reviews, settings, clock)

    private fun deck(id: String, parentId: String? = null) =
        Deck(id = id, name = id, parentId = parentId, createdAt = T0, updatedAt = T0)

    private fun addCards(deckId: String, state: CardState, count: Int) = repeat(count) { i ->
        val card = studyCard("$deckId-$state-$i", state, deckId = deckId)
        cards.notes.value += card.note.id to card.note
        cards.putCard(card.card)
    }

    @Test
    fun `respects what is left of today's limits`() = runTest {
        decks.addDeck(deck("a"))
        addCards("a", CardState.New, 5)
        addCards("a", CardState.Review, 5)
        reviews.baseCounts.value = DailyReviewCounts(newStudied = 1, reviewsDone = 1, total = 2)

        val session = buildQueue().session
        assertEquals(2, session.queue.count { it.card.state == CardState.New })
        assertEquals(1, session.queue.count { it.card.state == CardState.Review })
    }

    @Test
    fun `deck session includes subdecks only`() = runTest {
        decks.addDeck(deck("parent"))
        decks.addDeck(deck("child", parentId = "parent"))
        decks.addDeck(deck("other"))
        addCards("parent", CardState.New, 1)
        addCards("child", CardState.New, 1)
        addCards("other", CardState.New, 1)

        val queue = buildQueue("parent")
        assertEquals(setOf("parent", "child"), queue.session.queue.map { it.card.deckId }.toSet())
        assertEquals("parent", queue.deckName)
        assertEquals(3, buildQueue().session.queue.size)
    }
}
