package com.yahyafati.mnemo.core.ai.probe

import com.yahyafati.mnemo.core.ai.client.ChatStreamEvent
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.ModelInfo
import com.yahyafati.mnemo.core.ai.dto.ResponseFormat
import com.yahyafati.mnemo.core.ai.dto.Usage
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * "Test connection" (ARCHITECTURE §5.3): lists the models, then runs a one-token completion with
 * the chosen model and works out what it supports.
 *
 * - **Streaming**: the completion is streamed. A server that answers with plain JSON instead, or
 *   rejects `stream`, doesn't stream.
 * - **JSON output**: a second one-token request with a `json_schema` `response_format`. Accepted
 *   means supported. (A server that silently ignores the field looks supported too; Phase 4 parses
 *   tolerantly either way.)
 * - **Vision**: not worth an image upload. It comes from the models list where the provider
 *   describes it (OpenRouter), otherwise from the model's name. The user can correct it.
 */
class ConnectionProbe(private val client: OpenAiCompatibleClient) {
    suspend fun run(config: ProviderConfig, model: String?): ProbeResult {
        val models = client.listModels(config)
        val listed = (models as? MnemoResult.Success)?.data.orEmpty()
        // With no model chosen, a server offering exactly one chat model (a typical local setup,
        // maybe next to an embedding model) is tested with it.
        val modelId = model?.trim()?.takeIf { it.isNotEmpty() } ?: listed.singleOrNull { ModelHeuristics.isChatModel(it.id) }?.id
            ?: return ProbeResult(models, testedModel = null, completion = null, requests = 1, usage = Usage())

        var requests = 1
        var usage = Usage()
        var limit = TokenLimit.MaxTokens

        var streamed = streamOnce(config, modelId, limit).also { requests++ }
        if (streamed is Outcome.Failed && streamed.error.mentions("max_completion_tokens")) {
            limit = TokenLimit.MaxCompletionTokens
            streamed = streamOnce(config, modelId, limit).also { requests++ }
        }
        val streaming = when (streamed) {
            is Outcome.Streamed -> true
            is Outcome.Whole -> false
            is Outcome.Failed -> {
                if (!streamed.error.isClientError() || !streamed.error.mentions("stream")) {
                    return ProbeResult(models, modelId, MnemoResult.Failure(streamed.error), requests, usage)
                }
                // The server refuses streaming: try once without it.
                requests++
                when (val plain = client.complete(config, request(modelId, limit))) {
                    is MnemoResult.Failure -> return ProbeResult(models, modelId, plain, requests, usage)
                    is MnemoResult.Success -> usage += plain.data.usage
                }
                false
            }
        }
        if (streamed is Outcome.Whole) usage += streamed.usage

        requests++
        val json = client.complete(config, request(modelId, limit, responseFormat = PROBE_SCHEMA))
        if (json is MnemoResult.Success) usage += json.data.usage

        val capabilities = AiCapabilities(
            jsonOutput = json is MnemoResult.Success,
            vision = ModelHeuristics.supportsVision(modelId, listed.firstOrNull { it.id == modelId }),
            streaming = streaming,
        )
        return ProbeResult(models, modelId, MnemoResult.Success(capabilities), requests, usage)
    }

    private suspend fun streamOnce(config: ProviderConfig, model: String, limit: TokenLimit): Outcome {
        var chunks = 0
        var last: Outcome? = null
        client.stream(config, request(model, limit)).collect { event ->
            when (event) {
                is ChatStreamEvent.Chunk -> chunks++
                is ChatStreamEvent.Whole -> last = Outcome.Whole(event.response.usage)
                is ChatStreamEvent.Failed -> last = Outcome.Failed(event.error)
            }
        }
        return last ?: if (chunks > 0) Outcome.Streamed else Outcome.Failed(MnemoError.Parse("The stream ended without any data"))
    }

    private fun request(model: String, limit: TokenLimit, responseFormat: ResponseFormat? = null) = ChatRequest(
        model = model,
        messages = listOf(ChatMessage.user(if (responseFormat == null) "Reply with OK." else "Reply with {\"ok\": true}.")),
        maxTokens = PROBE_TOKENS.takeIf { limit == TokenLimit.MaxTokens },
        maxCompletionTokens = PROBE_TOKENS.takeIf { limit == TokenLimit.MaxCompletionTokens },
        responseFormat = responseFormat,
    )

    private enum class TokenLimit { MaxTokens, MaxCompletionTokens }

    private sealed interface Outcome {
        data object Streamed : Outcome

        data class Whole(val usage: Usage?) : Outcome

        data class Failed(val error: MnemoError) : Outcome
    }

    private fun MnemoError.isClientError() = this is MnemoError.Http && code in 400..499

    private fun MnemoError.mentions(word: String) = this is MnemoError.Http && body?.contains(word, ignoreCase = true) == true

    private companion object {
        /** "One-token completion": as cheap as a request gets. */
        const val PROBE_TOKENS = 1

        val PROBE_SCHEMA = ResponseFormat.jsonSchema(
            name = "probe",
            schema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("ok") { put("type", "boolean") } }
                putJsonArray("required") { add(JsonPrimitive("ok")) }
                put("additionalProperties", false)
            },
        )
    }
}

/**
 * What [ConnectionProbe.run] found. [completion] is null when there was no model to test.
 * [requests] and [usage] are what the test itself cost, for the usage log.
 */
data class ProbeResult(
    val models: MnemoResult<List<ModelInfo>>,
    val testedModel: String?,
    val completion: MnemoResult<AiCapabilities>?,
    val requests: Int,
    val usage: Usage,
)

