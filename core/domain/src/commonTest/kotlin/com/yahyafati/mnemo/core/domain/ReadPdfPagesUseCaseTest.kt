package com.yahyafati.mnemo.core.domain

import com.yahyafati.mnemo.core.data.repository.PageReadFailure
import com.yahyafati.mnemo.core.data.repository.PageTranscription
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiRoute
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Reading PDF pages with AI (docs/pdf/ROADMAP.md, P5): which pages come from where, resume, cache, blank pages and the limit. */
class ReadPdfPagesUseCaseTest {
    private val sources = FakeSourceRepository()
    private val reading = FakePdfReadRepository()
    private val useCase = ReadPdfPagesUseCase(sources, reading)
    private val cache = PageTranscriptionCache()

    private val handle = PdfHandle("pdf-1", PdfInfo(6, "Mixed"))
    private val route = AiRoute(
        task = AiTask.ReadPages,
        provider = AiProvider(id = "p", name = "Local", baseUrl = "http://localhost:11434/v1", defaultModel = "vision", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        modelId = "vision",
        capabilities = AiCapabilities(vision = true),
        usesDefault = true,
    )

    private val layer = "This page has a text layer of its own with plenty of words."

    /** Pages 1, 3 and 5 have a text layer; 2, 4 and 6 are pictures, as in the `mixed.pdf` fixture. */
    private fun mixedLayers() {
        sources.pageTextLayers = { _, pages -> PdfPageTextsResult.Success(pages.associateWith { if (it % 2 == 1) "$layer ($it)" else "" }) }
    }

    private suspend fun plan(mode: PdfReadMode, pages: List<Int> = (1..6).toList(), quality: PdfQuality = PdfQuality.Standard) =
        assertIs<PdfReadPlanResult.Ready>(useCase.plan(handle, pages, mode, quality, "vision", cache)).plan

    @Test
    fun autoTakesTheTextLayerWhereThereIsOneAndTranscribesTheRest() = runTest {
        mixedLayers()
        val plan = plan(PdfReadMode.Auto)

        assertEquals(listOf(PageSource.Layer, PageSource.Transcribe, PageSource.Layer, PageSource.Transcribe, PageSource.Layer, PageSource.Transcribe), plan.pages.map { it.source })
        assertEquals(3, plan.requests)
        assertFalse(plan.isAllLayer)

        val events = useCase.run(route, plan, cache).toList()
        assertEquals(listOf(2, 4, 6), reading.transcribed.map { it.page })
        val finished = assertIs<ReadPagesEvent.Finished>(events.last())
        assertEquals(
            listOf("$layer (1)", "Transcription of page 2.", "$layer (3)", "Transcription of page 4.", "$layer (5)", "Transcription of page 6.").joinToString("\n\n"),
            finished.text,
        )
        assertEquals(3, finished.transcribed)
        assertEquals(0, finished.blankPages)
        assertFalse(finished.truncated)
    }

    @Test
    fun aPageWithJustAStrayMarkIsNotATextLayer() = runTest {
        sources.pageTextLayers = { _, pages -> PdfPageTextsResult.Success(pages.associateWith { "12" }) }
        assertEquals(6, plan(PdfReadMode.Auto).requests)
    }

    @Test
    fun aPdfWithTextOnEveryPageNeedsNoRequestInAuto() = runTest {
        val plan = plan(PdfReadMode.Auto, listOf(2, 4))

        assertTrue(plan.isAllLayer)
        assertEquals(0, plan.requests)
        assertTrue(plan.needsNoRequests)
    }

    @Test
    fun readPagesWithAiTranscribesEveryPageWithoutLookingAtTheTextLayer() = runTest {
        val plan = plan(PdfReadMode.ReadWithAi, listOf(5, 1, 3))

        assertEquals(emptyList(), sources.pageTextRequests)
        assertEquals(listOf(1, 3, 5), plan.pages.map { it.page }) // document order
        assertEquals(3, plan.requests)
        val events = useCase.run(route, plan, cache).toList()
        assertEquals(listOf(1, 3, 5), reading.transcribed.map { it.page })
        assertEquals(PdfQuality.Standard, reading.transcribed.first().quality)
        assertEquals("Transcription of page 1.\n\nTranscription of page 3.\n\nTranscription of page 5.", assertIs<ReadPagesEvent.Finished>(events.last()).text)
    }

    @Test
    fun progressCountsEveryPageAndNamesTheOnesSentToTheModel() = runTest {
        mixedLayers()
        val events = useCase.run(route, plan(PdfReadMode.Auto, listOf(1, 2, 3)), cache).toList()

        assertEquals(
            listOf(
                ReadPagesEvent.Progress(1, 3, "$layer (1)"),
                ReadPagesEvent.Transcribing(1, 3, 2),
                ReadPagesEvent.Progress(2, 3, "$layer (1)\n\nTranscription of page 2."),
                ReadPagesEvent.Progress(3, 3, "$layer (1)\n\nTranscription of page 2.\n\n$layer (3)"),
            ),
            events.dropLast(1),
        )
    }

    @Test
    fun aFailureKeepsTheTextSoFarAndResumingStartsAtThePageThatFailed() = runTest {
        reading.respond = { page ->
            if (page == 3) PageTranscription.Failure(PageReadFailure.Ai(AiFailure(AiProblem.RateLimited))) else PageTranscription.Success("Page $page.")
        }
        val plan = plan(PdfReadMode.ReadWithAi, listOf(1, 2, 3, 4))
        val events = useCase.run(route, plan, cache).toList()

        val failed = assertIs<ReadPagesEvent.Failed>(events.last())
        assertEquals(2, failed.index)
        assertEquals(3, failed.page)
        assertEquals(AiProblem.RateLimited, assertIs<PageReadFailure.Ai>(failed.failure).failure.problem)
        assertEquals("Page 1.\n\nPage 2.", failed.text)

        reading.respond = { page -> PageTranscription.Success("Page $page.") }
        reading.transcribed.clear()
        val resumed = useCase.run(route, plan, cache, from = failed.index, before = failed.text).toList()

        assertEquals(listOf(3, 4), reading.transcribed.map { it.page })
        assertEquals("Page 1.\n\nPage 2.\n\nPage 3.\n\nPage 4.", assertIs<ReadPagesEvent.Finished>(resumed.last()).text)
    }

    @Test
    fun aPageThatCameOutBlankIsLeftOutNotAnError() = runTest {
        reading.respond = { page ->
            if (page == 2) PageTranscription.Failure(PageReadFailure.Page(SourceProblem.BlankPage)) else PageTranscription.Success("Page $page.")
        }
        val finished = assertIs<ReadPagesEvent.Finished>(useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1, 2, 3)), cache).toList().last())

        assertEquals("Page 1.\n\nPage 3.", finished.text)
        assertEquals(1, finished.blankPages)
        assertEquals(2, finished.transcribed)
    }

    @Test
    fun otherPageProblemsStopTheRun() = runTest {
        reading.respond = { PageTranscription.Failure(PageReadFailure.Page(SourceProblem.Encrypted)) }
        val events = useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1, 2)), cache).toList()

        assertEquals(1, reading.transcribed.size)
        assertEquals(SourceProblem.Encrypted, assertIs<PageReadFailure.Page>(assertIs<ReadPagesEvent.Failed>(events.last()).failure).problem)
    }

    @Test
    fun whatWasTranscribedIsNotAskedForAgain() = runTest {
        useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1, 2)), cache).toList()
        reading.transcribed.clear()

        val again = plan(PdfReadMode.ReadWithAi, listOf(2, 3))
        assertEquals(listOf(PageSource.Cached, PageSource.Transcribe), again.pages.map { it.source })
        assertEquals(1, again.requests)
        val finished = assertIs<ReadPagesEvent.Finished>(useCase.run(route, again, cache).toList().last())
        assertEquals(listOf(3), reading.transcribed.map { it.page })
        assertEquals("Transcription of page 2.\n\nTranscription of page 3.", finished.text)
        assertEquals(1, finished.transcribed) // only the new request is a new transcription
    }

    @Test
    fun aBlankPageIsRememberedToo() = runTest {
        reading.respond = { PageTranscription.Failure(PageReadFailure.Page(SourceProblem.BlankPage)) }
        useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1)), cache).toList()

        val again = plan(PdfReadMode.ReadWithAi, listOf(1))
        assertEquals(0, again.requests)
        assertEquals(1, assertIs<ReadPagesEvent.Finished>(useCase.run(route, again, cache).toList().last()).blankPages)
    }

    @Test
    fun theCacheKnowsTheModelTheQualityAndThePdf() = runTest {
        useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1)), cache).toList()

        assertEquals(1, plan(PdfReadMode.ReadWithAi, listOf(1), PdfQuality.High).requests)
        assertEquals(1, assertIs<PdfReadPlanResult.Ready>(useCase.plan(handle, listOf(1), PdfReadMode.ReadWithAi, PdfQuality.Standard, "other-model", cache)).plan.requests)
        assertEquals(1, assertIs<PdfReadPlanResult.Ready>(useCase.plan(PdfHandle("pdf-2", handle.info), listOf(1), PdfReadMode.ReadWithAi, PdfQuality.Standard, "vision", cache)).plan.requests)
        assertEquals(0, plan(PdfReadMode.ReadWithAi, listOf(1)).requests)

        cache.clear(handle)
        assertEquals(1, plan(PdfReadMode.ReadWithAi, listOf(1)).requests)
    }

    @Test
    fun theRunStopsWhenTheBoxIsFullInsteadOfPayingForTextThatWouldBeCutOff() = runTest {
        val long = "word ".repeat(SourceText.MAX_CHARS / 5 + 50).trim()
        reading.respond = { PageTranscription.Success(long) }
        val events = useCase.run(route, plan(PdfReadMode.ReadWithAi, listOf(1, 2, 3)), cache).toList()

        val finished = assertIs<ReadPagesEvent.Finished>(events.last())
        assertTrue(finished.truncated)
        assertEquals(SourceText.MAX_CHARS, finished.text.length)
        assertEquals(1, reading.transcribed.size)
    }

    @Test
    fun aCancelledCollectorStopsTheRequests() = runTest {
        val events = useCase.run(route, plan(PdfReadMode.ReadWithAi, (1..6).toList()), cache).take(3).toList()

        // Transcribing page 1, its progress, then "transcribing page 2" is the third event: that request is never made.
        assertEquals(3, events.size)
        assertEquals(listOf(1), reading.transcribed.map { it.page })
    }

    @Test
    fun aTextLayerThatCannotBeReadFailsThePlan() = runTest {
        sources.pageTextLayers = { _, _ -> PdfPageTextsResult.Failure(SourceProblem.Encrypted) }
        assertEquals(SourceProblem.Encrypted, assertIs<PdfReadPlanResult.Failed>(useCase.plan(handle, listOf(1), PdfReadMode.Auto, PdfQuality.Standard, "vision", cache)).problem)
    }
}
