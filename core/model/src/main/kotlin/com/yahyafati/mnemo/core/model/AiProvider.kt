package com.yahyafati.mnemo.core.model

import java.time.Instant

/**
 * An OpenAI-compatible endpoint the user configured (PROJECT_OVERVIEW §5). Providers are data,
 * not code: every one speaks the same protocol. The API key is never part of this type; only
 * whether one is stored ([hasApiKey]). It stays encrypted until a request needs it.
 */
data class AiProvider(
    val id: String,
    val name: String,
    /** Up to and including the version segment, e.g. `https://api.openai.com/v1`. */
    val baseUrl: String,
    /** The [AiProviderPreset] it was created from, if any. */
    val presetId: String? = null,
    val hasApiKey: Boolean = false,
    /** Sent with every request, e.g. OpenRouter's `HTTP-Referer`. */
    val headers: Map<String, String> = emptyMap(),
    /** Used when a task has no model of its own. */
    val defaultModel: String? = null,
    val enabled: Boolean = true,
    /** Position in the list; the first enabled, usable provider is the default. */
    val sortOrder: Int = 0,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    /** User-marked local server (LAN or localhost): the only kind allowed over plain HTTP. */
    val isLocal: Boolean = false,
    /** When the user acknowledged what is sent to this provider (shown before the first request). */
    val disclosureAcceptedAt: Instant? = null,
    val lastTest: AiConnectionStatus? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Enabled and has a model to call. */
    val isUsable: Boolean get() = enabled && !defaultModel.isNullOrBlank()

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 60
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 600
    }
}

/** The outcome of the last connection test. */
data class AiConnectionStatus(val ok: Boolean, val at: Instant)

/** What a model can do. Detected by the connection test, or set by the user. */
data class AiCapabilities(
    /** Accepts `response_format` (structured JSON output). */
    val jsonOutput: Boolean = false,
    /** Accepts images in messages. */
    val vision: Boolean = false,
    /** Streams responses as server-sent events. */
    val streaming: Boolean = true,
)

/** A model offered by a provider, from `GET /models` or typed in by the user. */
data class AiModel(
    val providerId: String,
    /** The id sent as `model` in requests. */
    val id: String,
    val capabilities: AiCapabilities = AiCapabilities(),
    /** Typed in by the user rather than listed by the provider; kept when the list is refreshed. */
    val manual: Boolean = false,
    /** The user edited [capabilities]; a later test doesn't overwrite them. */
    val capabilitiesSetByUser: Boolean = false,
)

/** What an AI request is for. Each task can have its own provider and model (PROJECT_OVERVIEW §5.1). */
enum class AiTask {
    /** Smart Extract: source text → cards. */
    Extract,

    /** Deck-scoped chat that suggests and improves cards. */
    CoAuthor,

    /** "Explain this" / "Give me an example" while studying. */
    Explain,

    /** "Rewrite this card". */
    Rewrite,
}

/** The user's choice for a task. A null [modelId] means the provider's default model. */
data class AiTaskRoute(
    val task: AiTask,
    val providerId: String,
    val modelId: String? = null,
)

/**
 * The provider and model a task will use right now: its own route if that provider is usable,
 * otherwise the default provider ([usesDefault]).
 */
data class AiRoute(
    val task: AiTask,
    val provider: AiProvider,
    val modelId: String,
    val capabilities: AiCapabilities,
    val usesDefault: Boolean,
)

/** Tokens used, as reported by the provider, per provider and task. A null [task] is connection tests. */
data class AiUsageTotal(
    val providerId: String,
    val providerName: String,
    val task: AiTask?,
    val requests: Int,
    val promptTokens: Long,
    val completionTokens: Long,
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

/** Why an AI request failed, in terms the UI can explain. */
enum class AiProblem {
    /** 401/403: the key is missing, wrong or revoked. */
    Unauthorized,

    /** 404: wrong base URL, or the model doesn't exist. */
    NotFound,

    /** 429: rate limit or quota. */
    RateLimited,

    /** The server rejected the request (other 4xx). */
    BadRequest,

    /** 5xx. */
    ServerError,

    /** DNS, refused connection, TLS, or timeout. */
    Unreachable,

    /** Plain HTTP to a provider that isn't marked local, or to a non-local address. */
    InsecureUrl,

    /** The provider has a key stored but it can't be decrypted (e.g. after a restore on another device). */
    KeyUnavailable,

    /** The answer wasn't what the OpenAI-compatible API describes. */
    InvalidResponse,

    Unknown,
}

/** An [AiProblem] with the provider's own message, when it gave one. */
data class AiFailure(val problem: AiProblem, val detail: String? = null)

/** The result of "Test connection": the models endpoint, then a one-token completion. */
data class AiConnectionReport(
    /** Models from `GET /models`, or empty if the endpoint failed. */
    val models: List<AiModel>,
    /** Set when `GET /models` failed; the user can still type a model id. */
    val modelsFailure: AiFailure?,
    /** The model the completion was tried with, if there was one to try. */
    val testedModel: String?,
    /** Set when the completion failed. */
    val completionFailure: AiFailure?,
    /** What the tested model supports, when the completion succeeded. */
    val capabilities: AiCapabilities?,
) {
    val ok: Boolean get() = testedModel != null && completionFailure == null
}
