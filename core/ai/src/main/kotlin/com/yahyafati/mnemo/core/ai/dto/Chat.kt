package com.yahyafati.mnemo.core.ai.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// The subset of the OpenAI chat completions API that every compatible server implements.
// Optional fields are null and left out of requests, so servers that don't know them never see them.

@Serializable
data class ChatMessage(
    val role: String = "assistant",
    val content: MessageContent? = null,
) {
    /** The message's text: the string itself, or the text parts of a multimodal message joined. */
    val text: String? get() = content?.text

    constructor(role: String, text: String) : this(role, MessageContent.Text(text))

    companion object {
        fun system(content: String) = ChatMessage("system", content)

        fun user(content: String) = ChatMessage("user", content)

        /** A user message with [text] and then [images], in the order given. No images is the plain text message. */
        fun user(text: String, images: List<ContentPart.Image>) =
            if (images.isEmpty()) user(text) else ChatMessage("user", MessageContent.Parts(listOf(ContentPart.Text(text)) + images))
    }
}

/**
 * What a message says. Text is a JSON string on the wire, exactly as before images existed, so a
 * server that doesn't know about vision never sees the array form (ADR 0014).
 */
@Serializable(with = MessageContentSerializer::class)
sealed interface MessageContent {
    val text: String

    @JvmInline
    value class Text(override val text: String) : MessageContent

    data class Parts(val parts: List<ContentPart>) : MessageContent {
        override val text: String get() = parts.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text }

        val images: List<ContentPart.Image> get() = parts.filterIsInstance<ContentPart.Image>()
    }

    /** Whether sending this to a server needs a model that reads images. */
    val hasImages: Boolean get() = this is Parts && parts.any { it is ContentPart.Image }
}

/** One part of a multimodal message, in the OpenAI shape. */
sealed interface ContentPart {
    /** `{"type": "text", "text": …}` */
    data class Text(val text: String) : ContentPart

    /**
     * `{"type": "image_url", "image_url": {"url": …}}`. [dataUrl] is a `data:` URL, so nothing is
     * fetched from anywhere; never log it, it is a whole page.
     */
    data class Image(val dataUrl: String) : ContentPart {
        companion object {
            @OptIn(ExperimentalEncodingApi::class)
            fun of(bytes: ByteArray, mimeType: String = "image/jpeg") = Image("data:$mimeType;base64," + Base64.Default.encode(bytes))
        }
    }
}

internal object MessageContentSerializer : KSerializer<MessageContent> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MessageContent) {
        val element: JsonElement = when (value) {
            is MessageContent.Text -> JsonPrimitive(value.text)
            is MessageContent.Parts -> buildJsonArray {
                for (part in value.parts) {
                    add(
                        when (part) {
                            is ContentPart.Text -> buildJsonObject {
                                put("type", "text")
                                put("text", part.text)
                            }
                            is ContentPart.Image -> buildJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", part.dataUrl) }
                            }
                        },
                    )
                }
            }
        }
        encoder.asJson().encodeJsonElement(element)
    }

    // Replies carry a string. Some servers answer with an array of parts, and we keep their text.
    override fun deserialize(decoder: Decoder): MessageContent = when (val element = decoder.asJson().decodeJsonElement()) {
        is JsonArray -> MessageContent.Parts(
            element.mapNotNull { part ->
                val obj = part as? JsonObject ?: return@mapNotNull null
                when ((obj["type"] as? JsonPrimitive)?.contentOrNull) {
                    "image_url" -> ((obj["image_url"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull?.let { ContentPart.Image(it) }
                    else -> (obj["text"] as? JsonPrimitive)?.contentOrNull?.let { ContentPart.Text(it) }
                }
            },
        )
        is JsonPrimitive -> MessageContent.Text(element.contentOrNull.orEmpty())
        else -> MessageContent.Text("")
    }

    private fun Encoder.asJson() = this as? JsonEncoder ?: error("MessageContent is only serialised as JSON")

    private fun Decoder.asJson() = this as? JsonDecoder ?: error("MessageContent is only serialised as JSON")
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
    val text: String? get() = choices.firstOrNull()?.message?.text

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
