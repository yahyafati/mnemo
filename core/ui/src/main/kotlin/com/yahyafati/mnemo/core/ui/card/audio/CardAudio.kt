package com.yahyafati.mnemo.core.ui.card.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.markdown.Markdown
import java.io.File
import java.util.Locale

/**
 * Card sound: a card's own audio (`[sound:media:<hash>]`), or text-to-speech when a side has
 * none. Everything plays on the device: sounds come from media storage, speech from the system
 * TTS engine, which the user chose and which may be offline.
 */
interface CardAudio {
    /** Plays [sources] (media references) one after another, stopping whatever was playing. */
    fun play(sources: List<String>)

    /** Reads [text] aloud, stopping whatever was playing. */
    fun speak(text: String)

    fun stop()

    companion object {
        /** Does nothing: previews, tests, and screens without sound. */
        val Silent: CardAudio = object : CardAudio {
            override fun play(sources: List<String>) = Unit

            override fun speak(text: String) = Unit

            override fun stop() = Unit
        }

        /**
         * Plays [markdown]'s sounds, or reads its text aloud when it has none. Returns false if
         * there was nothing to play or say.
         */
        fun CardAudio.playOrSpeak(markdown: String): Boolean {
            val sounds = Markdown.sounds(markdown)
            if (sounds.isNotEmpty()) {
                play(sounds)
                return true
            }
            val text = Markdown.plainText(markdown)
            if (text.isBlank()) return false
            speak(text)
            return true
        }
    }
}

/** The app's [CardAudio]; [CardAudio.Silent] unless provided (see [rememberCardAudio]). */
val LocalCardAudio = staticCompositionLocalOf { CardAudio.Silent }

/** Whether a card's sounds play by themselves when its side appears (Settings › Study). */
val LocalAutoPlayAudio = staticCompositionLocalOf { false }

/** A [CardAudio] for this composition, released when it leaves. */
@Composable
fun rememberCardAudio(): CardAudio {
    val context = LocalContext.current.applicationContext
    val audio = remember(context) { AndroidCardAudio(context) }
    DisposableEffect(audio) { onDispose { audio.release() } }
    return audio
}

/** [CardAudio] on [MediaPlayer] and [TextToSpeech]. The TTS engine starts on first use. */
internal class AndroidCardAudio(private val context: Context) : CardAudio {
    private val mediaDir = File(context.filesDir, MediaRef.DIRECTORY)
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    override fun play(sources: List<String>) {
        stop()
        val files = sources.mapNotNull { MediaRef.hashOf(it) }.map { File(mediaDir, it) }.filter { it.isFile }
        playFrom(files, 0)
    }

    private fun playFrom(files: List<File>, index: Int) {
        val file = files.getOrNull(index) ?: return
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            )
            next.setDataSource(file.path)
            next.setOnPreparedListener { it.start() }
            next.setOnCompletionListener {
                it.release()
                if (player === it) {
                    player = null
                    playFrom(files, index + 1)
                }
            }
            next.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (player === mp) player = null
                true
            }
            next.prepareAsync()
        } catch (e: Exception) {
            // A file Android can't play (unsupported format): skip it.
            next.release()
            if (player === next) player = null
            playFrom(files, index + 1)
        }
    }

    override fun speak(text: String) {
        stop()
        val engine = tts
        if (engine == null) {
            pendingSpeech = text
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) pendingSpeech?.let(::say)
                pendingSpeech = null
            }
        } else if (ttsReady) {
            say(text)
        } else {
            pendingSpeech = text
        }
    }

    private fun say(text: String) {
        val engine = tts ?: return
        engine.language = Locale.getDefault()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        pendingSpeech = null
        if (ttsReady) tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private companion object {
        const val UTTERANCE_ID = "mnemo-card"
    }
}
