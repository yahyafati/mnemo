package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.ai.probe.ModelHeuristics
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionProbeTest {
    private val server = MockWebServer()
    private val probe = ConnectionProbe(OpenAiCompatibleClient(OkHttpClient()))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val config get() = ProviderConfig(baseUrl = server.url("/v1").toString(), isLocal = true)

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private val stream = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body("data: {\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}\n\ndata: [DONE]\n\n")
        .build()

    private val completion = json("""{"choices":[{"message":{"content":"{\"ok\""}}],"usage":{"prompt_tokens":12,"completion_tokens":1}}""")

    @Test
    fun aFullTest() = runTest {
        server.enqueue(json("""{"data":[{"id":"gpt-4o-mini"},{"id":"text-embedding-3-small"}]}"""))
        server.enqueue(stream)
        server.enqueue(completion)

        val result = probe.run(config, "gpt-4o-mini")
        assertEquals("gpt-4o-mini", result.testedModel)
        assertEquals(AiCapabilities(jsonOutput = true, vision = true, streaming = true), assertIs<MnemoResult.Success<AiCapabilities>>(result.completion).data)
        assertEquals(3, result.requests)
        assertEquals(12, result.usage.promptTokens)

        server.takeRequest()
        assertTrue(server.takeRequest().body!!.utf8().contains(""""max_tokens":1"""))
        assertTrue(server.takeRequest().body!!.utf8().contains(""""response_format":{"type":"json_schema""""))
    }

    @Test
    fun withoutAModelOnlyTheListIsFetched() = runTest {
        server.enqueue(json("""{"data":[{"id":"a"},{"id":"b"}]}"""))
        val result = probe.run(config, model = null)
        assertNull(result.testedModel)
        assertNull(result.completion)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aSingleListedModelIsTestedWithoutAsking() = runTest {
        server.enqueue(json("""{"data":[{"id":"nomic-embed-text:latest"},{"id":"llama3.2"}]}"""))
        server.enqueue(stream)
        server.enqueue(json("""{"error":"response_format not supported"}""", code = 400))
        val result = probe.run(config, model = " ")
        assertEquals("llama3.2", result.testedModel)
        assertEquals(AiCapabilities(jsonOutput = false, vision = false, streaming = true), (result.completion as MnemoResult.Success).data)
    }

    @Test
    fun retriesWithMaxCompletionTokens() = runTest {
        server.enqueue(json("""{"data":[]}"""))
        server.enqueue(json("""{"error":{"message":"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead."}}""", code = 400))
        server.enqueue(stream)
        server.enqueue(completion)

        val result = probe.run(config, "o4-mini")
        assertTrue(result.completion is MnemoResult.Success)
        repeat(2) { server.takeRequest() }
        assertTrue(server.takeRequest().body!!.utf8().contains(""""max_completion_tokens":1"""))
        assertTrue(server.takeRequest().body!!.utf8().contains(""""max_completion_tokens":1"""))
    }

    @Test
    fun serversThatDoNotStream() = runTest {
        // Answers the streamed request with plain JSON.
        server.enqueue(json("""{"data":[]}"""))
        server.enqueue(completion)
        server.enqueue(completion)
        assertFalse((probe.run(config, "m").completion as MnemoResult.Success).data.streaming)

        // Rejects `stream` outright: tried again without it.
        server.enqueue(json("""{"data":[]}"""))
        server.enqueue(json("""{"error":"stream is not supported"}""", code = 400))
        server.enqueue(completion)
        server.enqueue(completion)
        val capabilities = (probe.run(config, "m").completion as MnemoResult.Success).data
        assertFalse(capabilities.streaming)
        assertTrue(capabilities.jsonOutput)
    }

    @Test
    fun aFailedCompletionIsReportedWithTheModels() = runTest {
        server.enqueue(json("""{"data":[{"id":"a"}]}"""))
        server.enqueue(json("""{"error":{"message":"Invalid API key"}}""", code = 401))
        val result = probe.run(config, "a")
        assertTrue(result.models is MnemoResult.Success)
        assertEquals(MnemoError.Http(401, "Invalid API key"), (result.completion as MnemoResult.Failure).error)
    }

    @Test
    fun modelHeuristics() {
        assertTrue(ModelHeuristics.supportsVision("qwen2.5-vl:7b"))
        assertTrue(ModelHeuristics.supportsVision("google/gemini-2.5-flash"))
        assertFalse(ModelHeuristics.supportsVision("deepseek-chat"))
        // The provider's own description wins over the name.
        val described = com.yahyafati.mnemo.core.ai.dto.ModelInfo("vision-free", architecture = com.yahyafati.mnemo.core.ai.dto.ModelInfo.Architecture(listOf("text")))
        assertFalse(ModelHeuristics.supportsVision(described.id, described))

        assertTrue(ModelHeuristics.isChatModel("gpt-4.1-mini"))
        assertFalse(ModelHeuristics.isChatModel("text-embedding-3-large"))
        assertFalse(ModelHeuristics.isChatModel("whisper-1"))
    }
}
