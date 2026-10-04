package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.prompt.PageTranscriptionPrompt
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow

/**
 * One transcription request: the image of a PDF page in, its text out (docs/pdf/ROADMAP.md, P5). The reply is
 * plain text, so there is no schema, and it streams when the model can. A model that refuses images ends the reply
 * with `MnemoError.ImagesNotAccepted` ([ChatTextRunner]); the request is never repeated without the image.
 */
class PageTranscriptionClient(private val runner: ChatTextRunner) {
    fun transcribe(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: PageTranscriptionPrompt): Flow<TextEvent> =
        runner.run(config, model, prompt.messages(), RequestMode.forCapabilities(capabilities, format = null))
}
