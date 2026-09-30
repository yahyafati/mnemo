package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
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
    private val media = FakeMediaRepository()

    private fun viewModel(vararg args: Pair<String, String>) =
        NoteEditorViewModel(SavedStateHandle(mapOf(*args)), decks, cards, media)

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

    @Test
    fun typeInAndMultipleChoiceNotesWithHints() = runTest {
        decks.saveDeck("Quiz")
        val vm = viewModel()
        vm.onAction(NoteEditorAction.SelectKind(NoteKind.TypeIn))
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("Capital of Peru?")))
        assertEquals(EditorProblem.EmptyBack, vm.uiState.value.problem)
        vm.onAction(NoteEditorAction.BackChanged(TextFieldValue("Lima")))
        vm.onAction(NoteEditorAction.HintChanged("Starts with L"))
        vm.onAction(NoteEditorAction.Save)
        val typed = cards.notes.value.values.single()
        assertEquals("Starts with L", typed.hint)
        assertEquals("", vm.uiState.value.hint)

        vm.onAction(NoteEditorAction.SelectKind(NoteKind.MultipleChoice))
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("2 + 2?")))
        vm.onAction(NoteEditorAction.BackChanged(TextFieldValue("4")))
        assertEquals(EditorProblem.NoWrongAnswers, vm.uiState.value.problem)
        vm.onAction(NoteEditorAction.WrongChanged(TextFieldValue("- 3\n- 5\n4")))
        assertTrue(vm.uiState.value.canSave)
        assertEquals(listOf("3", "4", "5"), vm.uiState.value.previewSides.choices?.sorted())
        vm.onAction(NoteEditorAction.Save)
        val choice = cards.notes.value.values.last()
        assertEquals(listOf("2 + 2?", "4", "- 3\n- 5\n4"), choice.fields)
    }

    @Test
    fun attachmentsGoIntoTheFocusedField() = runTest {
        decks.saveDeck("Media")
        media.files["content://image"] = "cell.png" to byteArrayOf(1, 2, 3)
        media.files["content://sound"] = "hola.mp3" to byteArrayOf(4, 5)
        val vm = viewModel()
        vm.onAction(NoteEditorAction.FrontChanged(TextFieldValue("What is this?", TextRange(13))))
        vm.onAction(NoteEditorAction.Attach("content://image", AttachmentKind.Image))
        val image = media.stored.values.single { it.name == "cell.png" }
        assertEquals("What is this?\n![cell](media:${image.id})", vm.uiState.value.front.text)

        vm.onAction(NoteEditorAction.FieldFocused(EditorField.Back))
        vm.onAction(NoteEditorAction.Attach("content://sound", AttachmentKind.Audio))
        val sound = media.stored.values.single { it.name == "hola.mp3" }
        assertEquals("[sound:media:${sound.id}]", vm.uiState.value.back.text)

        vm.onAction(NoteEditorAction.Attach("content://missing", AttachmentKind.Audio))
        assertEquals(1, vm.uiState.value.attachFailedEvent)
        assertFalse(vm.uiState.value.attaching)
    }
}
