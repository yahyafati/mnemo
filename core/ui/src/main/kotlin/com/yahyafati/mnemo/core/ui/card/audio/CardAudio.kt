package com.yahyafati.mnemo.core.ui.card.audio

import androidx.compose.runtime.staticCompositionLocalOf
import com.yahyafati.mnemo.core.model.markdown.Markdown

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

/** The app's [CardAudio]; [CardAudio.Silent] unless the app shell provides one (`rememberAndroidCardAudio` on Android). */
val LocalCardAudio = staticCompositionLocalOf { CardAudio.Silent }

/** Whether a card's sounds play by themselves when its side appears (Settings › Study). */
val LocalAutoPlayAudio = staticCompositionLocalOf { false }
