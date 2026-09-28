package com.yahyafati.mnemo.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.feature.analytics.navigation.analyticsScreen
import com.yahyafati.mnemo.feature.create.navigation.createScreen
import com.yahyafati.mnemo.feature.decks.navigation.decksScreen
import com.yahyafati.mnemo.feature.settings.navigation.settingsScreen
import com.yahyafati.mnemo.feature.study.navigation.studyScreen
import com.yahyafati.mnemo.ui.MnemoAppState

/** Composes every feature's navigation graph. Only `:app` knows all features. */
@Composable
fun MnemoNavHost(
    appState: MnemoAppState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    NavHost(
        navController = navController,
        startDestination = DecksRoute,
        modifier = modifier,
    ) {
        decksScreen()
        studyScreen()
        createScreen()
        analyticsScreen()
        settingsScreen(onBackClick = navController::popBackStack)
    }
}
