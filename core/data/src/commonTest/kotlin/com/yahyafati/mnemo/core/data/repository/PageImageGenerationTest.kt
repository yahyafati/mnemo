package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
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

/** Cards from page images through the real client, against MockWebServer (docs/pdf/ROADMAP.md, P6). */
class PageImageGenerationTest : PlatformTest() {
    private val directories = TestAppDirectories()
    private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)
    private val providers = FakeAiProviderRepository()
    private val sources = FakeSourceRepository()
    private val server = MockWebServer()
    private val generation = DefaultCardGenerationRepository(
        CardGenerationClient(ChatTextRunner(OpenAiCompatibleClient(OkHttpClient()))), ProviderConfigs(secrets), providers, sources, Dispatchers.Unconfined,
    )
    private val handle = PdfHandle("pdf-1", PdfInfo(40, "Cell biology"))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun route() = AiRoute(
        task = AiTask.ReadPages,
        provider = AiProvider(id = "local", name = "LM Studio", baseUrl = server.url("/v1").toString(), defaultModel = "vision", isLocal = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        modelId = "vision",
        capabilities = AiCapabilities(jsonOutput = false, streaming = false, vision = true),
        usesDefault = true,
    )

    private fun completion(content: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body("""{"choices":[{"message":{"role":"assistant","content":${JsonPrimitive(content)}}}],"usage":{"prompt_tokens":2500,"completion_tokens":80}}""")
        .build()

    private fun pageFile(page: Int): File = File(directories.cache, "p$page.jpg").apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(page.toByte(), 9, 9))
    }

    private fun request(vararg pages: Int, text: String = "", replacing: com.yahyafati.mnemo.core.model.GeneratedCard? = null) =
        GenerationRequest(text, ExtractOptions(), part = 1, parts = 3, pages = PageImages(handle, PdfQuality.Standard, pages.toList()), replacing = replacing)

    @Test
    fun theRequestHasTheRenderedPagesInOrderAndTheirTextLayer() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> PdfPageResult.Success(pageFile(page)) }
        server.enqueue(completion("""{"cards":[{"type":"basic","front":"Q","back":"A","tags":[],"page":15}]}"""))

        val updates = generation.generate(route(), request(14, 15, 16, text = "Mitochondria make ATP.")).toList()

        assertEquals(listOf(14, 15, 16), sources.renderedPages.map { it.second })
        assertEquals(PdfQuality.Standard, sources.renderedPages.first().third)
        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val content = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(listOf("text", "image_url", "image_url", "image_url"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        val text = content[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("pages 14, 15 and 16" in text && "Mitochondria make ATP." in text, text)
        assertEquals(15, updates.filterIsInstance<GenerationUpdate.Card>().single().card.page)
        assertEquals(GenerationUpdate.Done(), updates.last())
        // Tokens are logged under the route's task: the read-pages route sends the images.
        assertEquals(AiTask.ReadPages, providers.usage.value.single().task)
        assertEquals(2500, providers.usage.value.single().promptTokens)
    }

    @Test
    fun aPageTheModelMadeUpIsNoPage() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> PdfPageResult.Success(pageFile(page)) }
        server.enqueue(completion("""{"cards":[{"type":"basic","front":"Q1","back":"A","tags":[],"page":99},{"type":"basic","front":"Q2","back":"A","tags":[],"page":0},{"type":"basic","front":"Q3","back":"A","tags":[],"page":16}]}"""))

        val cards = generation.generate(route(), request(14, 15, 16)).toList().filterIsInstance<GenerationUpdate.Card>().map { it.card }

        assertEquals(listOf(null, null, 16), cards.map { it.page })
    }

    @Test
    fun aBlankPageIsLeftOutOfTheRequest() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> if (page == 15) PdfPageResult.Failure(SourceProblem.BlankPage) else PdfPageResult.Success(pageFile(page)) }
        server.enqueue(completion("""{"cards":[]}"""))

        generation.generate(route(), request(14, 15, 16)).toList()

        val body = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val content = body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals(3, content.size)
        assertTrue("pages 14 and 16" in content[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun withEveryPageBlankNothingIsSent() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, _ -> PdfPageResult.Failure(SourceProblem.BlankPage) }

        val updates = generation.generate(route(), request(14, 15)).toList()

        assertEquals(listOf(GenerationUpdate.Done()), updates)
        assertEquals(0, server.requestCount)
        assertTrue(providers.usage.value.isEmpty())
    }

    @Test
    fun aPageThatCannotBeDrawnStopsTheRequestAndSaysWhich() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> if (page == 15) PdfPageResult.Failure(SourceProblem.Unsupported) else PdfPageResult.Success(pageFile(page)) }

        val updates = generation.generate(route(), request(14, 15, 16)).toList()

        assertEquals(listOf(GenerationUpdate.Done(unreadablePage = UnreadablePage(15, SourceProblem.Unsupported))), updates)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun aVanishedPageFileIsUnavailable() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> PdfPageResult.Success(File(directories.cache, "gone-$page.jpg")) }

        val done = generation.generate(route(), request(14)).toList().single() as GenerationUpdate.Done

        assertEquals(UnreadablePage(14, SourceProblem.FileUnavailable), done.unreadablePage)
    }

    @Test
    fun regeneratingAskForOneCardWithTheSameImages() = runTest {
        secrets.put("local", "secret-key-123")
        sources.pdfPage = { _, page -> PdfPageResult.Success(pageFile(page)) }
        server.enqueue(completion("""{"cards":[{"type":"basic","front":"Better","back":"A","tags":[],"page":14}]}"""))
        val old = com.yahyafati.mnemo.core.model.GeneratedCard("old", com.yahyafati.mnemo.core.model.NoteKind.Basic, "Worse", "A", page = 14)

        val cards = generation.generate(route(), request(14, replacing = old)).toList().filterIsInstance<GenerationUpdate.Card>()

        assertEquals(14, cards.single().card.page)
        val text = Json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray[0]
            .jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("Write exactly 1 better card" in text && "Worse" in text, text)
    }
}
