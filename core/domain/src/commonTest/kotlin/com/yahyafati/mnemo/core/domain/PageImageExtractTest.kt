package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.PageImages
import com.yahyafati.mnemo.core.data.repository.UnreadablePage
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.ExtractOptions
import com.yahyafati.mnemo.core.model.PageImageBatches
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Cards from page images in the use cases (docs/pdf/ROADMAP.md, P6): one request per group of pages, resume, regenerate. */
class PageImageExtractTest {
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val generate = GenerateCardsUseCase(generation, cards)
    private val regenerate = RegenerateCardUseCase(generation, cards)
    private val handle = PdfHandle("pdf-1", PdfInfo(30, "Slides"))
    private val route = AiRoute(
        task = AiTask.ReadPages,
        provider = AiProvider(id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "m", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        modelId = "m",
        capabilities = AiCapabilities(vision = true),
        usesDefault = true,
    )

    /** Pages 2-4, 6-8 and 10, three to a request; the text layer of each group is its part. */
    private val batches = PageImageBatches.of(listOf(2, 3, 4, 6, 7, 8, 10))
    private val layers = listOf("layer 2-4", "", "layer 10")

    private fun request(fromPart: Int = 0, known: List<com.yahyafati.mnemo.core.model.GeneratedCard> = emptyList()) = ExtractRequest(
        parts = layers, options = ExtractOptions(), deckId = "deck", fromPart = fromPart, known = known,
        pages = PageBatches(handle, PdfQuality.High, batches),
    )

    @Test
    fun everyGroupOfPagesIsOneRequestInOrderWithItsImagesAndTextLayer() = runTest {
        generation.answer(card("Q", "A").copy(page = 3))

        val events = generate(route, request().copy(deckId = null)).toList()

        assertEquals(listOf(listOf(2, 3, 4), listOf(6, 7, 8), listOf(10)), generation.requests.map { it.pages!!.pages })
        assertEquals(listOf("layer 2-4", "", "layer 10"), generation.requests.map { it.text })
        assertEquals(listOf(0, 1, 2), generation.requests.map { it.part })
        assertEquals(setOf(handle to PdfQuality.High), generation.requests.map { it.pages!!.handle to it.pages!!.quality }.toSet())
        assertEquals(3, generation.requests.first().parts)
        // Each reply is the same card, so only the first is new; the page it named is kept.
        val queued = events.filterIsInstance<ExtractEvent.Card>().map { it.card }
        assertEquals(1, queued.size)
        assertEquals(3, queued.single().page)
        assertEquals(ExtractEvent.Finished, events.last())
    }

    @Test
    fun aTextRunSendsNoPages() = runTest {
        generate(route, ExtractRequest(listOf("One part"), ExtractOptions(), deckId = null)).toList()

        assertEquals(listOf(null), generation.requests.map { it.pages })
    }

    @Test
    fun resumingStartsAtTheGroupThatFailed() = runTest {
        generation.respond = { request ->
            if (request.part == 1) flowOf(GenerationUpdate.Done(AiFailure(AiProblem.RateLimited))) else flowOf(GenerationUpdate.Card(card("Q${request.part}", "A")), GenerationUpdate.Done())
        }

        val first = generate(route, request()).toList()
        assertEquals(ExtractEvent.Failed(1, AiFailure(AiProblem.RateLimited)), first.last())

        generation.requests.clear()
        generation.respond = { request -> flowOf(GenerationUpdate.Card(card("Q${request.part}", "A")), GenerationUpdate.Done()) }
        generate(route, request(fromPart = 1)).toList()
        assertEquals(listOf(listOf(6, 7, 8), listOf(10)), generation.requests.map { it.pages!!.pages })
    }

    @Test
    fun aPageThatCannotBeDrawnStopsTheRunAtItsGroup() = runTest {
        generation.respond = { request ->
            if (request.part == 1) flowOf(GenerationUpdate.Done(unreadablePage = UnreadablePage(7, SourceProblem.Unsupported))) else flowOf(GenerationUpdate.Done())
        }

        val events = generate(route, request()).toList()

        assertEquals(ExtractEvent.PageUnreadable(1, 7, SourceProblem.Unsupported), events.last())
        assertEquals(listOf(0, 1), generation.requests.map { it.part })
    }

    @Test
    fun everyPartNeedsItsGroupOfPages() {
        assertFailsWith<IllegalArgumentException> {
            ExtractRequest(listOf("only one"), ExtractOptions(), deckId = null, pages = PageBatches(handle, PdfQuality.Standard, batches))
        }
    }

    @Test
    fun regeneratingSendsThePagesOfTheCardsGroupAgain() = runTest {
        val old = card("What is shown?", "A cell").copy(id = "old", chunkIndex = 1, page = 7)
        generation.answer(card("What does the arrow show?", "Transport").copy(page = 7))
        val pages = PageImages(handle, PdfQuality.High, listOf(6, 7, 8))

        val result = regenerate(route, old, "", ExtractOptions(), deckId = null, pages = pages)

        assertEquals(7, assertIs<RegenerateResult.Replaced>(result).card.page)
        val sent = generation.requests.single()
        assertEquals(pages, sent.pages)
        assertEquals(old, sent.replacing)
        assertEquals(1, sent.part)
    }

    @Test
    fun regeneratingTellsWhenAPageCannotBeDrawn() = runTest {
        val old = card("What is shown?", "A cell").copy(id = "old", page = 7)
        generation.respond = { flowOf(GenerationUpdate.Done(unreadablePage = UnreadablePage(7, SourceProblem.FileUnavailable))) }

        val result = regenerate(route, old, "", ExtractOptions(), deckId = null, pages = PageImages(handle, PdfQuality.Standard, listOf(7)))

        assertEquals(RegenerateResult.PageUnreadable(7, SourceProblem.FileUnavailable), result)
    }
}
