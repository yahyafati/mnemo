package com.yahyafati.mnemo.feature.study.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.StudyRoute
import com.yahyafati.mnemo.core.ui.navigation.StudySessionRoute
import com.yahyafati.mnemo.feature.study.R
import com.yahyafati.mnemo.feature.study.StudyScreen
import com.yahyafati.mnemo.feature.study.StudySessionScreen

fun NavController.navigateToStudy(navOptions: NavOptions? = null) = navigate(StudyRoute, navOptions)

fun NavController.navigateToStudySession(deckId: String, navOptions: NavOptions? = null) =
    navigate(StudySessionRoute(deckId), navOptions)

/** The Study tab: the Daily Mix across all decks. */
fun NavGraphBuilder.studyScreen(
    onEditNote: (noteId: String) -> Unit,
    onBackToDecks: () -> Unit,
) {
    composable<StudyRoute> {
        StudyScreen(
            onEditNote = onEditNote,
            onDone = onBackToDecks,
            doneLabel = stringResource(R.string.feature_study_back_to_decks),
        )
    }
}

/** A full-screen session for one deck ([StudySessionRoute.deckId] reaches the ViewModel). */
fun NavGraphBuilder.studySessionScreen(
    onClose: () -> Unit,
    onEditNote: (noteId: String) -> Unit,
) {
    composable<StudySessionRoute> {
        StudySessionScreen(onClose = onClose, onEditNote = onEditNote)
    }
}
