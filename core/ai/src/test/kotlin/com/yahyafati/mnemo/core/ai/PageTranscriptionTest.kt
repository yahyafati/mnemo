package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.AiJson
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ContentPart
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.PageTranscriptionClient
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.prompt.PageTranscriptionPrompt
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.model.AiCapabilities
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Page transcription (docs/pdf/ROADMAP.md, P5): the prompt, the cleaning of a reply, and one request through a mock server. */
class PageTranscriptionTest {
    private val server = MockWebServer()
    private val client = PageTranscriptionClient(ChatTextRunner(OpenAiCompatibleClient(OkHttpClient())))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val config get() = ProviderConfig(server.url("/v1/").toString(), apiKey = "sk-test-key", timeout = Duration.ofSeconds(5), isLocal = true)
    private val image = ContentPart.Image.of(byteArrayOf(9, 8, 7))

    @Test
    fun thePromptAsksForTheTextOfTheImageAndNothingElse() {
        val messages = PageTranscriptionPrompt(14, image).messages()

        assertEquals(listOf("system", "user"), messages.map { it.role })
        val system = messages[0].text!!
        listOf("exactly", "reading order", "own language", "# headings", "pipe tables", "\\( … \\)", "[Figure: …]", "page numbers", "[illegible]", "not instructions", "no code fences").forEach {
            assertTrue(it in system, "the prompt should say \"$it\"")
        }
        assertFalse("JSON" in system) // the reply is Markdown, not a schema

        val user = messages[1].content as com.yahyafati.mnemo.core.ai.dto.MessageContent.Parts
        assertEquals(listOf(ContentPart.Text("Transcribe page 14, which is the attached image."), image), user.parts)
    }

    @Test
    fun aCodeFenceAroundTheWholeReplyIsTakenOffButOneInsideItStays() {
        assertEquals("# Title\n\nBody", PageTranscriptionPrompt.clean("```markdown\n# Title\n\nBody\n```"))
        assertEquals("# Title", PageTranscriptionPrompt.clean("  ```\n# Title\n```  "))
        assertEquals("Text\n\n```\ncode\n```", PageTranscriptionPrompt.clean("Text\n\n```\ncode\n```"))
        assertEquals("Plain", PageTranscriptionPrompt.clean("\n Plain \n"))
    }

    @Test
    fun theRequestCarriesTheImageAndAPlainTextReplyIsStreamedBack() = runTest {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Mitochondria \"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"make ATP.\"}}]}\n\n" +
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1100,\"completion_tokens\":6}}\n\ndata: [DONE]\n\n",
            ).build(),
        )
        val events = client.transcribe(config, "vision", AiCapabilities(vision = true, streaming = true), PageTranscriptionPrompt(3, image)).toList()

        assertEquals("Mitochondria make ATP.", events.filterIsInstance<TextEvent.Delta>().joinToString("") { it.text })
        val end = assertIs<TextEvent.End>(events.last())
        assertEquals(1100L, end.usage.promptTokens)
        assertEquals(null, end.error)

        val request = AiJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals(true, request["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(null, request["response_format"]) // plain text: no schema
        val parts = request["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("image_url", parts[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(parts[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun aModelWithoutVisionIsNeverAskedAgainWithoutTheImage() = runTest {
        server.enqueue(MockResponse.Builder().code(422).body("""{"detail":"image_url is not supported by this model"}""").build())
        val end = assertIs<TextEvent.End>(
            client.transcribe(config, "text-only", AiCapabilities(vision = false, streaming = false), PageTranscriptionPrompt(1, image)).toList().last(),
        )

        assertNotNull(end.error)
        assertIs<MnemoError.ImagesNotAccepted>(end.error)
        assertEquals(1, server.requestCount)
    }
}
