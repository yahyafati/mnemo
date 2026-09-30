package com.yahyafati.mnemo.feature.create

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.model.Cloze
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The manual card editor, for the Create tab and for the full-screen editor.
 *
 * [SavedStateHandle] keys (from `NoteEditorRoute`): `noteId` edits that note; `deckId` preselects
 * a deck for new notes. With neither, the first deck is preselected.
 */
class NoteEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val noteId: String? = savedStateHandle[NOTE_ID_KEY]
    private val initialDeckId: String? = savedStateHandle[DECK_ID_KEY]

    private val _uiState = MutableStateFlow(NoteEditorUiState(isEditing = noteId != null))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val note = noteId?.let { cardRepository.getNote(it) }
            if (note != null) {
                _uiState.update {
                    it.copy(
                        deckId = note.deckId,
                        kind = NoteType.byId(note.noteTypeId)?.kind ?: it.kind,
                        front = TextFieldValue(note.field(0)),
                        back = TextFieldValue(note.field(1)),
                        wrong = TextFieldValue(note.field(2)),
                        hint = note.hint.orEmpty(),
                        tags = note.tags,
                    )
                }
            }
        }
        viewModelScope.launch {
            deckRepository.observeDeckSummaries().collect { summaries ->
                val options = summaries.map { DeckOption(it.deck.id, it.path) }.sortedBy { it.path.lowercase() }
                _uiState.update { state ->
                    val current = state.deckId?.takeIf { id -> options.any { it.id == id } }
                    val preferred = initialDeckId?.takeIf { id -> options.any { it.id == id } }
                    state.copy(
                        isLoading = false,
                        decks = options,
                        deckId = current ?: preferred ?: options.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun onAction(action: NoteEditorAction) {
        when (action) {
            is NoteEditorAction.SelectDeck -> _uiState.update { it.copy(deckId = action.deckId) }
            is NoteEditorAction.SelectKind -> _uiState.update { if (it.isEditing) it else it.copy(kind = action.kind) }
            is NoteEditorAction.FrontChanged -> _uiState.update { it.copy(front = action.value) }
            is NoteEditorAction.BackChanged -> _uiState.update { it.copy(back = action.value) }
            is NoteEditorAction.WrongChanged -> _uiState.update { it.copy(wrong = action.value) }
            is NoteEditorAction.HintChanged -> _uiState.update { it.copy(hint = action.value) }
            is NoteEditorAction.FieldFocused -> _uiState.update { it.copy(target = action.field) }
            is NoteEditorAction.Attach -> attach(action.uri, action.kind)
            NoteEditorAction.InsertCloze -> _uiState.update { it.copy(front = wrapCloze(it.front)) }
            is NoteEditorAction.TagInputChanged -> {
                // Space or comma ends a tag, as in Anki.
                val value = action.value
                if (value.endsWith(' ') || value.endsWith(',')) {
                    _uiState.update { it.copy(tagInput = value.dropLast(1)) }
                    commitTag()
                } else {
                    _uiState.update { it.copy(tagInput = value) }
                }
            }
            NoteEditorAction.CommitTag -> commitTag()
            is NoteEditorAction.RemoveTag -> _uiState.update { it.copy(tags = it.tags - action.tag) }
            NoteEditorAction.ShowDeckDialog -> _uiState.update { it.copy(showDeckDialog = true) }
            NoteEditorAction.DismissDeckDialog -> _uiState.update { it.copy(showDeckDialog = false) }
            is NoteEditorAction.CreateDeck -> {
                _uiState.update { it.copy(showDeckDialog = false) }
                viewModelScope.launch {
                    val id = deckRepository.saveDeck(action.path, action.description, action.category)
                    _uiState.update { it.copy(deckId = id) }
                }
            }
            NoteEditorAction.Save -> save()
        }
    }

    private fun attach(uri: String, kind: AttachmentKind) {
        _uiState.update { it.copy(attaching = true) }
        viewModelScope.launch {
            val media = mediaRepository.importUri(uri)
            _uiState.update { state ->
                if (media == null) return@update state.copy(attaching = false, attachFailedEvent = state.attachFailedEvent + 1)
                val ref = MediaRef.of(media.id)
                val markup = when (kind) {
                    AttachmentKind.Image -> "![${media.name.substringBeforeLast('.').replace(Regex("[\\[\\]]"), "")}]($ref)"
                    AttachmentKind.Audio -> "[sound:$ref]"
                }
                when (state.target) {
                    EditorField.Front -> state.copy(front = insert(state.front, markup), attaching = false)
                    EditorField.Back -> state.copy(back = insert(state.back, markup), attaching = false)
                    EditorField.Hint -> state.copy(hint = listOf(state.hint.trimEnd(), markup).filter { it.isNotEmpty() }.joinToString(" "), attaching = false)
                }
            }
        }
    }

    private fun commitTag() = _uiState.update { state ->
        val tag = state.tagInput.trim().replace(Regex("\\s+"), "-")
        if (tag.isEmpty() || state.tags.any { it.equals(tag, ignoreCase = true) }) {
            state.copy(tagInput = "")
        } else {
            state.copy(tags = state.tags + tag, tagInput = "")
        }
    }

    private fun save() {
        commitTag()
        val state = _uiState.value
        val deckId = state.deckId
        if (!state.canSave || deckId == null) return
        val fields = state.fields.map { it.trim() }
        val hint = state.hint.trim().ifEmpty { null }
        viewModelScope.launch {
            if (noteId != null) {
                cardRepository.updateNote(noteId, deckId, fields, state.tags, hint)
                _uiState.update { it.copy(closeRequested = true) }
            } else {
                cardRepository.addNote(deckId, state.kind, fields, state.tags, hint)
                // Keep deck, type and tags for the next note, as Anki does.
                _uiState.update {
                    it.copy(
                        front = TextFieldValue(),
                        back = TextFieldValue(),
                        wrong = TextFieldValue(),
                        hint = "",
                        target = EditorField.Front,
                        addEvent = it.addEvent + 1,
                        lastAddedCards = state.cardCount,
                    )
                }
            }
        }
    }

    companion object {
        const val NOTE_ID_KEY = "noteId"
        const val DECK_ID_KEY = "deckId"

        /**
         * Wraps the selection in `{{cN::…}}` with the next free number. With no selection, inserts
         * an empty deletion and puts the cursor inside it.
         */
        /** Puts [markup] at the cursor (replacing a selection), on a line of its own for images and sounds. */
        internal fun insert(value: TextFieldValue, markup: String): TextFieldValue {
            val text = value.text
            val start = value.selection.min.coerceIn(0, text.length)
            val end = value.selection.max.coerceIn(0, text.length)
            val before = text.substring(0, start)
            val after = text.substring(end)
            val prefix = if (before.isEmpty() || before.endsWith('\n')) "" else "\n"
            val suffix = if (after.isEmpty() || after.startsWith('\n')) "" else "\n"
            val inserted = prefix + markup + suffix
            return TextFieldValue(before + inserted + after, TextRange(start + inserted.length))
        }

        internal fun wrapCloze(value: TextFieldValue): TextFieldValue {
            val text = value.text
            val start = value.selection.min
            val end = value.selection.max
            val prefix = "{{c${Cloze.nextOrdinal(text)}::"
            val selected = text.substring(start, end)
            val wrapped = text.substring(0, start) + prefix + selected + "}}" + text.substring(end)
            val cursor = if (selected.isEmpty()) start + prefix.length else start + prefix.length + selected.length + 2
            return TextFieldValue(wrapped, TextRange(cursor))
        }
    }
}
