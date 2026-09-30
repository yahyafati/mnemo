package com.yahyafati.mnemo.core.ingest.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.DictationProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

/**
 * [SpeechTranscriber] with the device's own speech recognizer. It prefers on-device recognition:
 * the on-device recognizer where Android has one (12+), otherwise the default recognizer asked to
 * stay offline. It needs the RECORD_AUDIO permission.
 */
class AndroidSpeechTranscriber(private val context: Context) : SpeechTranscriber {
    override fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    override fun transcribe(languageTag: String?): Flow<DictationEvent> = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            trySend(DictationEvent.Failed(DictationProblem.NoPermission))
            close()
            awaitClose()
            return@callbackFlow
        }
        if (!isAvailable()) {
            trySend(DictationEvent.Failed(DictationProblem.Unavailable))
            close()
            awaitClose()
            return@callbackFlow
        }
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val recognizer = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, PAUSE_MILLIS)
            languageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        }
        val handler = Handler(Looper.getMainLooper())
        var active = true
        // Starting again from inside a callback can report "busy": post it instead.
        fun listenAgain() = handler.post { if (active) recognizer.startListening(intent) }

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(DictationEvent.Listening)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults.firstResult()?.let { trySend(DictationEvent.Partial(it)) }
                }

                override fun onResults(results: Bundle?) {
                    results.firstResult()?.takeIf { it.isNotBlank() }?.let { trySend(DictationEvent.Final(it)) }
                    listenAgain()
                }

                override fun onError(error: Int) {
                    val problem = when (error) {
                        // Silence or nothing understood: keep listening.
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> DictationProblem.NoPermission
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            DictationProblem.LanguageUnavailable
                        else -> DictationProblem.RecognizerError
                    }
                    if (problem == null) {
                        listenAgain()
                    } else {
                        trySend(DictationEvent.Failed(problem))
                        close()
                    }
                }

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        recognizer.startListening(intent)
        awaitClose {
            active = false
            handler.removeCallbacksAndMessages(null)
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main.immediate) // SpeechRecognizer must be used on the main thread.

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private companion object {
        /** How long a pause ends a phrase. The recognizer treats it as a hint. */
        const val PAUSE_MILLIS = 2_500L
    }
}
