package com.yahyafati.mnemo.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.core.ui.adaptive.currentWindowLayout
import com.yahyafati.mnemo.core.ui.card.LocalCardFontScale
import com.yahyafati.mnemo.core.ui.card.audio.LocalAutoPlayAudio
import com.yahyafati.mnemo.shell.onboarding.OnboardingScreen

/** Whether the app is drawn dark: what the setting says, or the system's theme when it follows it. */
@Composable
fun DarkThemeConfig.isDark(): Boolean = when (this) {
    DarkThemeConfig.FollowSystem -> isSystemInDarkTheme()
    DarkThemeConfig.Light -> false
    DarkThemeConfig.Dark -> true
}

/**
 * The whole UI, from the app settings down: the theme and the card settings, then the first-run
 * onboarding or the app shell. The launchers (`MainActivity`, the desktop window) are thin around it.
 *
 * - [providePlatform] wraps everything in what only the platform has (capabilities, card math,
 *   images and sound). The theme reads some of it, so it sits outside the theme.
 * - [loadLicenses] returns the generated open-source licenses JSON (an Android raw resource, a
 *   classpath resource on the desktop).
 * - [systemBars] is called with the resolved dark-theme flag, for launchers that draw under system
 *   bars whose icon colors must follow the app theme (Android's edge-to-edge).
 *
 * Nothing is drawn until the settings are in, a few milliseconds after start.
 */
@Composable
fun MnemoRoot(
    viewModel: MainViewModel,
    loadLicenses: suspend () -> String,
    providePlatform: @Composable (content: @Composable () -> Unit) -> Unit,
    systemBars: @Composable (darkTheme: Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = uiState as? MainUiState.Ready ?: return
    val settings = ready.settings
    val darkTheme = settings.darkThemeConfig.isDark()
    systemBars(darkTheme)
    providePlatform {
        MnemoTheme(darkTheme = darkTheme, dynamicColor = settings.useDynamicColor) {
            CompositionLocalProvider(
                LocalCardFontScale provides settings.cardFontSize.scale,
                LocalAutoPlayAudio provides settings.autoPlayAudio,
                LocalWindowLayout provides currentWindowLayout(),
            ) {
                if (ready.showOnboarding) {
                    OnboardingScreen(
                        reminderTime = settings.reminder.time,
                        onCreateDeck = viewModel::finishOnboarding,
                        onImport = viewModel::importFromOnboarding,
                        onSkip = { reminder -> viewModel.finishOnboarding(deckName = null, reminder = reminder) },
                    )
                } else {
                    val destination by viewModel.destination.collectAsStateWithLifecycle()
                    MnemoApp(
                        loadLicenses = loadLicenses,
                        destination = destination,
                        onDestinationHandled = viewModel::destinationHandled,
                    )
                }
            }
        }
    }
}
