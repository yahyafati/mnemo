package com.yahyafati.mnemo.feature.browse.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.yahyafati.mnemo.core.ui.navigation.BrowseRoute
import com.yahyafati.mnemo.feature.browse.BrowseRoute as BrowseRouteScreen

/** Opens the card browser, filtered to [deckId] and its subdecks when given. */
fun NavController.navigateToBrowse(deckId: String? = null, navOptions: NavOptions? = null) =
    navigate(BrowseRoute(deckId), navOptions)

fun NavGraphBuilder.browseScreen(onBack: () -> Unit, onEditNote: (noteId: String) -> Unit) {
    composable<BrowseRoute> {
        BrowseRouteScreen(onBack = onBack, onEditNote = onEditNote)
    }
}
