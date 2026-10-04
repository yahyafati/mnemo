package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.input.TextFieldValue
import com.yahyafati.mnemo.core.model.CardSides
import com.yahyafati.mnemo.core.model.MultipleChoice
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.cardOrdinals

data class NoteEditorUiState(
    val isLoading: Boolean = true,
    /** Editing an existing note (note type fixed) rather than adding new ones. */
    val isEditing: Boolean = false,
    val decks: List<DeckOption> = emptyList(),
    val deckId: String? = null,
    val kind: NoteKind = NoteKind.Basic,
    // TextFieldValue, not String: the cloze shortcut and attachments need the selection.
    val front: TextFieldValue = TextFieldValue(),
    val back: TextFieldValue = TextFieldValue(),
    /** Multiple choice: the wrong answers, one per line. */
    val wrong: TextFieldValue = TextFieldValue(),
    /** An optional mnemonic or hint, revealed on request while studying. */
    val hint: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    /** The field an attachment goes into: the one focused last. */
    val target: EditorField = EditorField.Front,
    /** An attachment is being copied into media storage. */
    val attaching: Boolean = false,
    /** Bumped when a picked file couldn't be attached; the screen says so. */
    val attachFailedEvent: Int = 0,
    val showDeckDialog: Boolean = false,
    /** Bumped on every successful add; the screen shows a message for each. */
    val addEvent: Int = 0,
    /** Cards made by the last add. */
    val lastAddedCards: Int = 0,
    /** Set after saving an edit: the screen should close. */
    val closeRequested: Boolean = false,
) {
    val fields: List<String>
        get() = if (kind == NoteKind.MultipleChoice) listOf(front.text, back.text, wrong.text) else listOf(front.text, back.text)

    /** How many cards the note makes as written. */
    val cardCount: Int get() = kind.cardOrdinals(fields).size

    val problem: EditorProblem?
        get() = when {
            deckId == null -> EditorProblem.NoDeck
            front.text.isBlank() -> EditorProblem.EmptyFront
            kind in ANSWER_REQUIRED && back.text.isBlank() -> EditorProblem.EmptyBack
            kind == NoteKind.Cloze && cardCount == 0 -> EditorProblem.NoCloze
            kind == NoteKind.MultipleChoice && MultipleChoice.wrongAnswers(wrong.text, back.text).isEmpty() -> EditorProblem.NoWrongAnswers
            else -> null
        }

    val canSave: Boolean get() = !isLoading && problem == null && !attaching

    /** The first card the note would make, for the live preview. */
    val previewSides: CardSides
        get() = CardSides.of(kind, fields, kind.cardOrdinals(fields).firstOrNull() ?: 0)

    private companion object {
        val ANSWER_REQUIRED = setOf(NoteKind.Reversed, NoteKind.TypeIn, NoteKind.MultipleChoice)
    }
}

/** [cardCount] is the cards in this deck alone, not its subdecks (only Smart Extract reads it). */
data class DeckOption(val id: String, val path: String, val cardCount: Int = 0)

enum class EditorProblem { NoDeck, EmptyFront, EmptyBack, NoCloze, NoWrongAnswers }

/** The editor's text fields that take attachments. */
enum class EditorField { Front, Back, Hint }

/** What a picked file is attached as. */
enum class AttachmentKind { Image, Audio }

sealed interface NoteEditorAction {
    data class SelectDeck(val deckId: String) : NoteEditorAction

    data class SelectKind(val kind: NoteKind) : NoteEditorAction

    data class FrontChanged(val value: TextFieldValue) : NoteEditorAction

    data class BackChanged(val value: TextFieldValue) : NoteEditorAction

    data class WrongChanged(val value: TextFieldValue) : NoteEditorAction

    data class HintChanged(val value: String) : NoteEditorAction

    /** [field] got focus: attachments go there from now on. */
    data class FieldFocused(val field: EditorField) : NoteEditorAction

    /** Attach the file at [uri] (picked by the user) to the target field. */
    data class Attach(val uri: String, val kind: AttachmentKind) : NoteEditorAction

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
