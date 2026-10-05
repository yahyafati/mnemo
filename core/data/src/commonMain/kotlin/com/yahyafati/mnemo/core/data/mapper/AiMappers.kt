package com.yahyafati.mnemo.core.data.mapper

import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.database.entity.AiModelEntity
import com.yahyafati.mnemo.core.database.entity.AiProviderEntity
import com.yahyafati.mnemo.core.database.entity.AiTaskRouteEntity
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiConnectionStatus
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import java.io.InterruptedIOException
import java.time.Instant

internal fun AiProviderEntity.toModel(hasApiKey: Boolean) = AiProvider(
    id = id,
    name = name,
    baseUrl = baseUrl,
    presetId = presetId,
    hasApiKey = hasApiKey,
    headers = headers,
    defaultModel = defaultModel,
    enabled = enabled,
    sortOrder = sortOrder,
    timeoutSeconds = timeoutSeconds,
    isLocal = isLocal,
    disclosureAcceptedAt = disclosureAcceptedAt?.let(Instant::ofEpochMilli),
    lastTest = lastTestOk?.let { ok -> lastTestAt?.let { at -> AiConnectionStatus(ok, Instant.ofEpochMilli(at)) } },
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

internal fun AiModelEntity.toModel() = AiModel(
    providerId = providerId,
    id = modelId,
    capabilities = AiCapabilities(jsonOutput = supportsJson, vision = supportsVision, streaming = supportsStreaming),
    manual = manual,
    capabilitiesSetByUser = capabilitiesSetByUser,
)

internal fun AiModel.toEntity(now: Long) = AiModelEntity(
    providerId = providerId,
    modelId = id,
    supportsJson = capabilities.jsonOutput,
    supportsVision = capabilities.vision,
    supportsStreaming = capabilities.streaming,
    manual = manual,
    capabilitiesSetByUser = capabilitiesSetByUser,
    createdAt = now,
    updatedAt = now,
)

/** Null for a task this version doesn't know (written by a newer one). */
internal fun AiTaskRouteEntity.toModel(): AiTaskRoute? =
    AiTask.entries.firstOrNull { it.name == task }?.let { AiTaskRoute(it, providerId, modelId) }

/** Client errors in terms the settings screen can explain. */
internal fun MnemoError.toAiFailure(): AiFailure = when (this) {
    is MnemoError.Http -> AiFailure(
        problem = when (code) {
            401, 403 -> AiProblem.Unauthorized
            404 -> AiProblem.NotFound
            429 -> AiProblem.RateLimited
            in 300..499 -> AiProblem.BadRequest
            else -> AiProblem.ServerError
        },
        detail = listOfNotNull("HTTP $code", body).joinToString(": "),
    )
    // OkHttp's read timeout is a SocketTimeoutException, its call timeout an InterruptedIOException("timeout").
    is MnemoError.Network -> AiFailure(if (cause is InterruptedIOException) AiProblem.Timeout else AiProblem.Unreachable, cause?.message)
    is MnemoError.ImagesNotAccepted -> AiFailure(AiProblem.ImagesNotAccepted, detail)
    is MnemoError.Blocked -> AiFailure(AiProblem.InsecureUrl, reason)
    is MnemoError.Parse -> AiFailure(AiProblem.InvalidResponse, message)
    else -> AiFailure(AiProblem.Unknown, cause?.message)
}
