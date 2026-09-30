package com.yahyafati.mnemo.feature.create.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.core.ui.navigation.NoteEditorRoute
import com.yahyafati.mnemo.feature.create.CreateScreen
import com.yahyafati.mnemo.feature.create.NoteEditorFullScreen

fun NavController.navigateToCreate(navOptions: NavOptions? = null) = navigate(CreateRoute, navOptions)

/** Opens the full-screen editor: [noteId] to edit a note, or [deckId] to add cards to a deck. */
fun NavController.navigateToNoteEditor(noteId: String? = null, deckId: String? = null, navOptions: NavOptions? = null) =
    navigate(NoteEditorRoute(noteId = noteId, deckId = deckId), navOptions)

/** [onSetUpAi] opens the AI provider settings from the Smart Extract setup prompt. */
fun NavGraphBuilder.createScreen(onSetUpAi: () -> Unit) {
    composable<CreateRoute> {
        CreateScreen(onSetUpAi = onSetUpAi)
    }
}

fun NavGraphBuilder.noteEditorScreen(onClose: () -> Unit) {
    composable<NoteEditorRoute> {
        NoteEditorFullScreen(onClose = onClose)
    }
}
