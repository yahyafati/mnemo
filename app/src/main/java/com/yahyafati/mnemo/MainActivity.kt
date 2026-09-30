package com.yahyafati.mnemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yahyafati.mnemo.core.common.intent.AppIntents
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.android.androidPlatformCapabilities
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.android.AndroidMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.audio.LocalCardAudio
import com.yahyafati.mnemo.core.ui.card.audio.android.rememberAndroidCardAudio
import com.yahyafati.mnemo.core.ui.card.web.LocalMathRenderer
import com.yahyafati.mnemo.core.ui.card.web.android.KatexMathRenderer
import com.yahyafati.mnemo.core.ui.platform.LocalAppVersion
import com.yahyafati.mnemo.shell.AppDestination
import com.yahyafati.mnemo.shell.MainUiState
import com.yahyafati.mnemo.shell.MainViewModel
import com.yahyafati.mnemo.shell.MnemoRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()
    private val directories: AppDirectories by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash (Theme.Mnemo.Starting) stays until the settings are in, so the first frame
        // drawn is already in the right theme. It takes a few milliseconds.
        installSplashScreen().setKeepOnScreenCondition { viewModel.uiState.value !is MainUiState.Ready }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MnemoRoot(
                viewModel = viewModel,
                loadLicenses = ::loadLicenses,
                // What this platform gives the shared UI: what it can do, and the renderers and
                // players that only the platform has (KaTeX in a WebView, BitmapFactory, MediaPlayer, TTS).
                providePlatform = { content ->
                    CompositionLocalProvider(
                        LocalPlatformCapabilities provides remember { androidPlatformCapabilities() },
                        LocalAppVersion provides remember { appVersion() },
                        LocalMathRenderer provides KatexMathRenderer,
                        LocalMediaImageLoader provides remember { AndroidMediaImageLoader(directories.media) },
                        LocalCardAudio provides rememberAndroidCardAudio(),
                        content = content,
                    )
                },
                // The app theme can differ from the system's, so the bar icons follow the app theme.
                systemBars = { darkTheme ->
                    DisposableEffect(darkTheme) {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { darkTheme },
                            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                        )
                        onDispose {}
                    }
                },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** The generated open-source licenses (Settings > About), from the raw resource AboutLibraries writes. */
    private suspend fun loadLicenses(): String = withContext(Dispatchers.IO) {
        resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
    }

    private fun appVersion(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

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
