package com.yahyafati.mnemo.feature.create.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navOptions
import com.yahyafati.mnemo.core.ui.navigation.BookImportRoute
import com.yahyafati.mnemo.core.ui.navigation.CreateRoute
import com.yahyafati.mnemo.core.ui.navigation.NoteEditorRoute
import com.yahyafati.mnemo.feature.create.BookImportFullScreen
import com.yahyafati.mnemo.feature.create.CreateScreen
import com.yahyafati.mnemo.feature.create.NoteEditorFullScreen

fun NavController.navigateToCreate(navOptions: NavOptions? = null) = navigate(CreateRoute, navOptions)

/** Opens the full-screen editor: [noteId] to edit a note, or [deckId] to add cards to a deck. */
fun NavController.navigateToNoteEditor(noteId: String? = null, deckId: String? = null, navOptions: NavOptions? = null) =
    navigate(NoteEditorRoute(noteId = noteId, deckId = deckId), navOptions)

/**
 * Opens the book import. With a [location] (a file that was dropped or opened with Mnemo) it reads that
 * book at once; without one the screen asks for a file. A book import that is already open is replaced.
 */
fun NavController.navigateToBookImport(location: String? = null) =
    navigate(BookImportRoute(location), navOptions { popUpTo<BookImportRoute> { inclusive = true } })

/**
 * [onSetUpAi] opens the AI provider settings from the Smart Extract setup prompt; [onImportBook] opens
 * the book import.
 */
fun NavGraphBuilder.createScreen(onSetUpAi: () -> Unit, onImportBook: () -> Unit) {
    composable<CreateRoute> {
        CreateScreen(onSetUpAi = onSetUpAi, onImportBook = onImportBook)
    }
}

/**
 * [onGenerateCards] leaves for the Create tab, where Smart Extract has taken the book (the import hands it over
 * first); [onSetUpAi] opens the AI provider settings from the book run's confirmation.
 */
fun NavGraphBuilder.bookImportScreen(onClose: () -> Unit, onGenerateCards: () -> Unit, onSetUpAi: () -> Unit) {
    composable<BookImportRoute> {
        BookImportFullScreen(onClose = onClose, onGenerateCards = onGenerateCards, onSetUpAi = onSetUpAi)
    }
}

fun NavGraphBuilder.noteEditorScreen(onClose: () -> Unit) {
    composable<NoteEditorRoute> {
        NoteEditorFullScreen(onClose = onClose)
    }
}
