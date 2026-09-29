package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.generate.CardEvent
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.AssistRequest
import com.yahyafati.mnemo.core.ai.prompt.CardGenerationPrompt
import com.yahyafati.mnemo.core.ai.prompt.StudyAssistPrompt
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.NoteKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

class CardGenerationClientTest {
    private val server = MockWebServer()
    private val runner = ChatTextRunner(OpenAiCompatibleClient(OkHttpClient()))
    private val generator = CardGenerationClient(runner)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val config get() = ProviderConfig(server.url("/v1/").toString(), apiKey = "sk-test-key", timeout = Duration.ofSeconds(5), isLocal = true)

    private val prompt = CardGenerationPrompt(source = "Mitochondria make ATP.", options = ExtractOptions(), targetCards = 3)

    private val twoCards =
        """{"cards": [{"type": "basic", "front": "What makes ATP?", "back": "Mitochondria", "tags": ["cells"]}, """ +
            """{"type": "cloze", "front": "{{c1::Mitochondria}} make ATP.", "back": "", "tags": []}]}"""

    /** An SSE stream that sends [text] in pieces of [piece] characters, then usage, then [DONE]. */
    private fun sse(text: String, piece: Int = 9) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(
            text.chunked(piece).joinToString("") { "data: ${delta(it)}\n\n" } +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":40}}\n\n" +
                "data: [DONE]\n\n",
        )
        .build()

    private fun delta(content: String) = buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject { putJsonObject("delta") { put("content", content) } }) })
    }.toString()

    private fun completion(text: String, code: Int = 200) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body(
            buildJsonObject {
                put(
                    "choices",
                    buildJsonArray {
                        add(buildJsonObject { putJsonObject("message") { put("role", "assistant"); put("content", text) } })
                    },
                )
                putJsonObject("usage") { put("prompt_tokens", 100); put("completion_tokens", 30) }
            }.toString(),
        )
        .build()

    private fun error(code: Int, message: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/json")
        .body("""{"error": {"message": "$message"}}""")
        .build()

    private fun body() = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    @Test
    fun structuredStreamingEmitsCardsAsTheyArrive() = runTest {
        server.enqueue(sse(twoCards))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = true, streaming = true), prompt).toList()

        val cards = events.filterIsInstance<CardEvent.Card>().map { it.card }
        assertEquals(listOf(NoteKind.Basic, NoteKind.Cloze), cards.map { it.kind })
        val done = assertIs<CardEvent.Done>(events.last())
        assertNull(done.error)
        assertEquals(1, done.requests)
        assertEquals(120, done.usage.promptTokens)

        val request = body()
        assertEquals("json_schema", request["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("true", request["stream"]!!.jsonPrimitive.content)
        assertEquals("true", request["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content)
    }

    @Test
    fun withoutStructuredOutputTheFormatIsInThePrompt() = runTest {
        server.enqueue(completion("Here you go:\n```json\n$twoCards\n```"))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = false, streaming = false), prompt).toList()

        assertEquals(2, events.count { it is CardEvent.Card })
        val request = body()
        assertFalse("response_format" in request)
        assertFalse("stream" in request)
        val system = request["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue("Reply with only a JSON object" in system)
    }

    @Test
    fun rejectedSchemaFallsBackToThePrompt() = runTest {
        server.enqueue(error(400, "response_format json_schema is not supported by this model"))
        server.enqueue(sse(twoCards))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = true, streaming = true), prompt).toList()

        assertEquals(2, events.count { it is CardEvent.Card })
        assertEquals(2, assertIs<CardEvent.Done>(events.last()).requests)
        assertTrue("response_format" in body())
        assertFalse("response_format" in body())
    }

    @Test
    fun rejectedStreamingFallsBackToOneResponse() = runTest {
        server.enqueue(error(400, "stream is not supported"))
        server.enqueue(completion(twoCards))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = false, streaming = true), prompt).toList()

        assertEquals(2, events.count { it is CardEvent.Card })
        assertEquals("true", body()["stream"]!!.jsonPrimitive.content)
        assertFalse("stream" in body())
    }

    @Test
    fun unreadableReplyGetsOneRepairRequest() = runTest {
        server.enqueue(completion("I made some cards for you but forgot the format."))
        server.enqueue(completion(twoCards))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = false, streaming = false), prompt).toList()

        assertEquals(2, events.count { it is CardEvent.Card })
        val done = assertIs<CardEvent.Done>(events.last())
        assertEquals(2, done.requests)
        assertEquals(200, done.usage.promptTokens)

        body()
        val messages = body()["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it["role"]!!.jsonPrimitive.content })
        assertEquals(CardGenerationPrompt.REPAIR, messages.last()["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun onlyOneRepair() = runTest {
        server.enqueue(completion("No."))
        server.enqueue(completion("Still no."))
        val events = generator.generate(config, "m", AiCapabilities(jsonOutput = false, streaming = false), prompt).toList()
        assertEquals(listOf(CardEvent.Done::class), events.map { it::class })
        assertEquals(2, (events.single() as CardEvent.Done).requests)
    }

    @Test
    fun cardsBeforeAFailureAreKept() = runTest {
        val firstCard = """{"cards": [{"type": "basic", "front": "Q1", "back": "A1", "tags": []}, {"type": "basic", "front": "Q2", "ba"""
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/event-stream")
                .body("data: ${delta(firstCard)}\n\ndata: {\"error\": {\"message\": \"overloaded\"}}\n\n")
                .build(),
        )
        val events = generator.generate(config, "m", AiCapabilities(streaming = true), prompt).toList()

        assertEquals("Q1", assertIs<CardEvent.Card>(events.first()).card.front)
        val done = assertIs<CardEvent.Done>(events.last())
        assertIs<MnemoError.Http>(done.error)
        assertEquals(2, events.size)
    }

    @Test
    fun authErrorsAreNotRetried() = runTest {
        server.enqueue(error(401, "invalid api key"))
        val done = assertIs<CardEvent.Done>(generator.generate(config, "m", AiCapabilities(jsonOutput = true), prompt).toList().single())
        assertEquals(401, assertIs<MnemoError.Http>(done.error).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun explanationsStreamAsText() = runTest {
        server.enqueue(sse("Mitochondria are the **powerhouse** of the cell.", piece = 5))
        val assist = StudyAssistClient(runner)
        val prompt = StudyAssistPrompt(AssistRequest.Explain, NoteKind.Basic, listOf("What makes ATP?", "Mitochondria"))
        val events = assist.explain(config, "m", AiCapabilities(), prompt).toList()

        assertEquals("Mitochondria are the **powerhouse** of the cell.", events.filterIsInstance<TextEvent.Delta>().joinToString("") { it.text })
        assertNull(assertIs<TextEvent.End>(events.last()).error)
        val user = body()["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue("Front: What makes ATP?" in user && "Back: Mitochondria" in user)
    }

    @Test
    fun rewritesComeBackAsFields() = runTest {
        server.enqueue(completion("""```json
            {"front": "The {{C1: mitochondrion}} makes most of a cell's ATP.", "back": "Via oxidative phosphorylation"}
            ```"""))
        val assist = StudyAssistClient(runner)
        val prompt = StudyAssistPrompt(AssistRequest.Rewrite, NoteKind.Cloze, listOf("{{c1::Mitochondria}} make ATP.", ""))
        val result = assist.rewrite(config, "m", AiCapabilities(jsonOutput = true, streaming = false), prompt)

        assertEquals(
            listOf("The {{c1::mitochondrion}} makes most of a cell's ATP.", "Via oxidative phosphorylation"),
            assertIs<MnemoResult.Success<List<String>>>(result.fields).data,
        )
        val request = body()
        assertEquals("flashcard", request["response_format"]!!.jsonObject["json_schema"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        val user = (request["messages"]!!.jsonArray[1] as JsonObject)["content"] as JsonPrimitive
        assertTrue("numbered exactly as now (c1)" in user.content)
    }
}
