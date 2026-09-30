package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.ai.probe.ModelHeuristics
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.mapper.toAiFailure
import com.yahyafati.mnemo.core.data.mapper.toEntity
import com.yahyafati.mnemo.core.data.mapper.toModel
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.AiProviderDao
import com.yahyafati.mnemo.core.database.entity.AiModelEntity
import com.yahyafati.mnemo.core.database.entity.AiProviderEntity
import com.yahyafati.mnemo.core.database.entity.AiTaskRouteEntity
import com.yahyafati.mnemo.core.database.entity.AiUsageEntity
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionReport
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.model.AiUsageTotal
import com.yahyafati.mnemo.core.security.SecretStore
import com.yahyafati.mnemo.core.security.StoredSecret
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Duration
import java.util.UUID

internal class DefaultAiProviderRepository(
    private val dao: AiProviderDao,
    private val secrets: SecretStore,
    private val probe: ConnectionProbe,
    private val transaction: TransactionRunner,
    private val clock: Clock,
    private val defaultDispatcher: CoroutineDispatcher,
) : AiProviderRepository {
    override fun observeProviders(): Flow<List<AiProvider>> =
        dao.observeProviders().map { rows -> rows.map { it.toModel(hasApiKey = secrets.contains(it.id)) } }

    override fun observeModels(providerId: String): Flow<List<AiModel>> =
        dao.observeModels(providerId).map { rows -> rows.map { it.toModel() } }

    override fun observeAllModels(): Flow<Map<String, List<AiModel>>> =
        dao.observeAllModels().map { rows -> rows.map { it.toModel() }.groupBy { it.providerId } }

    override fun observeRoutes(): Flow<List<AiTaskRoute>> =
        dao.observeRoutes().map { rows -> rows.mapNotNull { it.toModel() } }

    override fun observeEffectiveRoutes(): Flow<Map<AiTask, AiRoute?>> =
        combine(observeProviders(), observeAllModels(), observeRoutes()) { providers, models, routes ->
            AiTask.entries.associateWith { resolve(it, providers, models, routes) }
        }

    override fun observeIsConfigured(): Flow<Boolean> =
        dao.observeProviders().map { rows -> rows.any { it.enabled && !it.defaultModel.isNullOrBlank() } }.distinctUntilChanged()

    override fun observeUsage(): Flow<List<AiUsageTotal>> = dao.observeUsageTotals().map { rows ->
        rows.map { row ->
            AiUsageTotal(
                providerId = row.providerId,
                providerName = row.providerName.orEmpty(),
                task = row.task?.let { name -> AiTask.entries.firstOrNull { it.name == name } },
                requests = row.requests,
                promptTokens = row.promptTokens,
                completionTokens = row.completionTokens,
            )
        }
    }

    override suspend fun getProvider(id: String): AiProvider? = dao.getProvider(id)?.let { it.toModel(secrets.contains(it.id)) }

    override suspend fun routeFor(task: AiTask): AiRoute? {
        val providers = dao.getProviders().map { it.toModel(secrets.contains(it.id)) }
        val models = providers.associate { p -> p.id to dao.getAllModelRows(p.id).filter { it.deletedAt == null }.map { it.toModel() } }
        return resolve(task, providers, models, dao.getRoutes().mapNotNull { it.toModel() })
    }

    override suspend fun saveProvider(draft: AiProviderDraft, report: AiConnectionReport?): MnemoResult<Unit> {
        val baseUrl = AiEndpoint.normalize(draft.baseUrl)
        require(AiEndpoint.check(baseUrl, draft.isLocal) == AiEndpoint.Check.Ok) { "The editor must not save an invalid or insecure base URL" }
        require(draft.headers.all { (k, v) -> AiEndpoint.isValidHeaderName(k.trim()) && AiEndpoint.isValidHeaderValue(v.trim()) }) { "Invalid header" }

        // The key first: if it can't be stored, nothing changes.
        when (val change = draft.apiKey) {
            is ApiKeyChange.Set -> try {
                secrets.put(draft.id, change.value.trim())
            } catch (e: GeneralSecurityException) {
                return MnemoResult.Failure(MnemoError.Storage(e))
            } catch (e: ProviderException) {
                return MnemoResult.Failure(MnemoError.Storage(e))
            } catch (e: IOException) {
                return MnemoResult.Failure(MnemoError.Storage(e))
            }
            ApiKeyChange.Remove -> secrets.remove(draft.id)
            ApiKeyChange.Keep -> Unit
        }

        transaction {
            val now = clock.now().toEpochMilli()
            val existing = dao.getProvider(draft.id)
            val provider = AiProviderEntity(
                id = draft.id,
                name = draft.name.trim().ifEmpty { AiEndpoint.host(baseUrl) ?: baseUrl },
                baseUrl = baseUrl,
                presetId = draft.presetId,
                headers = draft.headers.entries.associate { (k, v) -> k.trim() to v.trim() },
                defaultModel = draft.defaultModel?.trim()?.ifEmpty { null },
                enabled = draft.enabled,
                sortOrder = existing?.sortOrder ?: ((dao.maxSortOrder() ?: -1) + 1),
                timeoutSeconds = draft.timeoutSeconds.coerceIn(AiProvider.MIN_TIMEOUT_SECONDS, AiProvider.MAX_TIMEOUT_SECONDS),
                isLocal = draft.isLocal,
                disclosureAcceptedAt = existing?.disclosureAcceptedAt ?: now.takeIf { draft.disclosureAccepted },
                lastTestOk = report?.ok ?: existing?.lastTestOk,
                lastTestAt = if (report != null) now else existing?.lastTestAt,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            dao.upsertProviders(listOf(provider))
            mergeModels(provider, report, now)
        }
        return MnemoResult.Success(Unit)
    }

    /**
     * Brings the saved models in line with a test: listed models are added or revived, models the
     * provider stopped listing go (unless the user typed them in), the tested model gets the
     * capabilities that were found, and the default model always has a row.
     */
    private suspend fun mergeModels(provider: AiProviderEntity, report: AiConnectionReport?, now: Long) {
        val rows = dao.getAllModelRows(provider.id).associateBy { it.modelId }
        val updates = linkedMapOf<String, AiModelEntity>()
        fun current(id: String) = updates[id] ?: rows[id]
        fun newRow(model: AiModel, manual: Boolean) = model.toEntity(now).copy(manual = manual)

        if (report != null && report.modelsFailure == null) {
            val listed = report.models.map { it.id }.toSet()
            report.models.forEach { model ->
                val old = rows[model.id]
                // Revived rows keep what an earlier test found; new ones start from the name-based guess.
                updates[model.id] = old?.copy(manual = false, deletedAt = null, updatedAt = now) ?: newRow(model, manual = false)
            }
            val gone = rows.values.filter { it.deletedAt == null && !it.manual && it.modelId !in listed }.map { it.modelId }
            if (gone.isNotEmpty()) dao.softDeleteModels(provider.id, gone, now)
        }

        val tested = report?.testedModel
        val found = report?.capabilities
        if (tested != null && found != null) {
            val old = current(tested)
            updates[tested] = when {
                old == null -> newRow(AiModel(provider.id, tested, found), manual = true)
                old.capabilitiesSetByUser -> old.copy(deletedAt = null, updatedAt = now)
                else -> old.copy(
                    supportsJson = found.jsonOutput,
                    supportsVision = found.vision,
                    supportsStreaming = found.streaming,
                    deletedAt = null,
                    updatedAt = now,
                )
            }
        }

        provider.defaultModel?.let { model ->
            val old = current(model)
            if (old == null || old.deletedAt != null) {
                updates[model] = old?.copy(deletedAt = null, updatedAt = now)
                    ?: newRow(AiModel(provider.id, model, AiCapabilities(vision = ModelHeuristics.supportsVision(model))), manual = true)
            }
        }
        if (updates.isNotEmpty()) dao.upsertModels(updates.values.toList())
    }

    override suspend fun deleteProvider(id: String) {
        transaction {
            val now = clock.now().toEpochMilli()
            dao.softDeleteProvider(id, now)
            dao.softDeleteAllModels(id, now)
            dao.softDeleteRoutesFor(id, now)
        }
        secrets.remove(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled, clock.now().toEpochMilli())

    override suspend fun moveProvider(id: String, offset: Int) = transaction {
        val providers = dao.getProviders().toMutableList()
        val from = providers.indexOfFirst { it.id == id }
        if (from < 0) return@transaction
        val to = (from + offset).coerceIn(0, providers.lastIndex)
        if (to == from) return@transaction
        providers.add(to, providers.removeAt(from))
        val now = clock.now().toEpochMilli()
        dao.upsertProviders(
            providers.mapIndexedNotNull { index, p -> p.takeIf { it.sortOrder != index }?.copy(sortOrder = index, updatedAt = now) },
        )
    }

    override suspend fun setRoute(task: AiTask, providerId: String?, modelId: String?) {
        val now = clock.now().toEpochMilli()
        if (providerId == null) {
            dao.softDeleteRoute(task.name, now)
            return
        }
        val existing = dao.getRoutes().firstOrNull { it.task == task.name }
        dao.upsertRoute(AiTaskRouteEntity(task.name, providerId, modelId?.trim()?.ifEmpty { null }, existing?.createdAt ?: now, now))
    }

    override suspend fun acceptDisclosure(providerId: String) = dao.acceptDisclosure(providerId, clock.now().toEpochMilli())

    override suspend fun testConnection(draft: AiProviderDraft): AiConnectionReport {
        val key = when (val change = draft.apiKey) {
            is ApiKeyChange.Set -> change.value.trim().ifEmpty { null }
            ApiKeyChange.Remove -> null
            ApiKeyChange.Keep -> when (val stored = secrets.get(draft.id)) {
                is StoredSecret.Present -> stored.value
                StoredSecret.Missing -> null
                // Sending the request without the key would only fail less clearly.
                StoredSecret.Unreadable -> return AiConnectionReport(
                    models = emptyList(),
                    modelsFailure = AiFailure(AiProblem.KeyUnavailable),
                    testedModel = draft.defaultModel,
                    completionFailure = AiFailure(AiProblem.KeyUnavailable),
                    capabilities = null,
                )
            }
        }
        val config = ProviderConfig(
            baseUrl = AiEndpoint.normalize(draft.baseUrl),
            apiKey = key,
            headers = draft.headers.entries.associate { (k, v) -> k.trim() to v.trim() },
            timeout = Duration.ofSeconds(draft.timeoutSeconds.coerceIn(AiProvider.MIN_TIMEOUT_SECONDS, AiProvider.MAX_TIMEOUT_SECONDS).toLong()),
            isLocal = draft.isLocal,
        )
        // Parsing a long model list (OpenRouter has hundreds) stays off the main thread.
        val result = withContext(defaultDispatcher) { probe.run(config, draft.defaultModel) }
        recordUsage(draft.id, task = null, result.testedModel.orEmpty(), result.usage.promptTokens, result.usage.completionTokens, result.requests)

        val listed = (result.models as? MnemoResult.Success)?.data.orEmpty()
        return AiConnectionReport(
            models = listed
                .filter { ModelHeuristics.isChatModel(it.id) }
                .sortedBy { it.id.lowercase() }
                .map { AiModel(draft.id, it.id, AiCapabilities(vision = ModelHeuristics.supportsVision(it.id, it))) },
            modelsFailure = (result.models as? MnemoResult.Failure)?.error?.toAiFailure(),
            testedModel = result.testedModel,
            completionFailure = (result.completion as? MnemoResult.Failure)?.error?.toAiFailure(),
            capabilities = (result.completion as? MnemoResult.Success)?.data,
        )
    }

    override suspend fun setCapabilities(providerId: String, modelId: String, capabilities: AiCapabilities) {
        val now = clock.now().toEpochMilli()
        val old = dao.getAllModelRows(providerId).firstOrNull { it.modelId == modelId }
        val row = (old ?: AiModel(providerId, modelId, manual = true).toEntity(now)).copy(
            supportsJson = capabilities.jsonOutput,
            supportsVision = capabilities.vision,
            supportsStreaming = capabilities.streaming,
            capabilitiesSetByUser = true,
            deletedAt = null,
            updatedAt = now,
        )
        dao.upsertModels(listOf(row))
    }

    override suspend fun recordUsage(providerId: String, task: AiTask?, modelId: String, promptTokens: Long, completionTokens: Long, requests: Int) {
        val now = clock.now().toEpochMilli()
        dao.insertUsage(AiUsageEntity(UUID.randomUUID().toString(), providerId, task?.name, modelId, requests, promptTokens, completionTokens, now, now))
    }

    override suspend fun pruneOrphanedKeys() = secrets.retainOnly(dao.getProviders().map { it.id }.toSet())

    private fun resolve(
        task: AiTask,
        providers: List<AiProvider>,
        models: Map<String, List<AiModel>>,
        routes: List<AiTaskRoute>,
    ): AiRoute? {
        fun capabilities(providerId: String, modelId: String) =
            models[providerId]?.firstOrNull { it.id == modelId }?.capabilities ?: AiCapabilities(vision = ModelHeuristics.supportsVision(modelId))

        val route = routes.firstOrNull { it.task == task }
        val own = route?.let { r -> providers.firstOrNull { it.id == r.providerId && it.enabled } }
        val ownModel = own?.let { route.modelId ?: it.defaultModel }?.takeIf { it.isNotBlank() }
        if (own != null && ownModel != null) return AiRoute(task, own, ownModel, capabilities(own.id, ownModel), usesDefault = false)

        val default = providers.firstOrNull { it.isUsable } ?: return null
        val model = default.defaultModel!!
        return AiRoute(task, default, model, capabilities(default.id, model), usesDefault = true)
    }
}
