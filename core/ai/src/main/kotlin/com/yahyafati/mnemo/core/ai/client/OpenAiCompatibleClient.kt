package com.yahyafati.mnemo.core.ai.client

import com.yahyafati.mnemo.core.ai.dto.ChatChunk
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.ChatResponse
import com.yahyafati.mnemo.core.ai.dto.ErrorBody
import com.yahyafati.mnemo.core.ai.dto.ModelInfo
import com.yahyafati.mnemo.core.ai.dto.ModelsResponse
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiEndpoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One client for every provider (ARCHITECTURE §1): `GET /models` and `POST /chat/completions`,
 * plain or streamed as server-sent events.
 *
 * Every request is checked against [AiEndpoint] first, so nothing goes out over plain HTTP unless
 * the provider is marked local and its host is a local address. Expected failures come back as
 * [MnemoError]s. Nothing is logged: requests carry API keys and card content.
 */
class OpenAiCompatibleClient(private val httpClient: OkHttpClient) {
    suspend fun listModels(config: ProviderConfig): MnemoResult<List<ModelInfo>> =
        execute(config, "models", body = null) { AiJson.decodeFromString<ModelsResponse>(it).data }

    /** A complete (not streamed) chat completion. */
    suspend fun complete(config: ProviderConfig, request: ChatRequest): MnemoResult<ChatResponse> =
        execute(config, "chat/completions", AiJson.encodeToString(request.copy(stream = null))) {
            AiJson.decodeFromString<ChatResponse>(it)
        }

    /**
     * A streamed chat completion. Emits [ChatStreamEvent.Chunk]s and completes after the last one.
     * A server that ignores `stream` and answers with one JSON body gives a single
     * [ChatStreamEvent.Whole]. A failure is the last event, [ChatStreamEvent.Failed].
     * Cancelling the collector cancels the request.
     */
    fun stream(config: ProviderConfig, request: ChatRequest): Flow<ChatStreamEvent> = callbackFlow {
        val built = buildRequest(config, "chat/completions", AiJson.encodeToString(request.copy(stream = true)), accept = EVENT_STREAM)
        if (built is MnemoResult.Failure) {
            send(ChatStreamEvent.Failed(built.error))
            close()
            awaitClose()
            return@callbackFlow
        }
        val client = httpClient.newBuilder()
            .callTimeout(Duration.ZERO)
            .readTimeout(config.timeout)
            .build()

        // trySendBlocking: the reader thread waits for a slow collector instead of dropping chunks.
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == DONE) {
                    channel.close()
                    return
                }
                trySendBlocking(parseChunk(data, config))
                    .onFailure { eventSource.cancel() }
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val event = when {
                    response != null && response.isSuccessful && response.body.contentType()?.subtype == "json" ->
                        decode(response.body.string(), config) { ChatStreamEvent.Whole(AiJson.decodeFromString<ChatResponse>(it)) }
                            .let { result -> (result as? MnemoResult.Success)?.data ?: ChatStreamEvent.Failed((result as MnemoResult.Failure).error) }
                    response != null && !response.isSuccessful ->
                        ChatStreamEvent.Failed(httpError(response.code, response.peekBody(MAX_ERROR_BYTES).string(), config))
                    t is IOException -> ChatStreamEvent.Failed(MnemoError.Network(t))
                    else -> ChatStreamEvent.Failed(MnemoError.Unknown(t))
                }
                trySendBlocking(event)
                channel.close()
            }
        }
        val source = EventSources.createFactory(client).newEventSource((built as MnemoResult.Success).data, listener)
        awaitClose { source.cancel() }
    }

    private fun parseChunk(data: String, config: ProviderConfig): ChatStreamEvent = try {
        val obj = AiJson.parseToJsonElement(data).jsonObject
        // Some servers report a failure mid-stream as an event instead of an HTTP status.
        if ("error" in obj) {
            ChatStreamEvent.Failed(MnemoError.Http(STREAM_ERROR_CODE, errorMessage(data, config)))
        } else {
            ChatStreamEvent.Chunk(AiJson.decodeFromJsonElement(ChatChunk.serializer(), obj))
        }
    } catch (e: SerializationException) {
        ChatStreamEvent.Failed(MnemoError.Parse("Unreadable stream event", e))
    } catch (e: IllegalArgumentException) {
        ChatStreamEvent.Failed(MnemoError.Parse("Unreadable stream event", e))
    }

    private suspend fun <T> execute(
        config: ProviderConfig,
        path: String,
        body: String?,
        parse: (String) -> T,
    ): MnemoResult<T> {
        val request = when (val built = buildRequest(config, path, body, accept = JSON)) {
            is MnemoResult.Failure -> return built
            is MnemoResult.Success -> built.data
        }
        val client = httpClient.newBuilder().callTimeout(config.timeout).build()
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            return MnemoResult.Failure(MnemoError.Network(e))
        }
        return if (response.code in 200..299) {
            decode(response.body, config, parse)
        } else {
            MnemoResult.Failure(httpError(response.code, response.body, config))
        }
    }

    private fun <T> decode(body: String, config: ProviderConfig, parse: (String) -> T): MnemoResult<T> = try {
        MnemoResult.Success(parse(body))
    } catch (e: SerializationException) {
        MnemoResult.Failure(MnemoError.Parse("Not an OpenAI-compatible response: ${preview(body, config)}", e))
    } catch (e: IllegalArgumentException) {
        MnemoResult.Failure(MnemoError.Parse("Not an OpenAI-compatible response: ${preview(body, config)}", e))
    }

    private fun buildRequest(config: ProviderConfig, path: String, body: String?, accept: String): MnemoResult<Request> {
        when (AiEndpoint.check(config.baseUrl, config.isLocal)) {
            AiEndpoint.Check.Ok -> Unit
            AiEndpoint.Check.Invalid -> return blocked("Invalid base URL")
            is AiEndpoint.Check.Insecure -> return blocked("Plain HTTP is only allowed for local providers")
        }
        val url = (config.baseUrl.trimEnd('/') + "/" + path).toHttpUrlOrNull() ?: return blocked("Invalid base URL")
        val builder = Request.Builder().url(url).header("Accept", accept)
        config.apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer ${it.trim()}") }
        // After Authorization, so a provider that wants its own auth header can replace it.
        for ((name, value) in config.headers) {
            try {
                builder.header(name, value)
            } catch (e: IllegalArgumentException) {
                return blocked("Invalid header \"$name\"")
            }
        }
        if (body != null) builder.post(body.toRequestBody(JSON_MEDIA_TYPE))
        return MnemoResult.Success(builder.build())
    }

    private fun blocked(reason: String) = MnemoResult.Failure(MnemoError.Blocked(reason))

    private fun httpError(code: Int, body: String, config: ProviderConfig) = MnemoError.Http(code, errorMessage(body, config))

    /** The server's own explanation, short and with the key taken out (some servers echo it). */
    private fun errorMessage(body: String, config: ProviderConfig): String? {
        val parsed = runCatching { AiJson.decodeFromString<ErrorBody>(body) }.getOrNull()
        val message = parsed?.let {
            (it.error as? JsonPrimitive)?.contentOrNull
                ?: (it.error as? JsonObject)?.get("message")?.let { m -> (m as? JsonPrimitive)?.contentOrNull }
                ?: it.message
                ?: (it.detail as? JsonPrimitive)?.contentOrNull
                ?: it.detail?.toString()
        } ?: body.takeUnless { it.trimStart().startsWith("<") } // not an HTML error page
        return message?.trim()?.takeIf { it.isNotEmpty() }?.let { redact(it, config).take(MAX_ERROR_CHARS) }
    }

    private fun preview(body: String, config: ProviderConfig) = redact(body.take(PREVIEW_CHARS), config)

    private fun redact(text: String, config: ProviderConfig): String {
        val key = config.apiKey?.trim()?.takeIf { it.length >= MIN_REDACT_LENGTH } ?: return text
        return text.replace(key, "•••")
    }

    /** The status and body, read on OkHttp's thread so the caller never blocks on I/O. */
    private class HttpResult(val code: Int, val body: String)

    private suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { HttpResult(it.code, it.body.string()) }
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                        return
                    }
                    continuation.resume(result)
                }
            },
        )
    }

    private companion object {
        const val JSON = "application/json"
        const val EVENT_STREAM = "text/event-stream"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val DONE = "[DONE]"
        /** The status reported for an error that arrived inside a 200 stream. */
        const val STREAM_ERROR_CODE = 502
        const val MAX_ERROR_BYTES = 16L * 1024
        const val MAX_ERROR_CHARS = 300
        const val PREVIEW_CHARS = 120
        const val MIN_REDACT_LENGTH = 8
    }
}

/** An event of [OpenAiCompatibleClient.stream]. */
sealed interface ChatStreamEvent {
    data class Chunk(val chunk: ChatChunk) : ChatStreamEvent

    /** The server answered with one JSON response instead of a stream. */
    data class Whole(val response: ChatResponse) : ChatStreamEvent

    /** The stream failed; always the last event. */
    data class Failed(val error: MnemoError) : ChatStreamEvent
}

/** Lenient with what servers send, strict about what goes out (nulls are left out). */
internal val AiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}
