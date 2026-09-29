package com.yahyafati.mnemo.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yahyafati.mnemo.AppDestination
import com.yahyafati.mnemo.R
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBar
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBarItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationRail
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationRailItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.feature.create.navigation.navigateToNoteEditor
import com.yahyafati.mnemo.navigation.MnemoNavHost
import com.yahyafati.mnemo.navigation.TopLevelDestination

/**
 * App shell: brand top bar and the tab bar around the NavHost on the four tabs. Other screens
 * (Settings) get the whole window and draw their own bars. On wide windows (tablets, unfolded
 * foldables, landscape) the tabs move to a navigation rail along the start edge.
 *
 * Insets: the bars pad themselves for the system bars, the Scaffold adds no insets of its own,
 * and the NavHost consumes what the bars used, so no screen pads twice.
 */
@Composable
fun MnemoApp(
    modifier: Modifier = Modifier,
    appState: MnemoAppState = rememberMnemoAppState(),
    /** Where to go once the shell is up (reminder, widget, onboarding); null when nothing is pending. */
    destination: AppDestination? = null,
    onDestinationHandled: () -> Unit = {},
) {
    val currentTab = appState.currentTopLevelDestination
    val rail = LocalWindowLayout.current.wide && currentTab != null
    LaunchedEffect(destination) {
        when (destination) {
            null -> return@LaunchedEffect
            AppDestination.Study -> appState.navigateToTopLevelDestination(TopLevelDestination.Study)
            is AppDestination.AddCards -> appState.navController.navigateToNoteEditor(deckId = destination.deckId)
        }
        onDestinationHandled()
    }
    Row(modifier) {
        if (rail) {
            MnemoNavigationRail(
                header = {
                    IconButton(onClick = appState::navigateToSettings) {
                        Icon(MnemoIcons.Account, contentDescription = stringResource(R.string.open_settings))
                    }
                },
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    MnemoNavigationRailItem(
                        selected = destination == currentTab,
                        onClick = { appState.navigateToTopLevelDestination(destination) },
                        icon = destination.icon,
                        selectedIcon = destination.selectedIcon,
                        label = stringResource(destination.labelRes),
                    )
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                if (currentTab != null) {
                    MnemoTopBar(
                        title = stringResource(R.string.app_name),
                        // Beside the rail, the rail already pads the start edge.
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal,
                        ),
                        actions = {
                            // On wide windows Settings sits at the top of the rail instead.
                            if (!rail) {
                                IconButton(onClick = appState::navigateToSettings) {
                                    Icon(MnemoIcons.Account, contentDescription = stringResource(R.string.open_settings))
                                }
                            }
                        },
                    )
                }
            },
            bottomBar = {
                if (currentTab != null && !rail) {
                    MnemoNavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            MnemoNavigationBarItem(
                                selected = destination == currentTab,
                                onClick = { appState.navigateToTopLevelDestination(destination) },
                                icon = destination.icon,
                                selectedIcon = destination.selectedIcon,
                                label = stringResource(destination.labelRes),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            MnemoNavHost(
                appState = appState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal)),
            )
        }
    }
}
