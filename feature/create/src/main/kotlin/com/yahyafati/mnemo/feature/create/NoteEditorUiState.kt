package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.input.TextFieldValue
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.cardOrdinals

data class NoteEditorUiState(
    val isLoading: Boolean = true,
    /** Editing an existing note (note type fixed) rather than adding new ones. */
    val isEditing: Boolean = false,
    val decks: List<DeckOption> = emptyList(),
    val deckId: String? = null,
    val kind: NoteKind = NoteKind.Basic,
    // TextFieldValue, not String: the cloze shortcut needs the selection.
    val front: TextFieldValue = TextFieldValue(),
    val back: TextFieldValue = TextFieldValue(),
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val showDeckDialog: Boolean = false,
    /** Bumped on every successful add; the screen shows a message for each. */
    val addEvent: Int = 0,
    /** Cards made by the last add. */
    val lastAddedCards: Int = 0,
    /** Set after saving an edit: the screen should close. */
    val closeRequested: Boolean = false,
) {
    val fields: List<String> get() = listOf(front.text, back.text)

    /** How many cards the note makes as written. */
    val cardCount: Int get() = kind.cardOrdinals(fields).size

    val problem: EditorProblem?
        get() = when {
            deckId == null -> EditorProblem.NoDeck
            front.text.isBlank() -> EditorProblem.EmptyFront
            kind == NoteKind.Reversed && back.text.isBlank() -> EditorProblem.EmptyBack
            kind == NoteKind.Cloze && cardCount == 0 -> EditorProblem.NoCloze
            else -> null
        }

    val canSave: Boolean get() = !isLoading && problem == null

    /** The first card the note would make, for the live preview. */
    val previewSides: CardSides
        get() = CardSides.of(kind, fields, kind.cardOrdinals(fields).firstOrNull() ?: 0)
}

data class DeckOption(val id: String, val path: String)

enum class EditorProblem { NoDeck, EmptyFront, EmptyBack, NoCloze }

sealed interface NoteEditorAction {
    data class SelectDeck(val deckId: String) : NoteEditorAction

    data class SelectKind(val kind: NoteKind) : NoteEditorAction

    data class FrontChanged(val value: TextFieldValue) : NoteEditorAction

    data class BackChanged(val value: TextFieldValue) : NoteEditorAction

    /** Wraps the front field's selection in the next cloze deletion. */
    data object InsertCloze : NoteEditorAction

    data class TagInputChanged(val value: String) : NoteEditorAction

    data object CommitTag : NoteEditorAction

    data class RemoveTag(val tag: String) : NoteEditorAction

    data object ShowDeckDialog : NoteEditorAction

    data object DismissDeckDialog : NoteEditorAction

    data class CreateDeck(val path: String, val category: String, val description: String) : NoteEditorAction

    data object Save : NoteEditorAction
}
