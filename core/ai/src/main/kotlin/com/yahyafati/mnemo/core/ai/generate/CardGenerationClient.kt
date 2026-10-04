package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.Usage
import com.yahyafati.mnemo.core.ai.parse.GeneratedCardParser
import com.yahyafati.mnemo.core.ai.parse.ParsedCard
import com.yahyafati.mnemo.core.ai.prompt.CardGenerationPrompt
import com.yahyafati.mnemo.core.ai.prompt.CardsPrompt
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** An event of [CardGenerationClient.generate]. */
sealed interface CardEvent {
    data class Card(val card: ParsedCard) : CardEvent

    /** Always the last event. Cards emitted before an [error] are still good. */
    data class Done(val usage: Usage, val requests: Int, val error: MnemoError?) : CardEvent
}

/**
 * One Smart Extract request (PROJECT_OVERVIEW §5.3, steps 3–4). Asks for structured output when
 * the model supports it, streams when it can, and emits each card as soon as the reply completes
 * it. A reply with no readable cards gets one repair request, which shows the model its own
 * answer and asks for the JSON again.
 */
class CardGenerationClient(private val runner: ChatTextRunner) {
    fun generate(config: ProviderConfig, model: String, capabilities: AiCapabilities, prompt: CardsPrompt): Flow<CardEvent> = flow {
        val mode = RequestMode.forCapabilities(capabilities, prompt.responseFormat)
        var messages = prompt.messages()
        var usage = Usage()
        var requests = 0
        var repaired = false
        while (true) {
            val parser = GeneratedCardParser()
            var end: TextEvent.End? = null
            runner.run(config, model, messages, mode).collect { event ->
                when (event) {
                    is TextEvent.Delta -> parser.feed(event.text).forEach { emit(CardEvent.Card(it)) }
                    is TextEvent.End -> end = event
                }
            }
            val finished = checkNotNull(end) { "ChatTextRunner always ends with End" }
            usage += finished.usage
            requests += finished.requests
            if (finished.error != null) {
                emit(CardEvent.Done(usage, requests, finished.error))
                return@flow
            }
            parser.finish().forEach { emit(CardEvent.Card(it)) }
            if (parser.count == 0 && !repaired) {
                repaired = true
                messages = prompt.messages() +
                    ChatMessage("assistant", parser.text.take(MAX_ECHO_CHARS).ifBlank { "(no reply)" }) +
                    ChatMessage.user(CardGenerationPrompt.REPAIR)
                continue
            }
            emit(CardEvent.Done(usage, requests, error = null))
            return@flow
        }
    }

    private companion object {
        /** How much of an unreadable reply is shown back to the model. */
        const val MAX_ECHO_CHARS = 8_000
    }
}
