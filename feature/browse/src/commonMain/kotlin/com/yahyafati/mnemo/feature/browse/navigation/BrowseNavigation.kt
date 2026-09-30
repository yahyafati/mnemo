package com.yahyafati.mnemo.feature.browse.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yahyafati.mnemo.core.ui.navigation.BrowseRoute
import com.yahyafati.mnemo.feature.browse.BrowseRoute as BrowseRouteScreen

/** Opens the card browser, filtered to [deckId] and its subdecks when given, with the search box focused if [focusSearch]. */
fun NavController.navigateToBrowse(deckId: String? = null, focusSearch: Boolean = false, navOptions: NavOptions? = null) =
    navigate(BrowseRoute(deckId, focusSearch), navOptions)

fun NavGraphBuilder.browseScreen(onBack: () -> Unit, onEditNote: (noteId: String) -> Unit) {
    composable<BrowseRoute> { entry ->
        BrowseRouteScreen(onBack = onBack, onEditNote = onEditNote, focusSearchOnStart = entry.toRoute<BrowseRoute>().focusSearch)
    }
}
