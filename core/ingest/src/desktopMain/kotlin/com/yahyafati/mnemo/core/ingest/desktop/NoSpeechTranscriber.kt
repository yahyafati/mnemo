package com.yahyafati.mnemo.core.ingest.desktop

import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.model.DictationEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The desktop has no dictation in v1 (ROADMAP "Not on desktop"): a local speech model is a later
 * step. The UI hides the Dictation source when [isAvailable] is false.
 */
object NoSpeechTranscriber : SpeechTranscriber {
    override fun isAvailable(): Boolean = false

    override fun transcribe(languageTag: String?): Flow<DictationEvent> = emptyFlow()
}
