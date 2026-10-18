package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.prompt.SourceSummaryPrompt
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow

/**
 * The one request that writes a Smart Extract source's context summary (ADR 0015, C4). The reply is plain text, so
 * there is no schema, and it streams when the model can.
 */
class SourceSummaryClient(private val runner: ChatTextRunner) {
    fun summarize(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: SourceSummaryPrompt): Flow<TextEvent> =
        runner.run(config, model, prompt.messages(), RequestMode.forCapabilities(capabilities, format = null))
}
