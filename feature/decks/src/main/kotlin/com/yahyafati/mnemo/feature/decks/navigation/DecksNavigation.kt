package com.yahyafati.mnemo.feature.decks.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.DecksRoute
import com.yahyafati.mnemo.feature.decks.DecksScreen

fun NavController.navigateToDecks(navOptions: NavOptions? = null) = navigate(DecksRoute, navOptions)

fun NavGraphBuilder.decksScreen() {
    composable<DecksRoute> {
        DecksScreen()
    }
}
