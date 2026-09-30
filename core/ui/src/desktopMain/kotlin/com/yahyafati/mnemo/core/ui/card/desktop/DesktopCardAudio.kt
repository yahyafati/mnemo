package com.yahyafati.mnemo.core.ui.card.desktop

import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.ui.card.audio.CardAudio
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.UnsupportedAudioFileException

/**
 * Card sound on `javax.sound` (ADR 0010): wav and aiff natively, mp3 and ogg through the `mp3spi`
 * and `vorbisspi` decoders. flac, m4a (AAC) and opus have no decoder; such a sound is reported to
 * [onProblem] instead of playing. There is no text-to-speech on the desktop at first, so [speak]
 * does nothing and the platform reports no `textToSpeech` capability.
 *
 * Sounds are read from [mediaDirectory] (one file per hash) and play one after another on a single
 * background thread; a new [play] or [stop] cuts the current one short.
 */
class DesktopCardAudio(
    private val mediaDirectory: File,
    private val onProblem: (AudioProblem) -> Unit = {},
) : CardAudio, AutoCloseable {
    /** Why a sound didn't play. */
    sealed interface AudioProblem {
        /** No decoder for this file: the sound's format isn't one of wav, mp3 or ogg. */
        data class UnsupportedFormat(val source: String) : AudioProblem

        /** The file is missing or can't be read. */
        data class Unreadable(val source: String) : AudioProblem

        /** The computer has no free audio output. */
        data object NoOutput : AudioProblem
    }

    private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "mnemo-card-audio").apply { isDaemon = true } }

    /** Bumped by every [play] and [stop]; a playing sound checks it and gives up when it is stale. */
    @Volatile
    private var generation = 0

    override fun play(sources: List<String>) {
        val mine = ++generation
        executor.execute {
            for (source in sources) {
                if (generation != mine) return@execute
                playOne(source) { generation != mine }
            }
        }
    }

    override fun speak(text: String) = Unit

    override fun stop() {
        generation++
    }

    override fun close() {
        generation++
        executor.shutdownNow()
    }

    private fun playOne(source: String, cancelled: () -> Boolean) {
        val hash = MediaRef.hashOf(source)
        val file = hash?.let { File(mediaDirectory, it) }
        if (file == null || !file.isFile) return onProblem(AudioProblem.Unreadable(source))
        try {
            decode(file).use { pcm ->
                val line = AudioSystem.getSourceDataLine(pcm.format)
                line.open(pcm.format)
                try {
                    line.start()
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (!cancelled()) {
                        val read = pcm.read(buffer, 0, buffer.size)
                        if (read < 0) break
                        line.write(buffer, 0, read)
                    }
                    if (cancelled()) line.stop() else line.drain()
                } finally {
                    line.close()
                }
            }
        } catch (_: UnsupportedAudioFileException) {
            onProblem(AudioProblem.UnsupportedFormat(source))
        } catch (_: IOException) {
            onProblem(AudioProblem.Unreadable(source))
        } catch (_: LineUnavailableException) {
            onProblem(AudioProblem.NoOutput)
        } catch (_: IllegalArgumentException) {
            // No line for this format: a machine without a sound card, or a decoder that produced something odd.
            onProblem(AudioProblem.NoOutput)
        }
    }

    companion object {
        private const val BUFFER_BYTES = 8192

        /**
         * Opens [file] (its format is recognized from its bytes, not its name) as 16-bit signed PCM,
         * the one encoding every output line takes. Throws [UnsupportedAudioFileException] when no
         * decoder knows the format.
         */
        fun decode(file: File): AudioInputStream {
            val encoded = AudioSystem.getAudioInputStream(file)
            val format = encoded.format
            if (format.encoding == AudioFormat.Encoding.PCM_SIGNED && format.sampleSizeInBits == 16) return encoded
            val pcm = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                format.sampleRate,
                16,
                format.channels,
                format.channels * 2,
                format.sampleRate,
                false,
            )
            return try {
                AudioSystem.getAudioInputStream(pcm, encoded)
            } catch (e: IllegalArgumentException) {
                encoded.close()
                throw UnsupportedAudioFileException(e.message)
            }
        }
    }
}
