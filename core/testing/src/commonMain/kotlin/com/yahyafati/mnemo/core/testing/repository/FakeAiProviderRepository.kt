package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.repository.AiProviderDraft
import com.yahyafati.mnemo.core.data.repository.AiProviderRepository
import com.yahyafati.mnemo.core.data.repository.ApiKeyChange
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.model.AiUsageTotal
import com.yahyafati.mnemo.core.model.KeyProtection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * In-memory [AiProviderRepository]. Keys are kept in plain text here ([keys]) so tests can check
 * what was saved. [testConnection] answers with [nextReport] and remembers the drafts it got.
 */
class FakeAiProviderRepository : AiProviderRepository {
    override var keyProtection: KeyProtection = KeyProtection.PlatformKeystore

    private val providers = MutableStateFlow<List<AiProvider>>(emptyList())
    private val models = MutableStateFlow<Map<String, List<AiModel>>>(emptyMap())
    private val routes = MutableStateFlow<List<AiTaskRoute>>(emptyList())
    val usage = MutableStateFlow<List<AiUsageTotal>>(emptyList())
    val keys = mutableMapOf<String, String>()

    var nextReport: AiConnectionReport = AiConnectionReport(emptyList(), null, null, null, null)
    val tested = mutableListOf<AiProviderDraft>()
    val savedReports = mutableListOf<AiConnectionReport?>()
    var failKeyStorage = false

    /** Adds [provider] directly, as if it had been saved earlier. */
    fun addProvider(provider: AiProvider, providerModels: List<AiModel> = emptyList()) {
        providers.update { list -> (list.filterNot { it.id == provider.id } + provider).sortedBy { it.sortOrder } }
        models.update { it + (provider.id to providerModels) }
    }

    override fun observeProviders(): Flow<List<AiProvider>> = providers

    override fun observeModels(providerId: String): Flow<List<AiModel>> = models.map { it[providerId].orEmpty() }

    override fun observeAllModels(): Flow<Map<String, List<AiModel>>> = models

    override fun observeRoutes(): Flow<List<AiTaskRoute>> = routes

    override fun observeEffectiveRoutes(): Flow<Map<AiTask, AiRoute?>> =
        combine(providers, routes) { p, r -> AiTask.entries.associateWith { resolve(it, p, r) } }

    override fun observeIsConfigured(): Flow<Boolean> = providers.map { list -> list.any { it.isUsable } }

    override fun observeUsage(): Flow<List<AiUsageTotal>> = usage

    override suspend fun getProvider(id: String): AiProvider? = providers.value.firstOrNull { it.id == id }

    override suspend fun routeFor(task: AiTask): AiRoute? = resolve(task, providers.value, routes.value)

    override suspend fun saveProvider(draft: AiProviderDraft, report: AiConnectionReport?): MnemoResult<Unit> {
        if (failKeyStorage && draft.apiKey is ApiKeyChange.Set) return MnemoResult.Failure(MnemoError.Storage())
        when (val change = draft.apiKey) {
            is ApiKeyChange.Set -> keys[draft.id] = change.value.trim()
            ApiKeyChange.Remove -> keys.remove(draft.id)
            ApiKeyChange.Keep -> Unit
        }
        savedReports += report
        val existing = providers.value.firstOrNull { it.id == draft.id }
        val baseUrl = AiEndpoint.normalize(draft.baseUrl)
        val provider = AiProvider(
            id = draft.id,
            name = draft.name.trim().ifEmpty { AiEndpoint.host(baseUrl) ?: baseUrl },
            baseUrl = baseUrl,
            presetId = draft.presetId,
            hasApiKey = draft.id in keys,
            headers = draft.headers,
            defaultModel = draft.defaultModel?.trim()?.ifEmpty { null },
            enabled = draft.enabled,
            sortOrder = existing?.sortOrder ?: ((providers.value.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            timeoutSeconds = draft.timeoutSeconds,
            isLocal = draft.isLocal,
            disclosureAcceptedAt = existing?.disclosureAcceptedAt ?: Instant.EPOCH.takeIf { draft.disclosureAccepted },
            createdAt = existing?.createdAt ?: Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        addProvider(provider, if (report != null && report.modelsFailure == null) report.models else models.value[draft.id].orEmpty())
        return MnemoResult.Success(Unit)
    }

    override suspend fun deleteProvider(id: String) {
        providers.update { list -> list.filterNot { it.id == id } }
        routes.update { list -> list.filterNot { it.providerId == id } }
        keys.remove(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        providers.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    override suspend fun moveProvider(id: String, offset: Int) = providers.update { list ->
        val mutable = list.toMutableList()
        val from = mutable.indexOfFirst { it.id == id }
        if (from < 0) return@update list
        mutable.add((from + offset).coerceIn(0, list.lastIndex), mutable.removeAt(from))
        mutable.mapIndexed { index, p -> p.copy(sortOrder = index) }
    }

    override suspend fun setRoute(task: AiTask, providerId: String?, modelId: String?) = routes.update { list ->
        list.filterNot { it.task == task } + listOfNotNull(providerId?.let { AiTaskRoute(task, it, modelId) })
    }

    override suspend fun acceptDisclosure(providerId: String) =
        providers.update { list -> list.map { if (it.id == providerId) it.copy(disclosureAcceptedAt = Instant.EPOCH) else it } }

    override suspend fun testConnection(draft: AiProviderDraft): AiConnectionReport {
        tested += draft
        return nextReport
    }

    override suspend fun setCapabilities(providerId: String, modelId: String, capabilities: AiCapabilities) = models.update { all ->
        val list = all[providerId].orEmpty()
        val updated = list.filterNot { it.id == modelId } +
            (list.firstOrNull { it.id == modelId } ?: AiModel(providerId, modelId, manual = true)).copy(capabilities = capabilities, capabilitiesSetByUser = true)
        all + (providerId to updated.sortedBy { it.id })
    }

    override suspend fun recordUsage(providerId: String, task: AiTask?, modelId: String, promptTokens: Long, completionTokens: Long, requests: Int) =
        usage.update { it + AiUsageTotal(providerId, providerId, task, requests, promptTokens, completionTokens) }

    override suspend fun pruneOrphanedKeys() {
        keys.keys.retainAll(providers.value.map { it.id }.toSet())
    }

    private fun resolve(task: AiTask, providers: List<AiProvider>, routes: List<AiTaskRoute>): AiRoute? {
        val route = routes.firstOrNull { it.task == task }
        val own = route?.let { r -> providers.firstOrNull { it.id == r.providerId && it.enabled } }
        val ownModel = own?.let { route.modelId ?: it.defaultModel }
        if (own != null && ownModel != null) return AiRoute(task, own, ownModel, AiCapabilities(), usesDefault = false)
        val default = providers.firstOrNull { it.isUsable } ?: return null
        return AiRoute(task, default, default.defaultModel!!, AiCapabilities(), usesDefault = true)
    }
}
