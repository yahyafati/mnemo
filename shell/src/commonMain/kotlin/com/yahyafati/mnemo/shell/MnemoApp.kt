package com.yahyafati.mnemo.shell

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
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBar
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBarItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationRail
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationRailItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.feature.create.navigation.navigateToNoteEditor
import com.yahyafati.mnemo.shell.navigation.MnemoNavHost
import com.yahyafati.mnemo.shell.navigation.TopLevelDestination
import com.yahyafati.mnemo.shell.resources.Res
import com.yahyafati.mnemo.shell.resources.app_name
import com.yahyafati.mnemo.shell.resources.open_settings
import org.jetbrains.compose.resources.stringResource

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
    loadLicenses: suspend () -> String,
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
                        Icon(MnemoIcons.Account, contentDescription = stringResource(Res.string.open_settings))
                    }
                },
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    MnemoNavigationRailItem(
                        selected = destination == currentTab,
                        onClick = { appState.navigateToTopLevelDestination(destination) },
                        icon = destination.icon,
                        selectedIcon = destination.selectedIcon,
                        label = stringResource(destination.label),
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
                        title = stringResource(Res.string.app_name),
                        // Beside the rail, the rail already pads the start edge.
                        windowInsets = WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal,
                        ),
                        actions = {
                            // On wide windows Settings sits at the top of the rail instead.
                            if (!rail) {
                                IconButton(onClick = appState::navigateToSettings) {
                                    Icon(MnemoIcons.Account, contentDescription = stringResource(Res.string.open_settings))
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
                                label = stringResource(destination.label),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            MnemoNavHost(
                appState = appState,
                loadLicenses = loadLicenses,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(if (rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal)),
            )
        }
    }
}
