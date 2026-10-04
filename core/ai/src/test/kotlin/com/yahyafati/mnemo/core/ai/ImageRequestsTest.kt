package com.yahyafati.mnemo.core.ai

import com.yahyafati.mnemo.core.ai.client.AiJson
import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.client.ProviderConfig
import com.yahyafati.mnemo.core.ai.dto.ChatMessage
import com.yahyafati.mnemo.core.ai.dto.ChatRequest
import com.yahyafati.mnemo.core.ai.dto.ChatResponse
import com.yahyafati.mnemo.core.ai.dto.ContentPart
import com.yahyafati.mnemo.core.ai.dto.MessageContent
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.RequestMode
import com.yahyafati.mnemo.core.ai.generate.TextEvent
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.ai.probe.ImageProbeOutcome
import com.yahyafati.mnemo.core.ai.schema.GeneratedCardsSchema
import com.yahyafati.mnemo.core.common.result.MnemoError
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Images in chat requests (ADR 0014, P4): the message shape, the runner's handling of a refusal, and "Check images". */
class ImageRequestsTest {
    private val server = MockWebServer()
    private val client = OpenAiCompatibleClient(OkHttpClient())
    private val runner = ChatTextRunner(client)
    private val probe = ConnectionProbe(client)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val config get() = ProviderConfig(server.url("/v1/").toString(), apiKey = "sk-test-key", timeout = Duration.ofSeconds(5), isLocal = true)

    private val pageOne = ContentPart.Image.of(byteArrayOf(1, 2, 3))
    private val pageTwo = ContentPart.Image.of(byteArrayOf(4, 5, 6, 7))
    private val withImages = listOf(ChatMessage.system("Transcribe."), ChatMessage.user("Pages 1 and 2:", listOf(pageOne, pageTwo)))

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    private fun reply(text: String) = json("""{"choices":[{"message":{"role":"assistant","content":"$text"}}],"usage":{"prompt_tokens":900,"completion_tokens":7}}""")

    private fun sse(vararg pieces: String) = MockResponse.Builder()
        .addHeader("Content-Type", "text/event-stream")
        .body(
            pieces.joinToString("") { "data: {\"choices\":[{\"delta\":{\"content\":\"$it\"}}]}\n\n" } +
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":900,\"completion_tokens\":7}}\n\ndata: [DONE]\n\n",
        )
        .build()

    private fun body(): JsonObject = AiJson.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject

    private fun userContent(request: JsonObject): JsonArray = request["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray

    @Test
    fun partsAreWrittenInTheOpenAiShapeAndInOrder() {
        val encoded = AiJson.encodeToString(ChatRequest("m", withImages))
        val content = AiJson.parseToJsonElement(encoded).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(3, content.size)
        assertEquals("""{"type":"text","text":"Pages 1 and 2:"}""", content[0].toString())
        assertEquals("""{"type":"image_url","image_url":{"url":"data:image/jpeg;base64,AQID"}}""", content[1].toString())
        assertEquals("data:image/jpeg;base64,BAUGBw==", content[2].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        // The system message beside it is still a plain string.
        assertEquals(JsonPrimitive("Transcribe."), AiJson.parseToJsonElement(encoded).jsonObject["messages"]!!.jsonArray[0].jsonObject["content"])
    }

    @Test
    fun noImagesIsThePlainTextMessage() {
        assertEquals(ChatMessage.user("Hi"), ChatMessage.user("Hi", emptyList()))
        assertEquals("""{"role":"user","content":"Hi"}""", AiJson.encodeToString(ChatMessage.user("Hi", emptyList())))
    }

    @Test
    fun repliesAsAStringOrAsPartsAreBothRead() {
        val plain = AiJson.decodeFromString<ChatResponse>("""{"choices":[{"message":{"role":"assistant","content":"OK"}}]}""")
        assertEquals("OK", plain.text)
        assertIs<MessageContent.Text>(plain.choices.single().message!!.content)
        val parts = AiJson.decodeFromString<ChatResponse>("""{"choices":[{"message":{"role":"assistant","content":[{"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}""")
        assertEquals("a\nb", parts.text)
        val none = AiJson.decodeFromString<ChatResponse>("""{"choices":[{"message":{"role":"assistant","content":null}}]}""")
        assertNull(none.text)
    }

    @Test
    fun twoImagesGoOutInOneRequestAndTheReplyIsRead() = runTest {
        server.enqueue(reply("Mitochondria"))
        val events = runner.run(config, "vision-model", withImages, RequestMode(streaming = false, format = null)).toList()

        assertEquals("Mitochondria", events.filterIsInstance<TextEvent.Delta>().joinToString("") { it.text })
        val end = events.last() as TextEvent.End
        assertNull(end.error)
        assertEquals(900, end.usage.promptTokens)
        val content = userContent(body())
        assertEquals(listOf("text", "image_url", "image_url"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    }

    @Test
    fun streamingWorksWithImages() = runTest {
        server.enqueue(sse("Mito", "chondria"))
        val events = runner.run(config, "vision-model", withImages, RequestMode(streaming = true, format = null)).toList()

        assertEquals("Mitochondria", events.filterIsInstance<TextEvent.Delta>().joinToString("") { it.text })
        assertNull((events.last() as TextEvent.End).error)
        val request = body()
        assertEquals("true", request["stream"]!!.jsonPrimitive.content)
        assertEquals(3, userContent(request).size)
    }

    @Test
    fun aSchemaIsStillDroppedAndTheImagesStayOnTheRetry() = runTest {
        server.enqueue(json("""{"error":{"message":"response_format json_schema is not supported"}}""", code = 400))
        server.enqueue(reply("ok"))
        val mode = RequestMode(streaming = false, format = GeneratedCardsSchema.cardsFormat)
        val end = runner.run(config, "m", withImages, mode).toList().last() as TextEvent.End

        assertNull(end.error)
        assertEquals(2, end.requests)
        server.takeRequest()
        assertEquals(3, userContent(body()).size)
    }

    @Test
    fun aRefusalOfTheImagesIsReportedAndNotRetriedWithoutThem() = runTest {
        for ((code, message) in listOf(
            400 to """{"error":{"message":"This model does not support image input"}}""",
            422 to """{"detail":[{"msg":"Input should be a valid string","loc":["body","messages",1,"content"]}]}""",
            415 to """{"error":"unsupported media: images need a vision model"}""",
        )) {
            server.enqueue(json(message, code))
            val end = runner.run(config, "text-model", withImages, RequestMode(streaming = false, format = GeneratedCardsSchema.cardsFormat)).toList().last() as TextEvent.End

            val error = assertIs<MnemoError.ImagesNotAccepted>(end.error)
            assertTrue(error.detail!!.startsWith("HTTP $code"))
            assertEquals(1, end.requests, "one request, never asked again without the images")
            body()
        }
    }

    @Test
    fun otherFailuresOfARequestWithImagesStayWhatTheyAre() = runTest {
        for (response in listOf(
            json("""{"error":{"message":"Incorrect API key (image or not)"}}""", code = 401),
            json("""{"error":{"message":"Rate limit for vision models"}}""", code = 429),
            json("""{"error":{"message":"something else"}}""", code = 400),
            json("""{"error":{"message":"image service down"}}""", code = 503),
        )) {
            server.enqueue(response)
            val end = runner.run(config, "m", withImages, RequestMode(streaming = false, format = null)).toList().last() as TextEvent.End
            assertIs<MnemoError.Http>(end.error)
            body()
        }
    }

    @Test
    fun aTextOnlyRequestNeverBecomesAnImageError() = runTest {
        server.enqueue(json("""{"error":{"message":"image input is not supported"}}""", code = 400))
        val end = runner.run(config, "m", listOf(ChatMessage.user("Hi")), RequestMode(streaming = false, format = null)).toList().last() as TextEvent.End
        assertIs<MnemoError.Http>(end.error)
    }

    // --- Check images ---

    @Test
    fun theProbeImageIsA96By64Png() {
        val bytes = ConnectionProbe::class.java.getResourceAsStream("/probe/number.png")!!.use { it.readBytes() }
        val image = assertNotNull(ImageIO.read(bytes.inputStream()))
        assertEquals(96, image.width)
        assertEquals(64, image.height)
        val dark = (0 until image.width).sumOf { x -> (0 until image.height).count { y -> (image.getRGB(x, y) and 0xFF) < 128 } }
        assertTrue(dark in 500..2500, "a few digits on white, got $dark dark pixels")
    }

    @Test
    fun theImageCheckSendsThePictureAndAcceptsTheNumber() = runTest {
        server.enqueue(reply("52"))
        val result = probe.checkImages(config, "llava")

        assertEquals(ImageProbeOutcome.Reads, result.outcome)
        assertEquals(1, result.requests)
        assertEquals(900, result.usage.promptTokens)
        val content = body()["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("Reply with only the number in the image.", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content.startsWith("data:image/png;base64,iVBORw0KGgo"))
    }

    @Test
    fun aSentenceWithTheNumberCounts() = runTest {
        server.enqueue(reply("The number is 52."))
        assertEquals(ImageProbeOutcome.Reads, probe.checkImages(config, "m").outcome)
    }

    @Test
    fun aWrongAnswerMeansTheModelDoesNotSee() = runTest {
        server.enqueue(reply("I can't see any image."))
        val outcome = probe.checkImages(config, "m").outcome
        assertEquals("I can't see any image.", assertIs<ImageProbeOutcome.Misread>(outcome).answer)
    }

    @Test
    fun aRefusedImageMeansTheModelDoesNotSee() = runTest {
        server.enqueue(json("""{"error":{"message":"image_url is only supported by multimodal models"}}""", code = 400))
        assertIs<ImageProbeOutcome.Refused>(probe.checkImages(config, "m").outcome)
    }

    @Test
    fun anEmptyAnswerOrAKeyErrorProvesNothing() = runTest {
        server.enqueue(reply(""))
        assertIs<ImageProbeOutcome.Failed>(probe.checkImages(config, "m").outcome)
        server.enqueue(json("""{"error":{"message":"bad key"}}""", code = 401))
        assertIs<ImageProbeOutcome.Failed>(probe.checkImages(config, "m").outcome)
    }

    @Test
    fun aModelThatWantsMaxCompletionTokensIsAskedAgainWithIt() = runTest {
        server.enqueue(json("""{"error":{"message":"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead."}}""", code = 400))
        server.enqueue(reply("52"))
        val result = probe.checkImages(config, "o-model")

        assertEquals(ImageProbeOutcome.Reads, result.outcome)
        assertEquals(2, result.requests)
        assertTrue(""""max_tokens"""" in server.takeRequest().body!!.utf8())
        assertTrue(""""max_completion_tokens"""" in server.takeRequest().body!!.utf8())
    }
}
