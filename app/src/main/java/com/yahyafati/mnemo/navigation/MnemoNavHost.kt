package com.yahyafati.mnemo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.feature.analytics.navigation.analyticsScreen
import com.yahyafati.mnemo.feature.browse.navigation.browseScreen
import com.yahyafati.mnemo.feature.browse.navigation.navigateToBrowse
import com.yahyafati.mnemo.feature.create.navigation.createScreen
import com.yahyafati.mnemo.feature.create.navigation.navigateToNoteEditor
import com.yahyafati.mnemo.feature.create.navigation.noteEditorScreen
import com.yahyafati.mnemo.feature.decks.navigation.decksScreen
import com.yahyafati.mnemo.feature.settings.navigation.aiProviderEditorScreen
import com.yahyafati.mnemo.feature.settings.navigation.aiProvidersScreen
import com.yahyafati.mnemo.feature.settings.navigation.navigateToAiProviderEditor
import com.yahyafati.mnemo.feature.settings.navigation.navigateToAiProviders
import com.yahyafati.mnemo.feature.settings.navigation.settingsScreen
import com.yahyafati.mnemo.feature.study.navigation.navigateToStudySession
import com.yahyafati.mnemo.feature.study.navigation.studyScreen
import com.yahyafati.mnemo.feature.study.navigation.studySessionScreen
import com.yahyafati.mnemo.ui.MnemoAppState

/** Composes every feature's navigation graph. Only `:app` knows all features. */
@Composable
fun MnemoNavHost(
    appState: MnemoAppState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    val editNote: (String) -> Unit = { noteId -> navController.navigateToNoteEditor(noteId = noteId) }
    NavHost(
        navController = navController,
        startDestination = DecksRoute,
        modifier = modifier,
    ) {
        decksScreen(
            onStudyDeck = navController::navigateToStudySession,
            onStartDailyMix = { appState.navigateToTopLevelDestination(TopLevelDestination.Study) },
            onAddCards = { deckId -> navController.navigateToNoteEditor(deckId = deckId) },
            onBrowse = { deckId -> navController.navigateToBrowse(deckId) },
        )
        studyScreen(
            onEditNote = editNote,
            onBackToDecks = { appState.navigateToTopLevelDestination(TopLevelDestination.Decks) },
        )
        createScreen(onSetUpAi = { navController.navigateToAiProviders() })
        analyticsScreen()
        settingsScreen(onBackClick = navController::popBackStack, onOpenAiProviders = { navController.navigateToAiProviders() })
        aiProvidersScreen(
            onBack = navController::popBackStack,
            onAddProvider = { navController.navigateToAiProviderEditor() },
            onEditProvider = { id -> navController.navigateToAiProviderEditor(providerId = id) },
        )
        aiProviderEditorScreen(onClose = navController::popBackStack)
        studySessionScreen(onClose = navController::popBackStack, onEditNote = editNote)
        noteEditorScreen(onClose = navController::popBackStack)
        browseScreen(onBack = navController::popBackStack, onEditNote = editNote)
    }
}
