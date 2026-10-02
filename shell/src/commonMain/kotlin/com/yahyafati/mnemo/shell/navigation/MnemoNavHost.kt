package com.yahyafati.mnemo.shell.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.feature.analytics.navigation.analyticsScreen
import com.yahyafati.mnemo.feature.browse.navigation.browseScreen
import com.yahyafati.mnemo.feature.browse.navigation.navigateToBrowse
import com.yahyafati.mnemo.feature.create.navigation.bookImportScreen
import com.yahyafati.mnemo.feature.create.navigation.createScreen
import com.yahyafati.mnemo.feature.create.navigation.navigateToBookImport
import com.yahyafati.mnemo.feature.create.navigation.navigateToNoteEditor
import com.yahyafati.mnemo.feature.create.navigation.noteEditorScreen
import com.yahyafati.mnemo.feature.decks.navigation.decksScreen
import com.yahyafati.mnemo.feature.settings.navigation.aiProviderEditorScreen
import com.yahyafati.mnemo.feature.settings.navigation.aiProvidersScreen
import com.yahyafati.mnemo.feature.settings.navigation.licensesScreen
import com.yahyafati.mnemo.feature.settings.navigation.navigateToAiProviderEditor
import com.yahyafati.mnemo.feature.settings.navigation.navigateToAiProviders
import com.yahyafati.mnemo.feature.settings.navigation.navigateToLicenses
import com.yahyafati.mnemo.feature.settings.navigation.settingsScreen
import com.yahyafati.mnemo.feature.study.navigation.navigateToStudySession
import com.yahyafati.mnemo.feature.study.navigation.studyScreen
import com.yahyafati.mnemo.feature.study.navigation.studySessionScreen
import com.yahyafati.mnemo.shell.MnemoAppState

/** Composes every feature's navigation graph. Only the shell knows all features. */
@Composable
fun MnemoNavHost(
    appState: MnemoAppState,
    loadLicenses: suspend () -> String,
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
        createScreen(
            onSetUpAi = { navController.navigateToAiProviders() },
            onImportBook = { navController.navigateToBookImport() },
        )
        analyticsScreen(onEditNote = editNote)
        settingsScreen(
            onBackClick = navController::popBackStack,
            onOpenAiProviders = { navController.navigateToAiProviders() },
            onOpenLicenses = { navController.navigateToLicenses() },
        )
        licensesScreen(loadLibraries = loadLicenses, onBack = navController::popBackStack)
        aiProvidersScreen(
            onBack = navController::popBackStack,
            onAddProvider = { navController.navigateToAiProviderEditor() },
            onEditProvider = { id -> navController.navigateToAiProviderEditor(providerId = id) },
        )
        aiProviderEditorScreen(onClose = navController::popBackStack)
        studySessionScreen(onClose = navController::popBackStack, onEditNote = editNote)
        noteEditorScreen(onClose = navController::popBackStack)
        bookImportScreen(
            onClose = navController::popBackStack,
            onGenerateCards = { appState.navigateToTopLevelDestination(TopLevelDestination.Create) },
            onSetUpAi = { navController.navigateToAiProviders() },
        )
        browseScreen(onBack = navController::popBackStack, onEditNote = editNote)
    }
}
