package com.yahyafati.mnemo.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio.AudioProblem
import com.yahyafati.mnemo.core.ui.platform.desktop.ProvideDesktopPlatform
import com.yahyafati.mnemo.shell.MainViewModel
import com.yahyafati.mnemo.shell.MnemoRoot
import kotlinx.coroutines.channels.Channel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

/**
 * The desktop window's content: the shared app ([MnemoRoot]) with the desktop's platform pieces
 * (card math, images and sound from [mediaDirectory], and no widget, reminder or dictation). Needs
 * a started Koin graph ([openCollection]).
 */
@Composable
fun DesktopApp(
    mediaDirectory: File,
    appVersion: String = AppInfo.version,
    loadLicenses: suspend () -> String = { AppInfo.loadLicenses() },
) {
    KoinContext {
        WithViewModelStore {
            val viewModel = koinViewModel<MainViewModel>()
            val snackbar = remember { SnackbarHostState() }
            val problems = remember { Channel<AudioProblem>(Channel.CONFLATED) }
            LaunchedEffect(problems) {
                for (problem in problems) snackbar.showSnackbar(audioProblemMessage(problem))
            }
            Box {
                MnemoRoot(
                    viewModel = viewModel,
                    loadLicenses = loadLicenses,
                    providePlatform = { content ->
                        ProvideDesktopPlatform(
                            mediaDirectory = mediaDirectory,
                            appVersion = appVersion,
                            onAudioProblem = { problems.trySend(it) },
                            content = content,
                        )
                    },
                )
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** What to tell the user when a sound on a card doesn't play (a sound is never part of the answer). */
internal fun audioProblemMessage(problem: AudioProblem): String = when (problem) {
    is AudioProblem.UnsupportedFormat -> "This sound is in a format Mnemo can't play on the desktop yet. Mnemo plays WAV, MP3 and Ogg."
    is AudioProblem.Unreadable -> "This sound's file is missing or can't be read."
    AudioProblem.NoOutput -> "No audio output is available."
}

/** View models need an owner; the window has none of its own in every setup (and tests have none). */
@Composable
private fun WithViewModelStore(content: @Composable () -> Unit) {
    if (LocalViewModelStoreOwner.current != null) {
        content()
        return
    }
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
