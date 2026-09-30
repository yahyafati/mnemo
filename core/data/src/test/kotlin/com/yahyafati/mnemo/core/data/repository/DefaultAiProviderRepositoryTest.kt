package com.yahyafati.mnemo.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.android.AndroidAppDirectories
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AiTaskRoute
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DefaultAiProviderRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = MnemoDatabase.build(context, name = null)
    private val clock = TestClock(Instant.parse("2026-09-01T10:00:00Z"))
    private val cipher = SoftwareSecretCipher()
    private val secrets = FileSecretStore(AndroidAppDirectories(context), cipher, Dispatchers.Unconfined)
    private val server = MockWebServer()
    private val repository = DefaultAiProviderRepository(
        db.aiProviderDao(), secrets, ConnectionProbe(OpenAiCompatibleClient(OkHttpClient())),
        RoomTransactionRunner(db), clock, Dispatchers.Unconfined,
    )

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.close()
        db.close()
        File(context.noBackupFilesDir, "secrets").deleteRecursively()
    }

    private fun draft(id: String, model: String? = "m", key: ApiKeyChange = ApiKeyChange.Keep) = AiProviderDraft(
        id = id,
        name = "Provider $id",
        baseUrl = server.url("/v1/").toString(),
        apiKey = key,
        defaultModel = model,
        isLocal = true,
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun enqueueWorkingServer(models: String = """{"data":[{"id":"m"},{"id":"other"},{"id":"text-embedding-3-small"}]}""") {
        server.enqueue(json(models))
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body("data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n").build())
        server.enqueue(json("""{"choices":[{"message":{"content":"{}"}}],"usage":{"prompt_tokens":10,"completion_tokens":1}}"""))
    }

    @Test
    fun keysAreEncryptedOutsideTheDatabase() = runTest {
        assertIs<MnemoResult.Success<Unit>>(repository.saveProvider(draft("p", key = ApiKeyChange.Set(" sk-secret-key-123 "))))
        val provider = repository.getProvider("p")!!
        assertTrue(provider.hasApiKey)
        // Nowhere in the provider's row.
        db.openHelper.readableDatabase.query("SELECT * FROM ai_providers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            for (i in 0 until cursor.columnCount) assertFalse(cursor.getString(i).orEmpty().contains("sk-secret"))
        }

        // The stored key (trimmed) goes out with the test request.
        enqueueWorkingServer()
        repository.testConnection(draft("p"))
        assertEquals("Bearer sk-secret-key-123", server.takeRequest().headers["Authorization"])

        repository.saveProvider(draft("p", key = ApiKeyChange.Remove))
        assertFalse(repository.getProvider("p")!!.hasApiKey)
    }

    @Test
    fun aTestFindsModelsAndCapabilitiesAndSavingKeepsThem() = runTest {
        enqueueWorkingServer()
        val report = repository.testConnection(draft("p"))
        assertTrue(report.ok)
        // Embedding models are left out of the picker.
        assertEquals(listOf("m", "other"), report.models.map { it.id })
        assertEquals(AiCapabilities(jsonOutput = true, vision = false, streaming = true), report.capabilities)
        // The test's tokens are logged even before the provider is saved.
        assertEquals(listOf(3 to 11L), repository.observeUsage().first().map { it.requests to it.totalTokens })

        repository.saveProvider(draft("p"), report)
        val models = repository.observeModels("p").first()
        assertEquals(listOf("m", "other"), models.map { it.id })
        assertEquals(AiCapabilities(jsonOutput = true, vision = false, streaming = true), models.first { it.id == "m" }.capabilities)
        assertTrue(repository.getProvider("p")!!.lastTest!!.ok)

        // A later list without "other" drops it, but keeps a model the user typed in.
        repository.saveProvider(draft("p", model = "typed-in"))
        enqueueWorkingServer(models = """{"data":[{"id":"m"}]}""")
        val second = repository.testConnection(draft("p", model = "m"))
        repository.saveProvider(draft("p", model = "typed-in"), second)
        assertEquals(listOf("m", "typed-in"), repository.observeModels("p").first().map { it.id })
    }

    @Test
    fun userSetCapabilitiesSurviveTests() = runTest {
        repository.saveProvider(draft("p"))
        repository.setCapabilities("p", "m", AiCapabilities(jsonOutput = false, vision = true, streaming = false))
        enqueueWorkingServer()
        repository.saveProvider(draft("p"), repository.testConnection(draft("p")))
        val model = repository.observeModels("p").first().first { it.id == "m" }
        assertEquals(AiCapabilities(jsonOutput = false, vision = true, streaming = false), model.capabilities)
        assertTrue(model.capabilitiesSetByUser)
    }

    @Test
    fun anUnreadableKeyIsReportedWithoutSendingAnything() = runTest {
        repository.saveProvider(draft("p", key = ApiKeyChange.Set("sk-1234567890")))
        cipher.keyLost = true
        val report = repository.testConnection(draft("p"))
        assertEquals(AiProblem.KeyUnavailable, report.completionFailure?.problem)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun failuresAreExplained() = runTest {
        server.enqueue(json("""{"error":{"message":"bad key"}}""", code = 401))
        server.enqueue(json("""{"error":{"message":"bad key"}}""", code = 401))
        val report = repository.testConnection(draft("p"))
        assertFalse(report.ok)
        assertEquals(AiProblem.Unauthorized, report.modelsFailure?.problem)
        assertEquals("HTTP 401: bad key", report.completionFailure?.detail)
    }

    @Test
    fun routingFallsBackToTheDefaultProvider() = runTest {
        assertNull(repository.routeFor(AiTask.Extract))
        assertFalse(repository.observeIsConfigured().first())

        // A provider without a model can't be used.
        repository.saveProvider(draft("a", model = null))
        assertNull(repository.routeFor(AiTask.Extract))
        repository.saveProvider(draft("a", model = "a-model"))
        repository.saveProvider(draft("b", model = "b-model"))
        assertTrue(repository.observeIsConfigured().first())

        // No route: the first provider in the list.
        val default = repository.routeFor(AiTask.Explain)!!
        assertEquals("a" to "a-model", default.provider.id to default.modelId)
        assertTrue(default.usesDefault)

        // A task with its own route, with and without its own model.
        repository.setRoute(AiTask.Extract, "b", "b-big")
        repository.setRoute(AiTask.Rewrite, "b")
        assertEquals("b-big", repository.routeFor(AiTask.Extract)!!.modelId)
        assertEquals("b-model", repository.routeFor(AiTask.Rewrite)!!.modelId)
        assertFalse(repository.routeFor(AiTask.Rewrite)!!.usesDefault)
        assertEquals(
            setOf(AiTaskRoute(AiTask.Extract, "b", "b-big"), AiTaskRoute(AiTask.Rewrite, "b", null)),
            repository.observeRoutes().first().toSet(),
        )

        // Disabled: back to the default.
        repository.setEnabled("b", false)
        assertEquals("a", repository.routeFor(AiTask.Extract)!!.provider.id)

        // Moving b to the top makes it the default once it's enabled again.
        repository.setEnabled("b", true)
        repository.moveProvider("b", -1)
        assertEquals(listOf("b", "a"), repository.observeProviders().first().map { it.id })
        assertEquals("b", repository.observeEffectiveRoutes().first()[AiTask.Explain]!!.provider.id)

        repository.setRoute(AiTask.Extract, null)
        assertTrue(repository.routeFor(AiTask.Extract)!!.usesDefault)
    }

    @Test
    fun deletingAProviderDestroysItsKeyAndRoutes() = runTest {
        repository.saveProvider(draft("a", key = ApiKeyChange.Set("sk-aaaaaaaaaa")))
        repository.saveProvider(draft("b"))
        repository.setRoute(AiTask.Extract, "a")
        repository.deleteProvider("a")
        assertEquals(listOf("b"), repository.observeProviders().first().map { it.id })
        assertTrue(repository.observeRoutes().first().isEmpty())
        assertFalse(secrets.contains("a"))
        assertEquals("b", repository.routeFor(AiTask.Extract)!!.provider.id)
    }

    @Test
    fun orphanedKeysArePruned() = runTest {
        secrets.put("gone", "sk-left-behind")
        repository.saveProvider(draft("kept", key = ApiKeyChange.Set("sk-kept-12345")))
        repository.pruneOrphanedKeys()
        assertFalse(secrets.contains("gone"))
        assertTrue(secrets.contains("kept"))
    }

    @Test
    fun disclosureIsRemembered() = runTest {
        repository.saveProvider(draft("a").copy(disclosureAccepted = true))
        assertEquals(clock.now(), repository.getProvider("a")!!.disclosureAcceptedAt)
        repository.saveProvider(draft("b"))
        assertNull(repository.getProvider("b")!!.disclosureAcceptedAt)
        repository.acceptDisclosure("b")
        assertEquals(clock.now(), repository.getProvider("b")!!.disclosureAcceptedAt)
    }
}
