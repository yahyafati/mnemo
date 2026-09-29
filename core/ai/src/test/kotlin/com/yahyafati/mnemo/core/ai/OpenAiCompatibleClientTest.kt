package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.ChatStreamEvent
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiCompatibleClientTest {
    private val server = MockWebServer()
    private val client = OpenAiCompatibleClient(OkHttpClient())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    /** MockWebServer speaks plain HTTP on localhost, so the provider is marked local. */
    private fun config(key: String? = KEY, headers: Map<String, String> = emptyMap()) = ProviderConfig(
        baseUrl = server.url("/v1/").toString(),
        apiKey = key,
        headers = headers,
        timeout = Duration.ofSeconds(5),
        isLocal = true,
    )

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun sse(vararg events: String) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(events.joinToString("") { "data: $it\n\n" })
        .build()

    private val hello = ChatRequest("m", listOf(ChatMessage.user("Hi")), maxTokens = 1)

    @Test
    fun listsModelsWithKeyAndHeaders() = runTest {
        server.enqueue(json("""{"object":"list","data":[{"id":"llama3","owned_by":"me"},{"id":"qwen","extra":1}]}"""))
        val result = client.listModels(config(headers = mapOf("X-Title" to "Mnemo")))
        assertEquals(listOf("llama3", "qwen"), assertIs<MnemoResult.Success<List<*>>>(result).data.map { (it as com.yahyafati.mnemo.core.ai.dto.ModelInfo).id })

        val request = server.takeRequest()
        assertEquals("/v1/models", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertEquals("Bearer $KEY", request.headers["Authorization"])
        assertEquals("Mnemo", request.headers["X-Title"])
    }

    @Test
    fun noKeyMeansNoAuthorizationHeader() = runTest {
        server.enqueue(json("""{"data":[]}"""))
        client.listModels(config(key = null))
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun completionsLeaveOutUnsetFields() = runTest {
        server.enqueue(json("""{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"OK"},"finish_reason":"length"}],"usage":{"prompt_tokens":9,"completion_tokens":1}}"""))
        val result = assertIs<MnemoResult.Success<com.yahyafati.mnemo.core.ai.dto.ChatResponse>>(client.complete(config(), hello))
        assertEquals("OK", result.data.text)
        assertEquals(9, result.data.usage?.promptTokens)

        val body = server.takeRequest().body!!.utf8()
        assertEquals("""{"model":"m","messages":[{"role":"user","content":"Hi"}],"max_tokens":1}""", body)
    }

    @Test
    fun httpErrorsCarryTheServerMessageWithoutTheKey() = runTest {
        server.enqueue(json("""{"error":{"message":"Incorrect API key provided: $KEY","type":"invalid_request_error"}}""", code = 401))
        val error = assertIs<MnemoError.Http>(assertIs<MnemoResult.Failure>(client.complete(config(), hello)).error)
        assertEquals(401, error.code)
        assertEquals("Incorrect API key provided: •••", error.body)
        assertFalse(error.toString().contains(KEY))
    }

    @Test
    fun unreadableAnswersAreParseErrors() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>Welcome to nginx</html>").build())
        assertIs<MnemoError.Parse>(assertIs<MnemoResult.Failure>(client.listModels(config())).error)
    }

    @Test
    fun streamsChunksUntilDone() = runTest {
        server.enqueue(
            sse(
                """{"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"}}]}""",
                """{"choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":"stop"}]}""",
                "[DONE]",
            ),
        )
        val events = client.stream(config(), hello).toList()
        assertEquals(listOf("Hel", "lo"), events.map { assertIs<ChatStreamEvent.Chunk>(it).chunk.text })
        val request = server.takeRequest()
        assertEquals("text/event-stream", request.headers["Accept"])
        assertTrue(request.body!!.utf8().contains(""""stream":true"""))
    }

    @Test
    fun aServerThatIgnoresStreamGivesOneWholeAnswer() = runTest {
        server.enqueue(json("""{"choices":[{"message":{"role":"assistant","content":"OK"}}]}"""))
        val event = client.stream(config(), hello).toList().single()
        assertEquals("OK", assertIs<ChatStreamEvent.Whole>(event).response.text)
    }

    @Test
    fun streamFailures() = runTest {
        server.enqueue(json("""{"error":"model 'x' not found"}""", code = 404))
        val http = assertIs<ChatStreamEvent.Failed>(client.stream(config(), hello).toList().single()).error
        assertEquals(MnemoError.Http(404, "model 'x' not found"), http)

        // Some servers put the error inside the stream.
        server.enqueue(sse("""{"choices":[{"delta":{"content":"a"}}]}""", """{"error":{"message":"overloaded"}}"""))
        val events = client.stream(config(), hello).toList()
        assertIs<ChatStreamEvent.Chunk>(events.first())
        assertEquals("overloaded", assertIs<MnemoError.Http>(assertIs<ChatStreamEvent.Failed>(events.last()).error).body)
    }

    @Test
    fun plainHttpNeedsALocalProviderOnALocalHost() = runTest {
        val remote = ProviderConfig(baseUrl = "http://api.example.com/v1", apiKey = KEY)
        assertIs<MnemoError.Blocked>(assertIs<MnemoResult.Failure>(client.listModels(remote)).error)
        // Marking a public host "local" doesn't help.
        val lying = ProviderConfig(baseUrl = "http://api.example.com/v1", apiKey = KEY, isLocal = true)
        assertIs<MnemoError.Blocked>(assertIs<MnemoResult.Failure>(client.listModels(lying)).error)
        val stream = client.stream(ProviderConfig(baseUrl = server.url("/v1").toString()), hello).toList().single()
        assertIs<MnemoError.Blocked>(assertIs<ChatStreamEvent.Failed>(stream).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun badHeadersAreRefusedBeforeSending() = runTest {
        val result = client.listModels(config(headers = mapOf("X-Bad" to "line\nbreak")))
        assertIs<MnemoError.Blocked>(assertIs<MnemoResult.Failure>(result).error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unreachableServersAreNetworkErrors() = runTest {
        val port = server.port
        server.close()
        val result = client.listModels(ProviderConfig(baseUrl = "http://127.0.0.1:$port/v1", isLocal = true, timeout = Duration.ofSeconds(2)))
        assertIs<MnemoError.Network>(assertIs<MnemoResult.Failure>(result).error)
    }

    @Test
    fun configNeverPrintsTheKey() {
        val text = ProviderConfig("https://api.openai.com/v1", apiKey = KEY, headers = mapOf("OpenAI-Organization" to "org-secret")).toString()
        assertFalse(KEY in text)
        assertFalse("org-secret" in text)
    }

    private companion object {
        const val KEY = "sk-test-0123456789abcdef"
    }
}
