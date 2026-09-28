package com.yahyafati.mnemo

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.DarkThemeConfig
import com.yahyafati.mnemo.core.ui.card.LocalCardFontScale
import com.yahyafati.mnemo.ui.MnemoApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            // Settings load in a few milliseconds; until then the window background shows,
            // rather than a frame in the wrong theme.
            val settings = (uiState as? MainUiState.Ready)?.settings ?: return@setContent
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
                CompositionLocalProvider(LocalCardFontScale provides settings.cardFontSize.scale) {
                    MnemoApp()
                }
            }
        }
    }

    private companion object {
        val TRANSPARENT = Color.Transparent.toArgb()

        // The scrims enableEdgeToEdge() uses for three-button navigation.
        val LIGHT_SCRIM = Color(0xE6FFFFFF).toArgb()
        val DARK_SCRIM = Color(0x801B1B1B).toArgb()
    }
}
