package com.yahyafati.mnemo.feature.study

import com.yahyafati.mnemo.core.model.CardState
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.UserSettings
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudyViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = StudyTestFixture()

    private val StudyViewModel.phase get() = uiState.value.phase

    private fun StudyViewModel.reviewing() = assertIs<StudyPhase.Reviewing>(phase)

    @Test
    fun emptyWhenNothingIsDue() = runTest {
        assertEquals(StudyPhase.Empty(laterCount = 0), fixture.viewModel().phase)
    }

    @Test
    fun ratingNeedsTheAnswerShownFirst() = runTest {
        fixture.addBasic("Q1", "A1")
        val vm = fixture.viewModel()
        assertEquals(false, vm.reviewing().revealed)

        vm.onAction(StudyAction.Rate(Rating.Good))
        assertTrue(fixture.reviews.logs.value.isEmpty())

        vm.onAction(StudyAction.Flip)
        assertTrue(vm.reviewing().revealed)
        assertEquals(Duration.ofMinutes(10), vm.reviewing().intervals[Rating.Good])
    }

    @Test
    fun answeringAdvancesAndSaves() = runTest {
        fixture.addBasic("Q1", "A1")
        fixture.addBasic("Q2", "A2")
        val vm = fixture.viewModel()
        assertEquals(1, vm.reviewing().position)
        assertEquals(2, vm.reviewing().total)

        vm.onAction(StudyAction.Flip)
        vm.onAction(StudyAction.Rate(Rating.Easy))

        assertEquals("Q2", vm.reviewing().card.note.fields[0])
        assertEquals(2, vm.reviewing().position)
        val log = fixture.reviews.logs.value.single()
        assertEquals(Rating.Easy, log.rating)
        assertEquals(CardState.Review, fixture.cards.getCard(log.cardId)?.state)
    }

    @Test
    fun againBringsTheCardBackInTheSameSession() = runTest {
        fixture.addBasic("Q1", "A1")
        val vm = fixture.viewModel()
        vm.onAction(StudyAction.Flip)
        vm.onAction(StudyAction.Rate(Rating.Again))

        // Nothing else is left, so the learning card is shown again (within the learn-ahead window).
        with(vm.reviewing()) {
            assertEquals("Q1", card.note.fields[0])
            assertEquals(CardState.Learning, card.card.state)
            assertEquals(2, position)
        }
        vm.onAction(StudyAction.Flip)
        vm.onAction(StudyAction.Rate(Rating.Easy))

        val finished = assertIs<StudyPhase.Finished>(vm.phase)
        assertEquals(mapOf(Rating.Again to 1, Rating.Easy to 1), finished.summary.ratings)
        assertEquals(50, finished.summary.accuracyPercent)
    }

    @Test
    fun undoRestoresTheCardAndDeletesTheLog() = runTest {
        fixture.addBasic("Q1", "A1")
        fixture.addBasic("Q2", "A2")
        val vm = fixture.viewModel()
        val firstCardId = vm.reviewing().card.card.id
        vm.onAction(StudyAction.Flip)
        vm.onAction(StudyAction.Rate(Rating.Good))
        assertTrue(vm.reviewing().canUndo)

        vm.onAction(StudyAction.Undo)
        with(vm.reviewing()) {
            assertEquals(firstCardId, card.card.id)
            assertEquals(false, revealed)
            assertEquals(1, position)
            assertEquals(false, canUndo)
        }
        assertTrue(fixture.reviews.logs.value.isEmpty())
        assertEquals(CardState.New, fixture.cards.getCard(firstCardId)?.state)
    }

    @Test
    fun buryAndSuspendTakeTheCardOut() = runTest {
        fixture.addBasic("Q1", "A1")
        fixture.addBasic("Q2", "A2")
        val vm = fixture.viewModel()
        val first = vm.reviewing().card.card.id
        vm.onAction(StudyAction.Bury)
        val second = vm.reviewing().card.card.id
        vm.onAction(StudyAction.Suspend)

        assertEquals(StudyPhase.Empty(laterCount = 0), vm.phase)
        assertEquals(true, fixture.cards.getCard(first)?.buriedUntil?.isAfter(fixture.clock.now()))
        assertEquals(true, fixture.cards.getCard(second)?.suspended)
    }

    @Test
    fun starAndFlagUpdateTheCardInPlace() = runTest {
        fixture.addBasic("Q1", "A1")
        val vm = fixture.viewModel()
        vm.onAction(StudyAction.ToggleStar)
        vm.onAction(StudyAction.ToggleFlag)
        with(vm.reviewing().card.card) {
            assertTrue(starred)
            assertTrue(flagged)
        }
        assertEquals(true, fixture.cards.getCard(vm.reviewing().card.card.id)?.starred)
    }

    @Test
    fun dailyNewCardLimitIsRespected() = runTest {
        val limited = StudyTestFixture(UserSettings(newCardsPerDay = 1))
        limited.addBasic("Q1", "A1")
        limited.addBasic("Q2", "A2")
        val vm = limited.viewModel()
        assertEquals(1, vm.reviewing().total)
    }

    @Test
    fun editInPlaceRefreshesContentButKeepsTheSession() = runTest {
        fixture.addBasic("Q1", "A1")
        val vm = fixture.viewModel()
        val noteId = vm.reviewing().card.note.id
        fixture.cards.updateNote(noteId, fixture.deckId, listOf("Q1 edited", "A1"), emptyList(), hint = null)

        vm.onScreenShown()
        assertEquals("Q1 edited", vm.reviewing().card.note.fields[0])
        assertEquals(1, vm.reviewing().position)
    }

    @Test
    fun clozeCardsShowTheirKind() = runTest {
        fixture.cards.addNote(fixture.deckId, NoteKind.Cloze, listOf("{{c1::Paris}} and {{c2::Rome}}", ""), emptyList())
        val vm = fixture.viewModel()
        assertEquals(NoteKind.Cloze, vm.reviewing().card.kind)
        assertEquals(2, vm.reviewing().total)
        assertNull(fixture.reviews.logs.value.firstOrNull())
    }

    @Test
    fun typeInAnswersAreCheckedAndSuggestARating() = runTest {
        fixture.cards.addNote(fixture.deckId, NoteKind.TypeIn, listOf("Capital of Peru?", "**Lima**"), emptyList(), hint = "L…")
        val vm = fixture.viewModel()
        vm.onAction(StudyAction.ShowHint)
        assertTrue(vm.reviewing().response.hintShown)
        vm.onAction(StudyAction.TypeAnswer("lima "))
        assertEquals("lima ", vm.reviewing().response.typed)
        assertNull(vm.reviewing().suggestedRating)

        vm.onAction(StudyAction.Flip)
        assertTrue(vm.reviewing().revealed)
        assertEquals(Rating.Good, vm.reviewing().suggestedRating)
        // Typing after the answer shows changes nothing.
        vm.onAction(StudyAction.TypeAnswer("x"))
        assertEquals("lima ", vm.reviewing().response.typed)
    }

    @Test
    fun choosingAnOptionRevealsTheAnswer() = runTest {
        fixture.cards.addNote(fixture.deckId, NoteKind.MultipleChoice, listOf("2 + 2?", "4", "3\n5\n22"), emptyList())
        fixture.clock.advanceBy(Duration.ofSeconds(1))
        fixture.cards.addNote(fixture.deckId, NoteKind.MultipleChoice, listOf("3 + 3?", "6", "5\n7"), emptyList())
        val vm = fixture.viewModel()
        val sides = vm.reviewing().card.sides
        assertEquals(4, sides.choices?.size)
        val wrong = sides.choices!!.indices.first { it != sides.correctChoice }

        vm.onAction(StudyAction.Choose(wrong))
        assertTrue(vm.reviewing().revealed)
        assertEquals(wrong, vm.reviewing().response.chosen)
        assertEquals(Rating.Again, vm.reviewing().suggestedRating)

        // The next card starts fresh.
        vm.onAction(StudyAction.Rate(Rating.Again))
        assertEquals("3 + 3?", vm.reviewing().card.note.fields[0])
        assertNull(vm.reviewing().response.chosen)
        vm.onAction(StudyAction.Choose(vm.reviewing().card.sides.correctChoice))
        assertEquals(Rating.Good, vm.reviewing().suggestedRating)
    }
}
