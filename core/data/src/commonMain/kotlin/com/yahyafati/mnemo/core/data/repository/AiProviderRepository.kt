package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.model.AiUsageTotal
import com.yahyafati.mnemo.core.model.ImageCheck
import com.yahyafati.mnemo.core.model.KeyProtection
import kotlinx.coroutines.flow.Flow

/**
 * AI providers (ARCHITECTURE §5.3): the list the user manages, their models, which one each task
 * uses, and the tokens spent. API keys go into the secret store encrypted and come out only to
 * build a single request; no method here returns one.
 */
interface AiProviderRepository {
    /** Where the key that encrypts the API keys is kept, for Settings to say. */
    val keyProtection: KeyProtection get() = KeyProtection.PlatformKeystore

    /** Live providers in list order. The first usable one is the default. */
    fun observeProviders(): Flow<List<AiProvider>>

    fun observeModels(providerId: String): Flow<List<AiModel>>

    /** The models of every provider, by provider id. */
    fun observeAllModels(): Flow<Map<String, List<AiModel>>>

    fun observeRoutes(): Flow<List<AiTaskRoute>>

    /** What each task uses right now, with fallback to the default provider. */
    fun observeEffectiveRoutes(): Flow<Map<AiTask, AiRoute?>>

    /** Whether AI features can run at all: some provider is enabled and has a model. */
    fun observeIsConfigured(): Flow<Boolean>

    fun observeUsage(): Flow<List<AiUsageTotal>>

    suspend fun getProvider(id: String): AiProvider?

    /**
     * The provider and model for [task]: its own route if that provider is enabled, otherwise the
     * default provider. Null when no provider is usable, so callers show the setup prompt.
     */
    suspend fun routeFor(task: AiTask): AiRoute?

    /**
     * Creates or updates the provider [draft.id]. With a [report] from [testConnection], the
     * models it listed and the capabilities it found are saved too.
     * Fails with `MnemoError.Storage` if the key couldn't be stored.
     */
    suspend fun saveProvider(draft: AiProviderDraft, report: AiConnectionReport? = null): MnemoResult<Unit>

    /** Soft-deletes the provider and its models, drops its routes, and destroys its key. */
    suspend fun deleteProvider(id: String)

    suspend fun setEnabled(id: String, enabled: Boolean)

    /** Moves the provider [offset] places in the list (negative is up). */
    suspend fun moveProvider(id: String, offset: Int)

    /** Routes [task] to [providerId] and [modelId] (null: its default model); a null [providerId] clears it. */
    suspend fun setRoute(task: AiTask, providerId: String?, modelId: String? = null)

    /** The user has seen what is sent to this provider. */
    suspend fun acceptDisclosure(providerId: String)

    /**
     * Tests [draft] as entered, saved or not: lists the models, then a one-token completion with
     * [AiProviderDraft.defaultModel]. For a saved provider the result is recorded; the tokens it
     * cost are logged either way.
     */
    suspend fun testConnection(draft: AiProviderDraft): AiConnectionReport

    /**
     * "Check images" (ADR 0014): sends a small picture of a number to [AiProviderDraft.defaultModel]
     * and asks for it. [ImageCheck.vision] says what to save as the model's vision capability, or is
     * null when nothing was proved. The tokens are logged like a connection test's. Nothing the user
     * wrote leaves the device: the picture is part of the app.
     */
    suspend fun checkImages(draft: AiProviderDraft): ImageCheck

    /** Overrides what [modelId] supports; later tests leave it alone. */
    suspend fun setCapabilities(providerId: String, modelId: String, capabilities: AiCapabilities)

    /** Logs tokens a request used, as the provider reported them. A null [task] is a connection test. */
    suspend fun recordUsage(providerId: String, task: AiTask?, modelId: String, promptTokens: Long, completionTokens: Long, requests: Int = 1)

    /** Destroys stored keys of providers that no longer exist (e.g. replaced by a restore). */
    suspend fun pruneOrphanedKeys()
}

/**
 * A provider as the editor has it. [id] is chosen when editing starts, so a test before the first
 * save already has an id to log usage against.
 */
data class AiProviderDraft(
    val id: String,
    val name: String,
    val baseUrl: String,
    val presetId: String? = null,
    val apiKey: ApiKeyChange = ApiKeyChange.Keep,
    val headers: Map<String, String> = emptyMap(),
    val defaultModel: String? = null,
    val timeoutSeconds: Int = AiProvider.DEFAULT_TIMEOUT_SECONDS,
    val isLocal: Boolean = false,
    val enabled: Boolean = true,
    /** The user acknowledged the first-request notice while editing. */
    val disclosureAccepted: Boolean = false,
)

/** What saving does to the stored key. */
sealed interface ApiKeyChange {
    /** Leave it as it is (or without one, for a new provider). */
    data object Keep : ApiKeyChange

    data class Set(val value: String) : ApiKeyChange

    data object Remove : ApiKeyChange
}
