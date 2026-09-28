package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val decks = FakeDeckRepository()
    private val cards = FakeCardRepository()

    private fun viewModel(vararg args: Pair<String, String>) =
        NoteEditorViewModel(SavedStateHandle(mapOf(*args)), decks, cards)

    @Test
    fun addsABasicNoteAndKeepsDeckAndTags() = runTest {
        val deck = decks.saveDeck("Bio")
        val vm = viewModel()
        assertEquals(deck, vm.uiState.value.deckId)

        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("Q")))
        vm.onAction(NoteEditorAction.BackChanged(TextFieldValue("A")))
        vm.onAction(NoteEditorAction.TagInputChanged("cells "))
        vm.onAction(NoteEditorAction.Save)

        val note = cards.notes.value.values.single()
        assertEquals(listOf("Q", "A"), note.fields)
        assertEquals(listOf("cells"), note.tags)
        with(vm.uiState.value) {
            assertEquals("", front.text)
            assertEquals(listOf("cells"), tags)
            assertEquals(1, addEvent)
            assertEquals(deck, deckId)
        }
    }

    @Test
    fun reversedNotesNeedBothSidesAndMakeTwoCards() = runTest {
        decks.saveDeck("Words")
        val vm = viewModel()
        vm.onAction(NoteEditorAction.SelectKind(NoteKind.Reversed))
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("perro")))
        assertEquals(EditorProblem.EmptyBack, vm.uiState.value.problem)
        vm.onAction(NoteEditorAction.BackChanged(TextFieldValue("dog")))
        assertEquals(2, vm.uiState.value.cardCount)
        vm.onAction(NoteEditorAction.Save)
        assertEquals(2, cards.cards.value.size)
        assertEquals(2, vm.uiState.value.lastAddedCards)
    }

    @Test
    fun clozeShortcutWrapsSelectionWithTheNextNumber() {
        val wrapped = NoteEditorViewModel.wrapCloze(TextFieldValue("{{c1::Paris}} is in France", TextRange(20, 26)))
        assertEquals("{{c1::Paris}} is in {{c2::France}}", wrapped.text)
        assertEquals(TextRange(wrapped.text.length), wrapped.selection)

        val empty = NoteEditorViewModel.wrapCloze(TextFieldValue("x ", TextRange(2)))
        assertEquals("x {{c1::}}", empty.text)
        assertEquals(TextRange(8), empty.selection)
    }

    @Test
    fun clozeNotesNeedADeletion() = runTest {
        decks.saveDeck("Geo")
        val vm = viewModel()
        vm.onAction(NoteEditorAction.SelectKind(NoteKind.Cloze))
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("Paris is in France", TextRange(12, 18))))
        assertEquals(EditorProblem.NoCloze, vm.uiState.value.problem)
        vm.onAction(NoteEditorAction.InsertCloze)
        assertTrue(vm.uiState.value.canSave)
        assertEquals(2, vm.uiState.value.previewSides.clozeOrdinal?.plus(1))
    }

    @Test
    fun noDeckMeansNoSave() = runTest {
        val vm = viewModel()
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("Q")))
        assertEquals(EditorProblem.NoDeck, vm.uiState.value.problem)
        assertFalse(vm.uiState.value.canSave)

        vm.onAction(NoteEditorAction.CreateDeck("New", "", ""))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun editingLoadsTheNoteAndClosesAfterSaving() = runTest {
        val deck = decks.saveDeck("Deck")
        val note = cards.addNote(deck, NoteKind.Basic, listOf("old q", "old a"), listOf("t"))
        val vm = viewModel(NoteEditorViewModel.NOTE_ID_KEY to note.id)
        with(vm.uiState.value) {
            assertTrue(isEditing)
            assertEquals("old q", front.text)
            assertEquals(listOf("t"), tags)
        }
        // The note type can't change while editing.
        vm.onAction(NoteEditorAction.SelectKind(NoteKind.Cloze))
        assertEquals(NoteKind.Basic, vm.uiState.value.kind)

        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("new q")))
        vm.onAction(NoteEditorAction.Save)
        assertEquals("new q", cards.getNote(note.id)?.fields?.first())
        assertTrue(vm.uiState.value.closeRequested)
    }
}
