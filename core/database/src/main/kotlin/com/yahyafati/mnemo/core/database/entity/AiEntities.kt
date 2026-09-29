package com.yahyafati.mnemo.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An OpenAI-compatible provider. The API key is deliberately not a column: it is encrypted in the
 * secret store (`:core:security`), outside the database, so no backup or export can carry it
 * (ADR 0005). [headers] is a JSON object.
 */
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val presetId: String?,
    val headers: Map<String, String>,
    val defaultModel: String?,
    val enabled: Boolean,
    val sortOrder: Int,
    val timeoutSeconds: Int,
    val isLocal: Boolean,
    val disclosureAcceptedAt: Long?,
    val lastTestOk: Boolean?,
    val lastTestAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A model of a provider, with its capabilities. Keyed by provider and model id, like the API. */
@Entity(tableName = "ai_models", primaryKeys = ["providerId", "modelId"])
data class AiModelEntity(
    val providerId: String,
    val modelId: String,
    val supportsJson: Boolean,
    val supportsVision: Boolean,
    val supportsStreaming: Boolean,
    /** Typed in by the user: kept when the list is refreshed from the provider. */
    val manual: Boolean,
    val capabilitiesSetByUser: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Which provider (and model) an `AiTask` uses. One row per task; the task name is the key. */
@Entity(tableName = "ai_task_routes", indices = [Index("providerId")])
data class AiTaskRouteEntity(
    @PrimaryKey val task: String,
    val providerId: String,
    /** Null: the provider's default model. */
    val modelId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Tokens one request used, as the provider reported them. A null [task] is a connection test. */
@Entity(tableName = "ai_usage", indices = [Index("providerId", "task")])
data class AiUsageEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val task: String?,
    val modelId: String,
    val requests: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
