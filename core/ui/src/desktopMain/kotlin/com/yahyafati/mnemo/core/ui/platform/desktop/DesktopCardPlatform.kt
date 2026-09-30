package com.yahyafati.mnemo.core.ui.platform.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.desktop.desktopPlatformCapabilities
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.core.ui.adaptive.currentWindowLayout
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.audio.LocalCardAudio
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.desktop.JLaTeXMathPainter
import com.yahyafati.mnemo.core.ui.card.desktop.JLaTeXMathRenderer
import com.yahyafati.mnemo.core.ui.card.web.LocalMathRenderer
import com.yahyafati.mnemo.core.ui.platform.LocalAppVersion
import java.io.File

/**
 * What the desktop app gives the shared UI, the counterpart of what `MainActivity` provides on
 * Android: the platform's capabilities, the window layout, and card images, math and sound (images
 * and sounds are read from [mediaDirectory], the collection's `media` folder). Wrap the app in it,
 * outside `MnemoTheme`.
 */
@Composable
fun ProvideDesktopPlatform(
    mediaDirectory: File,
    appVersion: String = "",
    onAudioProblem: (DesktopCardAudio.AudioProblem) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val images = remember(mediaDirectory) { DesktopMediaImageLoader(mediaDirectory) }
    val math = remember { JLaTeXMathRenderer(JLaTeXMathPainter()) }
    val audio = remember(mediaDirectory) { DesktopCardAudio(mediaDirectory, onAudioProblem) }
    DisposableEffect(audio) { onDispose(audio::close) }
    CompositionLocalProvider(
        LocalPlatformCapabilities provides desktopPlatformCapabilities(),
        LocalAppVersion provides appVersion,
        LocalMediaImageLoader provides images,
        LocalMathRenderer provides math,
        LocalCardAudio provides audio,
        LocalWindowLayout provides currentWindowLayout(),
        content = content,
    )
}
