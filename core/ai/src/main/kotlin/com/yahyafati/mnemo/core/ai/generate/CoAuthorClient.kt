package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.prompt.CoAuthorChatPrompt
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow

/**
 * AI Co-Author's chat, streamed as Markdown. Its other actions reuse the card and rewrite clients:
 * suggestions are [CardGenerationClient] with a
 * [com.yahyafati.mnemo.core.ai.prompt.CoAuthorSuggestPrompt], weak-card fixes are
 * [StudyAssistClient.rewrite].
 */
class CoAuthorClient(private val runner: ChatTextRunner) {
    fun chat(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: CoAuthorChatPrompt): Flow<TextEvent> =
        runner.run(config, model, prompt.messages(), RequestMode.forCapabilities(capabilities, format = null))
}
