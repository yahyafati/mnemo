package com.yahyafati.mnemo.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.yahyafati.mnemo.core.database.entity.AiModelEntity
import com.yahyafati.mnemo.core.database.entity.AiProviderEntity
import com.yahyafati.mnemo.core.database.entity.AiTaskRouteEntity
import com.yahyafati.mnemo.core.database.entity.AiUsageEntity
import kotlinx.coroutines.flow.Flow

/** Providers, their models, task routes and token usage (ARCHITECTURE §5.3). */
@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    suspend fun getProviders(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id AND deletedAt IS NULL")
    suspend fun getProvider(id: String): AiProviderEntity?

    @Upsert
    suspend fun upsertProviders(providers: List<AiProviderEntity>)

    @Query("SELECT MAX(sortOrder) FROM ai_providers WHERE deletedAt IS NULL")
    suspend fun maxSortOrder(): Int?

    @Query("UPDATE ai_providers SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Query("UPDATE ai_providers SET lastTestOk = :ok, lastTestAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun setTestResult(id: String, ok: Boolean, now: Long)

    @Query("UPDATE ai_providers SET defaultModel = :modelId, updatedAt = :now WHERE id = :id")
    suspend fun setDefaultModel(id: String, modelId: String?, now: Long)

    @Query("UPDATE ai_providers SET disclosureAcceptedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun acceptDisclosure(id: String, now: Long)

    @Query("UPDATE ai_providers SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteProvider(id: String, now: Long)

    @Query("SELECT * FROM ai_models WHERE deletedAt IS NULL ORDER BY providerId, modelId COLLATE NOCASE")
    fun observeAllModels(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE providerId = :providerId AND deletedAt IS NULL ORDER BY modelId COLLATE NOCASE")
    fun observeModels(providerId: String): Flow<List<AiModelEntity>>

    /** Every model row of the provider, deleted ones too, so a refresh can bring one back. */
    @Query("SELECT * FROM ai_models WHERE providerId = :providerId")
    suspend fun getAllModelRows(providerId: String): List<AiModelEntity>

    @Upsert
    suspend fun upsertModels(models: List<AiModelEntity>)

    @Query("UPDATE ai_models SET deletedAt = :now, updatedAt = :now WHERE providerId = :providerId AND modelId IN (:modelIds)")
    suspend fun softDeleteModels(providerId: String, modelIds: List<String>, now: Long)

    @Query("UPDATE ai_models SET deletedAt = :now, updatedAt = :now WHERE providerId = :providerId AND deletedAt IS NULL")
    suspend fun softDeleteAllModels(providerId: String, now: Long)

    @Query("SELECT * FROM ai_task_routes WHERE deletedAt IS NULL")
    fun observeRoutes(): Flow<List<AiTaskRouteEntity>>

    @Query("SELECT * FROM ai_task_routes WHERE deletedAt IS NULL")
    suspend fun getRoutes(): List<AiTaskRouteEntity>

    @Upsert
    suspend fun upsertRoute(route: AiTaskRouteEntity)

    @Query("UPDATE ai_task_routes SET deletedAt = :now, updatedAt = :now WHERE task = :task")
    suspend fun softDeleteRoute(task: String, now: Long)

    @Query("UPDATE ai_task_routes SET deletedAt = :now, updatedAt = :now WHERE providerId = :providerId AND deletedAt IS NULL")
    suspend fun softDeleteRoutesFor(providerId: String, now: Long)

    @Insert
    suspend fun insertUsage(usage: AiUsageEntity)

    /** Totals per provider and task, with the provider's name even after it was deleted. */
    @Query(
        """
        SELECT u.providerId AS providerId, p.name AS providerName, u.task AS task,
            SUM(u.requests) AS requests, SUM(u.promptTokens) AS promptTokens, SUM(u.completionTokens) AS completionTokens
        FROM ai_usage u
        LEFT JOIN ai_providers p ON p.id = u.providerId
        WHERE u.deletedAt IS NULL
        GROUP BY u.providerId, u.task
        ORDER BY providerName COLLATE NOCASE, u.task
        """,
    )
    fun observeUsageTotals(): Flow<List<AiUsageTotalRow>>
}

data class AiUsageTotalRow(
    val providerId: String,
    val providerName: String?,
    val task: String?,
    val requests: Int,
    val promptTokens: Long,
    val completionTokens: Long,
)
