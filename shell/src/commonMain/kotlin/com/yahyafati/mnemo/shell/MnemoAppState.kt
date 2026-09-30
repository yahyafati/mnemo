package com.yahyafati.mnemo.shell

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
import com.yahyafati.mnemo.shell.navigation.TopLevelDestination

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

    /** Leaves a full-screen page for the one before it; on a tab there is nowhere to go, and nothing happens. */
    fun navigateBack() {
        val destination = navController.currentDestination ?: return
        if (TopLevelDestination.entries.none { destination.hierarchy.any { node -> node.hasRoute(it.route) } }) {
            navController.popBackStack()
        }
    }

    /** Whether the screen showing now is the route [T] (for commands, which aren't composable). */
    inline fun <reified T : Any> isShowing(): Boolean = navController.currentDestination?.hasRoute<T>() == true

    fun navigateToSettings() {
        navController.navigateToSettings(navOptions { launchSingleTop = true })
    }
}
