package com.yahyafati.mnemo.core.ui.card

import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio
import com.yahyafati.mnemo.core.ui.card.desktop.DesktopCardAudio.AudioProblem
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.UnsupportedAudioFileException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopCardAudioTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val hash = "f".repeat(64)

    /** The test sound [name], stored as the app stores media: under its hash, with no extension. */
    private fun resource(name: String, stored: String = hash): File =
        File(folder.root, stored).also { target ->
            checkNotNull(javaClass.getResourceAsStream("/audio/$name")) { "missing test sound $name" }.use { it.copyTo(target.outputStream()) }
        }

    @Test
    fun wavMp3AndOggDecodeToPcm() {
        val wav = File(folder.root, "tone.wav")
        val samples = ByteArray(22050 * 2 / 4)
        val format = AudioFormat(22050f, 16, 1, true, false)
        AudioSystem.write(
            javax.sound.sampled.AudioInputStream(samples.inputStream(), format, samples.size / 2L),
            javax.sound.sampled.AudioFileFormat.Type.WAVE,
            wav,
        )
        for (file in listOf(wav, resource("tone.mp3", "1".repeat(64)), resource("tone.ogg", "2".repeat(64)))) {
            DesktopCardAudio.decode(file).use { pcm ->
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, pcm.format.encoding, file.name)
                assertEquals(16, pcm.format.sampleSizeInBits, file.name)
                assertTrue(pcm.readAllBytes().isNotEmpty(), file.name)
            }
        }
    }

    @Test
    fun aacHasNoDecoder() {
        val file = resource("tone.m4a")

        assertFailsWith<UnsupportedAudioFileException> { DesktopCardAudio.decode(file) }
    }

    @Test
    fun aSoundInAnUnsupportedFormatIsReportedNotPlayed() {
        resource("tone.m4a")
        val problems = CopyOnWriteArrayList<AudioProblem>()
        val audio = DesktopCardAudio(folder.root) { problems += it }

        audio.play(listOf("media:$hash"))
        audio.awaitIdle()

        assertEquals(listOf<AudioProblem>(AudioProblem.UnsupportedFormat("media:$hash")), problems)
    }

    @Test
    fun aMissingSoundIsReported() {
        val problems = CopyOnWriteArrayList<AudioProblem>()
        val audio = DesktopCardAudio(folder.root) { problems += it }

        audio.play(listOf("media:$hash"))
        audio.awaitIdle()

        assertEquals(listOf<AudioProblem>(AudioProblem.Unreadable("media:$hash")), problems)
    }

    /** `close` stops accepting work; waiting for the executor makes the asynchronous report visible. */
    private fun DesktopCardAudio.awaitIdle() {
        val executor = DesktopCardAudio::class.java.getDeclaredField("executor").apply { isAccessible = true }
            .get(this) as java.util.concurrent.ExecutorService
        executor.shutdown()
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
    }
}
