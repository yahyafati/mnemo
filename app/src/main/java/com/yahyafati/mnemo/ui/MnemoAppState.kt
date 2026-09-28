package com.yahyafati.mnemo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.yahyafati.mnemo.feature.analytics.navigation.navigateToAnalytics
import com.yahyafati.mnemo.feature.create.navigation.navigateToCreate
import com.yahyafati.mnemo.feature.decks.navigation.navigateToDecks
import com.yahyafati.mnemo.feature.settings.navigation.navigateToSettings
import com.yahyafati.mnemo.feature.study.navigation.navigateToStudy
import com.yahyafati.mnemo.navigation.TopLevelDestination

@Composable
fun rememberMnemoAppState(
    navController: NavHostController = rememberNavController(),
): MnemoAppState = remember(navController) { MnemoAppState(navController) }

/** App-level UI state: which destination is showing and how to move between tabs. */
@Stable
class MnemoAppState(val navController: NavHostController) {
    private val currentDestination: NavDestination?
        @Composable get() {
            val entry by navController.currentBackStackEntryAsState()
            return entry?.destination
        }

    /** The selected tab, or null when a non-tab screen (e.g. Settings) is showing. */
    val currentTopLevelDestination: TopLevelDestination?
        @Composable get() {
            // Before the NavHost has composed there is no destination yet; that is the start tab.
            val destination = currentDestination ?: return TopLevelDestination.Decks
            return TopLevelDestination.entries.firstOrNull { tab ->
                destination.hierarchy.any { it.hasRoute(tab.route) }
            }
        }

    /**
     * Switches tabs with the standard bottom-bar behavior: one copy of each tab on the back
     * stack, and each tab keeps its own state.
     */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.Decks -> navController.navigateToDecks(options)
            TopLevelDestination.Study -> navController.navigateToStudy(options)
            TopLevelDestination.Create -> navController.navigateToCreate(options)
            TopLevelDestination.Analytics -> navController.navigateToAnalytics(options)
        }
    }

    fun navigateToSettings() {
        navController.navigateToSettings(navOptions { launchSingleTop = true })
    }
}
