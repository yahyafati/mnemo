package com.yahyafati.mnemo.core.ai.probe

import com.yahyafati.mnemo.core.ai.client.ChatStreamEvent
import com.yahyafati.mnemo.core.ai.client.ImageRejection
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.ContentPart
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
 * - **Vision**: not worth an image upload here. It comes from the models list where the provider
 *   describes it (OpenRouter), otherwise from the model's name. The user can correct it, or run
 *   [checkImages], which is a button of its own because it costs tokens.
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

    /**
     * "Check images" (ADR 0014): sends a 96 × 64 picture of a number and asks for it. A right answer
     * means the model sees images; a different one, or the server refusing the image, means it
     * doesn't. Anything else (key, network, an empty reply: reasoning models sometimes spend the
     * whole budget thinking) proves nothing and is reported as inconclusive.
     */
    suspend fun checkImages(config: ProviderConfig, model: String): ImageProbeResult {
        var requests = 0
        var limit = TokenLimit.MaxTokens
        while (true) {
            requests++
            val request = ChatRequest(
                model = model,
                messages = listOf(ChatMessage.user(IMAGE_PROMPT, listOf(ContentPart.Image.of(numberImage(), "image/png")))),
                maxTokens = IMAGE_PROBE_TOKENS.takeIf { limit == TokenLimit.MaxTokens },
                maxCompletionTokens = IMAGE_PROBE_TOKENS.takeIf { limit == TokenLimit.MaxCompletionTokens },
            )
            when (val result = client.complete(config, request)) {
                is MnemoResult.Success -> {
                    val usage = result.data.usage ?: Usage()
                    val answer = result.data.text?.trim().orEmpty()
                    val outcome = when {
                        answer.isEmpty() -> ImageProbeOutcome.Failed(MnemoError.Parse("The model sent no answer"))
                        answer.filter(Char::isDigit) == IMAGE_PROBE_NUMBER -> ImageProbeOutcome.Reads
                        else -> ImageProbeOutcome.Misread(answer.take(MAX_ANSWER_CHARS))
                    }
                    return ImageProbeResult(outcome, requests, usage)
                }
                is MnemoResult.Failure -> {
                    val error = result.error
                    if (limit == TokenLimit.MaxTokens && error.mentions("max_completion_tokens")) {
                        limit = TokenLimit.MaxCompletionTokens
                        continue
                    }
                    val refused = ImageRejection.from(error)
                    return ImageProbeResult(if (refused != null) ImageProbeOutcome.Refused(refused) else ImageProbeOutcome.Failed(error), requests, Usage())
                }
            }
        }
    }

    private fun numberImage(): ByteArray =
        checkNotNull(ConnectionProbe::class.java.getResourceAsStream(IMAGE_RESOURCE)) { "Missing $IMAGE_RESOURCE" }.use { it.readBytes() }

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

        /** Room for a short answer; a model that is going to read the image says the number at once. */
        const val IMAGE_PROBE_TOKENS = 200
        const val MAX_ANSWER_CHARS = 80
        const val IMAGE_PROMPT = "Reply with only the number in the image."

        /** What `probe/number.png` shows (see `core/ai/fixtures/make_probe_image.py`). */
        const val IMAGE_PROBE_NUMBER = "52"
        const val IMAGE_RESOURCE = "/probe/number.png"

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


/** What [ConnectionProbe.checkImages] found, with what the check cost. */
data class ImageProbeResult(val outcome: ImageProbeOutcome, val requests: Int, val usage: Usage)

sealed interface ImageProbeOutcome {
    data object Reads : ImageProbeOutcome

    data class Misread(val answer: String) : ImageProbeOutcome

    data class Refused(val error: MnemoError.ImagesNotAccepted) : ImageProbeOutcome

    data class Failed(val error: MnemoError) : ImageProbeOutcome
}
