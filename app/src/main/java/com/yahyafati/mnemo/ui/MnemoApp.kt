package com.yahyafati.mnemo.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yahyafati.mnemo.R
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBar
import com.yahyafati.mnemo.core.designsystem.component.MnemoNavigationBarItem
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.navigation.MnemoNavHost
import com.yahyafati.mnemo.navigation.TopLevelDestination

/**
 * App shell: brand top bar and bottom bar around the NavHost on the four tabs. Other screens
 * (Settings) get the whole window and draw their own bars.
 *
 * Insets: the bars pad themselves for the system bars, the Scaffold adds no insets of its own,
 * and the NavHost consumes what the bars used, so no screen pads twice.
 */
@Composable
fun MnemoApp(
    modifier: Modifier = Modifier,
    appState: MnemoAppState = rememberMnemoAppState(),
) {
    val currentTab = appState.currentTopLevelDestination
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (currentTab != null) {
                MnemoTopBar(
                    title = stringResource(R.string.app_name),
                    actions = {
                        IconButton(onClick = appState::navigateToSettings) {
                            Icon(MnemoIcons.Account, contentDescription = stringResource(R.string.open_settings))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentTab != null) {
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        )
    }
}
