package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.ai.client.OpenAiCompatibleClient
import com.yahyafati.mnemo.core.ai.generate.CardGenerationClient
import com.yahyafati.mnemo.core.ai.generate.ChatTextRunner
import com.yahyafati.mnemo.core.ai.generate.CoAuthorClient
import com.yahyafati.mnemo.core.ai.generate.StudyAssistClient
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.AssistUpdate
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.ChatTurn
import com.yahyafati.mnemo.core.model.CoAuthorDeck
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.RewriteOutcome
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.security.FileSecretStore
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestAppDirectories
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.security.SoftwareSecretCipher
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Smart Extract and study-time AI through the real client, against MockWebServer. */
class AiGenerationRepositoriesTest : PlatformTest() {
    private val directories = TestAppDirectories()
    private val secrets = FileSecretStore(directories, SoftwareSecretCipher(), Dispatchers.Unconfined)
    private val providers = FakeAiProviderRepository()
    private val server = MockWebServer()
    private val runner = ChatTextRunner(OpenAiCompatibleClient(OkHttpClient()))
    private val generation = DefaultCardGenerationRepository(CardGenerationClient(runner), ProviderConfigs(secrets), providers, Dispatchers.Unconfined)
    private val assist = DefaultStudyAssistRepository(StudyAssistClient(runner), ProviderConfigs(secrets), providers, Dispatchers.Unconfined)
    private val coAuthor = DefaultCoAuthorRepository(
        CoAuthorClient(runner), CardGenerationClient(runner), StudyAssistClient(runner), ProviderConfigs(secrets), providers, Dispatchers.Unconfined,
    )

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun route(task: AiTask = AiTask.Extract) = AiRoute(
        task = task,
        provider = AiProvider(
            id = "local", name = "Ollama", baseUrl = server.url("/v1").toString(), defaultModel = "llama3.2",
            isLocal = true, headers = mapOf("X-Test" to "1"), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
        modelId = "llama3.2",
        capabilities = AiCapabilities(jsonOutput = false, streaming = false),
        usesDefault = true,
    )

    private fun completion(content: String) = MockResponse.Builder()
        .addHeader("Content-Type", "application/json")
        .body(
            """{"choices":[{"message":{"role":"assistant","content":${kotlinx.serialization.json.JsonPrimitive(content)}}}],""" +
                """"usage":{"prompt_tokens":300,"completion_tokens":90}}""",
        )
        .build()

    @Test
    fun cardsCarryTheirPartAndUsageIsLogged() = runTest {
        secrets.put("local", "secret-key-123")
        server.enqueue(completion("""{"cards":[{"type":"basic","front":"Q","back":"A","tags":["t"]},{"type":"cloze","front":"{{c1::X}} y","back":"","tags":[]}]}"""))
        val updates = generation.generate(route(), GenerationRequest("Source text", ExtractOptions(), part = 2, parts = 3, title = "Notes")).toList()

        val cards = updates.filterIsInstance<GenerationUpdate.Card>().map { it.card }
        assertEquals(listOf(NoteKind.Basic, NoteKind.Cloze), cards.map { it.kind })
        assertTrue(cards.all { it.chunkIndex == 2 } && cards.map { it.id }.distinct().size == 2)
        assertEquals(GenerationUpdate.Done(), updates.last())

        val request = server.takeRequest()
        assertEquals("Bearer secret-key-123", request.headers["Authorization"])
        assertEquals("1", request.headers["X-Test"])
        assertTrue("Source \\\"Notes\\\" (part 3 of 3)" in request.body!!.utf8())

        val usage = providers.usage.value.single()
        assertEquals(AiTask.Extract, usage.task)
        assertEquals(300, usage.promptTokens)
        assertEquals(90, usage.completionTokens)
    }

    @Test
    fun unreadableKeyFailsWithoutARequest() = runTest {
        secrets.put("local", "secret-key-123")
        // Corrupt the stored ciphertext, as after a restore onto another device.
        directories.secrets.listFiles()!!.forEach { it.writeBytes(byteArrayOf(1, 2, 3)) }

        val updates = generation.generate(route(), GenerationRequest("Source", ExtractOptions())).toList()
        assertEquals(listOf(GenerationUpdate.Done(AiFailure(AiProblem.KeyUnavailable))), updates)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun explainAndRewrite() = runTest {
        val now = Instant.EPOCH
        val note = Note("n", "d", NoteType.Basic.id, listOf("What makes ATP?", "Mitochondria"), createdAt = now, updatedAt = now)
        val card = StudyCard(Card("c", "n", "d", 0, due = now, createdAt = now, updatedAt = now), note, NoteKind.Basic, "Biology")

        server.enqueue(completion("Because they run **oxidative phosphorylation**."))
        val explained = assist.explain(route(AiTask.Explain), StudyAssist.Explain, card).toList()
        assertEquals("Because they run **oxidative phosphorylation**.", assertIs<AssistUpdate.Text>(explained.first()).delta)
        assertEquals(AssistUpdate.Done(), explained.last())
        assertTrue("Deck: Biology" in server.takeRequest().body!!.utf8())

        server.enqueue(completion("""{"front": "Which organelle makes most ATP?", "back": "The mitochondrion"}"""))
        assertEquals(
            RewriteOutcome.Proposed(listOf("Which organelle makes most ATP?", "The mitochondrion")),
            assist.rewrite(route(AiTask.Rewrite), card),
        )
        assertEquals(setOf(AiTask.Explain, AiTask.Rewrite), providers.usage.first().map { it.task }.toSet())
    }

    @Test
    fun coAuthorChatsSuggestsAndImproves() = runTest {
        val now = Instant.EPOCH
        val notes = (1..200).map { i ->
            Note("n$i", "d", NoteType.Basic.id, listOf("Question **$i**", "Answer $i"), createdAt = now, updatedAt = now)
        }
        val deck = CoAuthorDeck("Biology", notes)

        server.enqueue(completion("Add cards on **enzymes**."))
        val reply = coAuthor.chat(route(AiTask.CoAuthor), deck, listOf(ChatTurn(true, "What's missing?"))).toList()
        assertEquals("Add cards on **enzymes**.", assertIs<AssistUpdate.Text>(reply.first()).delta)
        val chatBody = server.takeRequest().body!!.utf8()
        // A sample of the deck, as plain text, and the user's message.
        assertTrue("(150 of its 200 cards shown)" in chatBody)
        assertTrue("[basic] Question 1 | Answer 1" in chatBody)
        assertTrue("What's missing?" in chatBody)

        server.enqueue(completion("""{"cards":[{"type":"basic","front":"What do enzymes lower?","back":"Activation energy","options":[],"tags":[]}]}"""))
        val suggested = coAuthor.suggest(route(AiTask.CoAuthor), deck, focus = "enzymes").toList()
        assertEquals("What do enzymes lower?", assertIs<GenerationUpdate.Card>(suggested.first()).card.front)
        assertTrue("Focus on: enzymes." in server.takeRequest().body!!.utf8())

        val weak = StudyCard(
            Card("c", "n1", "d", 0, due = now, reps = 20, lapses = 9, createdAt = now, updatedAt = now), notes.first(), NoteKind.Basic, "Biology",
        )
        server.enqueue(completion("""{"front": "Q1 clearer", "back": "A1"}"""))
        assertEquals(RewriteOutcome.Proposed(listOf("Q1 clearer", "A1")), coAuthor.improve(route(AiTask.CoAuthor), weak))
        assertTrue("forgotten this card 9 times in 20 reviews" in server.takeRequest().body!!.utf8())
        assertEquals(setOf(AiTask.CoAuthor), providers.usage.first().map { it.task }.toSet())
    }
}
