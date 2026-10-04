package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.PageTranscriptionClient
import com.yahyafati.mnemo.core.ai.probe.ConnectionProbe
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.ingest.EpubReader
import com.yahyafati.mnemo.core.ingest.PdfPageRenderer
import com.yahyafati.mnemo.core.ingest.PdfRenderException
import com.yahyafati.mnemo.core.ingest.PdfTextExtractor
import com.yahyafati.mnemo.core.ingest.RenderedPage
import com.yahyafati.mnemo.core.ingest.SpeechTranscriber
import com.yahyafati.mnemo.core.ingest.WebPageExtractor
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.DictationEvent
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfInfoResult
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.inMemoryDatabase
import com.yahyafati.mnemo.core.testing.platform.FakeDocumentAccess
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
import java.io.File
import java.io.InputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reading a PDF page with a vision model (docs/pdf/ROADMAP.md, P5): the page image in the request, the reply as text, the usage log. */
class PdfReadRepositoryTest : PlatformTest() {
    private val directories = TestAppDirectories()
    private val db = inMemoryDatabase()
    private val documents = FakeDocumentAccess()
    private val server = MockWebServer()
    private val client = OpenAiCompatibleClient(OkHttpClient())
    private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)
    private val providers = DefaultAiProviderRepository(
        db.aiProviderDao(), secrets, ConnectionProbe(client), RoomTransactionRunner(db), TestClock(), Dispatchers.Unconfined,
    )

    private val renderer = object : PdfPageRenderer {
        var blank = emptySet<Int>()
        var failure: SourceProblem? = null

        override fun render(file: File, page: Int, longEdge: Int): RenderedPage {
            failure?.let { throw PdfRenderException(it) }
            return RenderedPage("JPEG of page $page at $longEdge".toByteArray(), "image/jpeg", 10, 10, blank = page in blank)
        }
    }

    private val pdf = object : PdfTextExtractor {
        override fun inspect(input: InputStream, fileName: String?) = PdfInfoResult.Success(PdfInfo(5, fileName))

        override fun pageTexts(input: InputStream, pages: List<Int>) = PdfPageTextsResult.Success(emptyMap())

        override fun extract(input: InputStream, pages: List<Int>?, fileName: String?): SourceResult = SourceResult.Failure(SourceProblem.NoText)
    }

    private val sources = DefaultSourceRepository(
        documents = documents,
        pdf = pdf,
        pageRenderer = renderer,
        web = WebPageExtractor(OkHttpClient(), pdf),
        epub = EpubReader({ createTempDirectory("pdf-read-cache").toFile() }),
        speech = object : SpeechTranscriber {
            override fun isAvailable() = false

            override fun transcribe(languageTag: String?): Flow<DictationEvent> = emptyFlow()
        },
        directories = directories,
        clock = TestClock(),
        ioDispatcher = Dispatchers.Unconfined,
    )

    private val reading = DefaultPdfReadRepository(sources, PageTranscriptionClient(ChatTextRunner(client)), ProviderConfigs(secrets), providers, Dispatchers.Unconfined)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        server.close()
        db.close()
        directories.delete()
    }

    private suspend fun route(streaming: Boolean = false): AiRoute {
        providers.saveProvider(
            AiProviderDraft(
                id = "p",
                name = "Local",
                baseUrl = server.url("/v1/").toString(),
                apiKey = ApiKeyChange.Set("sk-test-key"),
                defaultModel = "vision-model",
                isLocal = true,
                disclosureAccepted = true,
            ),
        )
        return AiRoute(AiTask.ReadPages, providers.getProvider("p")!!, "vision-model", AiCapabilities(vision = true, streaming = streaming), usesDefault = false)
    }

    private suspend fun handle(): PdfHandle {
        documents.put("/docs/scan.pdf", "%PDF scan")
        return assertIs<PdfOpenResult.Success>(sources.openPdf("/docs/scan.pdf")).handle
    }

    private fun reply(text: String, promptTokens: Int = 1200, completionTokens: Int = 40) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(text)}}}],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens}}""")
        .build()

    @Test
    fun theRenderedPageGoesOutAsAnImageAndTheReplyComesBackAsTidyText() = runTest {
        server.enqueue(reply("```markdown\n# Cells\n\n\n\nMitochondria make ATP.\n```"))
        val result = reading.transcribe(route(), handle(), page = 3, quality = PdfQuality.High)

        assertEquals("# Cells\n\nMitochondria make ATP.", assertIs<PageTranscription.Success>(result).text)

        val request = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertEquals("vision-model", request["model"]!!.jsonPrimitive.content)
        val user = request["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertTrue("page 3" in user[0].jsonObject["text"]!!.jsonPrimitive.content)
        val url = user[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content
        assertTrue(url.startsWith("data:image/jpeg;base64,"))
        // The page was drawn at High's long edge.
        val bytes = kotlin.io.encoding.Base64.Default.decode(url.removePrefix("data:image/jpeg;base64,"))
        assertEquals("JPEG of page 3 at ${PdfQuality.High.longEdge}", bytes.decodeToString())
    }

    @Test
    fun theTokensAreLoggedUnderTheReadPagesTask() = runTest {
        server.enqueue(reply("Text.", promptTokens = 1500, completionTokens = 12))
        reading.transcribe(route(), handle(), 1, PdfQuality.Standard)

        val usage = providers.observeUsage().first().single { it.task == AiTask.ReadPages }
        assertEquals(1, usage.requests)
        assertEquals(1500L, usage.promptTokens)
        assertEquals(12L, usage.completionTokens)
    }

    @Test
    fun aModelThatTakesNoImagesIsSaidSoAndStillBilledForWhatItRead() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"message":"This model does not support image input"}}""").build())
        val failure = assertIs<PageTranscription.Failure>(reading.transcribe(route(), handle(), 1, PdfQuality.Standard)).failure

        assertEquals(AiProblem.ImagesNotAccepted, assertIs<PageReadFailure.Ai>(failure).failure.problem)
        assertEquals(1, server.requestCount) // never asked again without the image
    }

    @Test
    fun aPageThatRendersBlankSendsNothing() = runTest {
        renderer.blank = setOf(2)
        val failure = assertIs<PageTranscription.Failure>(reading.transcribe(route(), handle(), 2, PdfQuality.Standard)).failure

        assertEquals(SourceProblem.BlankPage, assertIs<PageReadFailure.Page>(failure).problem)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aReplyWithNoTextIsAFailureNotAnEmptyPage() = runTest {
        server.enqueue(reply("   "))
        val failure = assertIs<PageTranscription.Failure>(reading.transcribe(route(), handle(), 1, PdfQuality.Standard)).failure

        assertEquals(AiProblem.InvalidResponse, assertIs<PageReadFailure.Ai>(failure).failure.problem)
    }

    @Test
    fun aReplyThatStreamsIsJoined() = runTest {
        server.enqueue(
            MockResponse.Builder().addHeader("Content-Type", "text/event-stream").body(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Mito\"}}]}\n\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\"chondria\"}}]}\n\n" +
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":800,\"completion_tokens\":2}}\n\ndata: [DONE]\n\n",
            ).build(),
        )
        assertEquals("Mitochondria", assertIs<PageTranscription.Success>(reading.transcribe(route(streaming = true), handle(), 1, PdfQuality.Standard)).text)
    }
}
