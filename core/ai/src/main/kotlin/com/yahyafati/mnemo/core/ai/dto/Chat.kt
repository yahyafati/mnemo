package com.yahyafati.mnemo.core.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// The subset of the OpenAI chat completions API that every compatible server implements.
// Optional fields are null and left out of requests, so servers that don't know them never see them.

@Serializable
data class ChatMessage(
    val role: String = "assistant",
    val content: String? = null,
) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)

        fun user(content: String) = ChatMessage("user", content)
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    /** Newer OpenAI models (reasoning ones) reject `max_tokens` and take this instead. */
    @SerialName("max_completion_tokens") val maxCompletionTokens: Int? = null,
    val temperature: Double? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null,
)

@Serializable
data class StreamOptions(@SerialName("include_usage") val includeUsage: Boolean)

/** `{"type": "json_schema", "json_schema": {…}}` or `{"type": "json_object"}`. */
@Serializable
data class ResponseFormat(
    val type: String,
    @SerialName("json_schema") val jsonSchema: JsonSchemaFormat? = null,
) {
    companion object {
        fun jsonSchema(name: String, schema: JsonObject, strict: Boolean = true) =
            ResponseFormat("json_schema", JsonSchemaFormat(name, schema, strict))

        val JsonObject = ResponseFormat("json_object")
    }
}

@Serializable
data class JsonSchemaFormat(
    val name: String,
    val schema: JsonObject,
    val strict: Boolean? = null,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    /** The first choice's text, if any. */
    val text: String? get() = choices.firstOrNull()?.message?.content

    @Serializable
    data class Choice(
        val index: Int = 0,
        val message: ChatMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )
}

/** One server-sent event of a streamed completion. */
@Serializable
data class ChatChunk(
    val id: String? = null,
    val model: String? = null,
    val choices: List<Choice> = emptyList(),
    /** Only on the last chunk, and only if the request asked for it ([StreamOptions]). */
    val usage: Usage? = null,
) {
    val text: String? get() = choices.firstOrNull()?.delta?.content

    @Serializable
    data class Choice(
        val index: Int = 0,
        val delta: Delta? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class Delta(
        val role: String? = null,
        val content: String? = null,
    )
}

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
    @SerialName("total_tokens") val totalTokens: Long? = null,
) {
    operator fun plus(other: Usage?): Usage = if (other == null) this else Usage(promptTokens + other.promptTokens, completionTokens + other.completionTokens)
}
