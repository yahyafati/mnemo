package com.yahyafati.mnemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.common.intent.AppIntents
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.core.ui.adaptive.currentWindowLayout
import com.yahyafati.mnemo.core.ui.card.audio.LocalAutoPlayAudio
import com.yahyafati.mnemo.core.ui.card.audio.LocalCardAudio
import com.yahyafati.mnemo.core.ui.card.audio.rememberCardAudio
import com.yahyafati.mnemo.ui.onboarding.OnboardingScreen
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.ui.card.LocalCardFontScale
import com.yahyafati.mnemo.ui.MnemoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash (Theme.Mnemo.Starting) stays until the settings are in, so the first frame
        // drawn is already in the right theme. It takes a few milliseconds.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value !is MainUiState.Ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // Settings load in a few milliseconds; until then the splash shows, rather than a
            // frame in the wrong theme.
            val ready = uiState as? MainUiState.Ready ?: return@setContent
            val settings = ready.settings
            val darkTheme = when (settings.darkThemeConfig) {
                DarkThemeConfig.FollowSystem -> isSystemInDarkTheme()
                DarkThemeConfig.Light -> false
                DarkThemeConfig.Dark -> true
            }
            // The app theme can differ from the system's, so the bar icons follow the app theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            MnemoTheme(darkTheme = darkTheme, dynamicColor = settings.useDynamicColor) {
                CompositionLocalProvider(
                    LocalCardFontScale provides settings.cardFontSize.scale,
                    LocalCardAudio provides rememberCardAudio(),
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
                        MnemoApp(destination = destination, onDestinationHandled = viewModel::destinationHandled)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** The reminder notification and the widget open the Study tab. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(AppIntents.EXTRA_OPEN) == AppIntents.OPEN_STUDY) viewModel.open(AppDestination.Study)
    }

    private companion object {
        val TRANSPARENT = Color.Transparent.toArgb()

        // The scrims enableEdgeToEdge() uses for three-button navigation.
        val LIGHT_SCRIM = Color(0xE6FFFFFF).toArgb()
        val DARK_SCRIM = Color(0x801B1B1B).toArgb()
    }
}
