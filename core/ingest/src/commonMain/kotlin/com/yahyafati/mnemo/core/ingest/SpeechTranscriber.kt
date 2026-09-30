package com.yahyafati.mnemo.core.ingest

import com.yahyafati.mnemo.core.model.DictationEvent
import kotlinx.coroutines.flow.Flow

/**
 * Dictation (PROJECT_OVERVIEW §5.3, step 1): speech recognized on the device, never sent to an AI
 * provider; only the text the user keeps goes on. A platform without a recognizer reports
 * [isAvailable] false, and the UI hides dictation.
 */
interface SpeechTranscriber {
    fun isAvailable(): Boolean

    /**
     * Keeps listening phrase after phrase until the collector cancels. May need a microphone
     * permission, which the UI asks for first.
     */
    fun transcribe(languageTag: String? = null): Flow<DictationEvent>
}
