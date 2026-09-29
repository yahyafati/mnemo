package com.yahyafati.mnemo.core.database

import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.database.dao.AiUsageTotalRow
import com.yahyafati.mnemo.core.database.entity.AiModelEntity
import com.yahyafati.mnemo.core.database.entity.AiProviderEntity
import com.yahyafati.mnemo.core.database.entity.AiTaskRouteEntity
import com.yahyafati.mnemo.core.database.entity.AiUsageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AiProviderDaoTest {
    private lateinit var db: MnemoDatabase
    private val dao get() = db.aiProviderDao()

    @Before
    fun setUp() {
        db = MnemoDatabase.build(ApplicationProvider.getApplicationContext(), name = null)
    }

    @After
    fun tearDown() = db.close()

    private fun provider(id: String, sortOrder: Int, headers: Map<String, String> = emptyMap()) = AiProviderEntity(
        id, "P-$id", "https://$id.example/v1", null, headers, "m", enabled = true, sortOrder = sortOrder,
        timeoutSeconds = 60, isLocal = false, disclosureAcceptedAt = null, lastTestOk = null, lastTestAt = null, createdAt = 0, updatedAt = 0,
    )

    private fun model(providerId: String, id: String) = AiModelEntity(providerId, id, false, false, true, false, false, 0, 0)

    @Test
    fun providersInOrderWithHeaders() = runTest {
        dao.upsertProviders(listOf(provider("b", 1, mapOf("X-Title" to "Mnemo")), provider("a", 0)))
        assertEquals(listOf("a", "b"), dao.observeProviders().first().map { it.id })
        assertEquals(mapOf("X-Title" to "Mnemo"), dao.getProvider("b")?.headers)
        assertEquals(1, dao.maxSortOrder())

        dao.softDeleteProvider("a", now = 5)
        assertEquals(listOf("b"), dao.getProviders().map { it.id })
        assertEquals(null, dao.getProvider("a"))
    }

    @Test
    fun modelsAreSoftDeletedAndRevived() = runTest {
        dao.upsertModels(listOf(model("p", "x"), model("p", "y"), model("q", "x")))
        dao.softDeleteModels("p", listOf("x"), now = 3)
        assertEquals(listOf("y"), dao.observeModels("p").first().map { it.modelId })
        assertEquals(2, dao.getAllModelRows("p").size)
        dao.upsertModels(listOf(model("p", "x")))
        assertEquals(listOf("x", "y"), dao.observeModels("p").first().map { it.modelId })
        dao.softDeleteAllModels("p", now = 4)
        assertEquals(listOf("q"), dao.observeAllModels().first().map { it.providerId })
    }

    @Test
    fun routes() = runTest {
        dao.upsertRoute(AiTaskRouteEntity("Extract", "p", "big", 0, 0))
        dao.upsertRoute(AiTaskRouteEntity("Explain", "q", null, 0, 0))
        dao.softDeleteRoutesFor("p", now = 1)
        assertEquals(listOf("Explain"), dao.getRoutes().map { it.task })
        // Setting a route again brings the row back.
        dao.upsertRoute(AiTaskRouteEntity("Extract", "q", null, 0, 2))
        assertEquals(setOf("Extract", "Explain"), dao.observeRoutes().first().map { it.task }.toSet())
        dao.softDeleteRoute("Explain", now = 3)
        assertEquals(listOf("Extract"), dao.getRoutes().map { it.task })
    }

    @Test
    fun usageTotalsKeepDeletedProviderNames() = runTest {
        dao.upsertProviders(listOf(provider("a", 0)))
        dao.insertUsage(AiUsageEntity("1", "a", null, "m", 3, 30, 3, 0, 0))
        dao.insertUsage(AiUsageEntity("2", "a", "Extract", "m", 1, 1000, 500, 0, 0))
        dao.insertUsage(AiUsageEntity("3", "a", "Extract", "m", 1, 200, 100, 0, 0))
        dao.softDeleteProvider("a", now = 1)
        assertEquals(
            listOf(
                AiUsageTotalRow("a", "P-a", null, 3, 30, 3),
                AiUsageTotalRow("a", "P-a", "Extract", 2, 1200, 600),
            ),
            dao.observeUsageTotals().first(),
        )
    }
}
