package com.yahyafati.mnemo.feature.study

import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.domain.AnswerCardUseCase
import com.yahyafati.mnemo.core.domain.BuildStudyQueueUseCase
import com.yahyafati.mnemo.core.domain.UndoLastAnswerUseCase
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeReviewRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.runBlocking
import java.time.Instant

/** Fakes wired together so answers, undo and queue building all see the same cards. */
internal class StudyTestFixture(settings: UserSettings = UserSettings()) {
    val clock = TestClock(Instant.parse("2026-01-01T09:00:00Z"))
    val decks = FakeDeckRepository()
    val cards = FakeCardRepository { clock.now() }
    val reviews = FakeReviewRepository(cards)
    val settings = FakeUserSettingsRepository(settings)
    val deckId: String = runBlocking { decks.saveDeck("Biology") }.also { cards.deckNames = mapOf(it to "Biology") }

    fun addBasic(front: String, back: String) = runBlocking {
        cards.addNote(deckId, NoteKind.Basic, listOf(front, back), emptyList())
        clock.advanceBy(java.time.Duration.ofSeconds(1)) // keep creation order stable
    }

    fun viewModel(deckId: String? = null) = StudyViewModel(
        savedStateHandle = SavedStateHandle(deckId?.let { mapOf(StudyViewModel.DECK_ID_KEY to it) } ?: emptyMap()),
        buildStudyQueue = BuildStudyQueueUseCase(decks, cards, reviews, settings, clock),
        answerCard = AnswerCardUseCase(reviews),
        undoLastAnswer = UndoLastAnswerUseCase(reviews),
        cardRepository = cards,
        clock = clock,
    )
}
