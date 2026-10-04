package com.yahyafati.mnemo.core.ai.generate

import com.yahyafati.mnemo.core.ai.client.ChatStreamEvent
import com.yahyafati.mnemo.core.ai.client.ImageRejection
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.ResponseFormat
import com.yahyafati.mnemo.core.ai.dto.StreamOptions
import com.yahyafati.mnemo.core.ai.dto.Usage
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * How a request goes out. Starts from what the model is known to support, and is relaxed one
 * step at a time when the server rejects a feature ([relaxedFor]).
 */
data class RequestMode(
    val streaming: Boolean,
    /** `response_format`, for models with structured output; null asks through the prompt only. */
    val format: ResponseFormat?,
    /** Ask for token usage at the end of a stream (`stream_options`), which not every server knows. */
    val includeUsage: Boolean = streaming,
) {
    /**
     * The mode to retry with after [error], or null if the error isn't about a feature this mode
     * uses. A 4xx that names a feature drops it; a bare 400/422 with a schema drops the schema,
     * the usual culprit.
     */
    fun relaxedFor(error: MnemoError): RequestMode? {
        if (error !is MnemoError.Http || error.code !in 400..499 || error.code in AUTH_CODES) return null
        val body = error.body?.lowercase().orEmpty()
        fun mentions(vararg words: String) = words.any { it in body }
        return when {
            format != null && mentions("response_format", "json_schema", "schema", "structured", "json mode") -> copy(format = null)
            includeUsage && mentions("stream_options", "include_usage") -> copy(includeUsage = false)
            streaming && mentions("stream") -> copy(streaming = false, includeUsage = false)
            format != null && error.code in GENERIC_REJECTIONS -> copy(format = null)
            else -> null
        }
    }

    companion object {
        fun forCapabilities(capabilities: AiCapabilities, format: ResponseFormat?) = RequestMode(
            streaming = capabilities.streaming,
            format = format.takeIf { capabilities.jsonOutput },
        )

        private val AUTH_CODES = setOf(401, 403, 429)
        private val GENERIC_REJECTIONS = setOf(400, 422)
    }
}

/** A piece of a reply, or its end. */
sealed interface TextEvent {
    data class Delta(val text: String) : TextEvent

    /** Always the last event. [error] is set when the reply failed (possibly after some deltas). */
    data class End(val usage: Usage, val requests: Int, val error: MnemoError?) : TextEvent
}

/**
 * Sends one chat request and turns the answer, streamed or not, into text deltas. A server that
 * rejects a feature before any text arrived is asked again without it, so callers only see text
 * and one final [TextEvent.End] with the usage of every request made.
 */
class ChatTextRunner(private val client: OpenAiCompatibleClient) {
    fun run(config: ProviderConfig, model: String, messages: List<ChatMessage>, initial: RequestMode): Flow<TextEvent> = flow {
        val hasImages = messages.any { it.content?.hasImages == true }
        var mode = initial
        var usage = Usage()
        var requests = 0
        while (true) {
            requests++
            var sent = false
            var failure: MnemoError? = null
            val request = ChatRequest(
                model = model,
                messages = messages,
                responseFormat = mode.format,
                streamOptions = StreamOptions(includeUsage = true).takeIf { mode.streaming && mode.includeUsage },
            )
            if (mode.streaming) {
                client.stream(config, request).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Chunk -> {
                            usage += event.chunk.usage
                            event.chunk.text?.takeIf { it.isNotEmpty() }?.let {
                                sent = true
                                emit(TextEvent.Delta(it))
                            }
                        }
                        is ChatStreamEvent.Whole -> {
                            usage += event.response.usage
                            event.response.text?.takeIf { it.isNotEmpty() }?.let {
                                sent = true
                                emit(TextEvent.Delta(it))
                            }
                        }
                        is ChatStreamEvent.Failed -> failure = event.error
                    }
                }
            } else {
                when (val result = client.complete(config, request)) {
                    is MnemoResult.Success -> {
                        usage += result.data.usage
                        result.data.text?.takeIf { it.isNotEmpty() }?.let {
                            sent = true
                            emit(TextEvent.Delta(it))
                        }
                    }
                    is MnemoResult.Failure -> failure = result.error
                }
            }
            val failed = failure
            // Images can't be dropped like a schema can: say so instead of asking again without them.
            val rejected = failed?.takeIf { !sent && hasImages }?.let(ImageRejection::from)
            val relaxed = failed?.takeIf { rejected == null && !sent && requests < MAX_REQUESTS }?.let(mode::relaxedFor)
            if (relaxed != null) {
                mode = relaxed
                continue
            }
            emit(TextEvent.End(usage, requests, rejected ?: failed))
            return@flow
        }
    }

    private companion object {
        /** The first request plus one retry per feature that can be dropped. */
        const val MAX_REQUESTS = 4
    }
}
