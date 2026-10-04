package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.data.repository.PageImages
import com.yahyafati.mnemo.core.data.repository.UnreadablePage
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.DisclosureStep
import com.yahyafati.mnemo.core.model.PageRangeError
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cards from page images in Smart Extract (docs/pdf/ROADMAP.md, P6): the grid and the Pages field, the run, the queue, retry and regenerate. */
class SmartExtractPageImagesTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val settings = FakeUserSettingsRepository()
    private val sources = FakeSourceRepository()
    private val reading = FakePdfReadRepository()
    private val generation = FakeCardGenerationRepository()

    private val handle = PdfHandle("1", PdfInfo(6, "Slides"))

    private fun setUpProvider(vision: Boolean = true, textAccepted: Boolean = true) = providers.addProvider(
        AiProvider(
            id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "vision-model",
            disclosureAcceptedAt = if (textAccepted) Instant.EPOCH else null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
        listOf(AiModel("p", "vision-model", AiCapabilities(vision = vision))),
    )

    private fun viewModel() = SmartExtractViewModel(
        aiProviders = providers,
        deckRepository = FakeDeckRepository(),
        sources = sources,
        generateCards = GenerateCardsUseCase(generation, FakeCardRepository()),
        regenerateCard = RegenerateCardUseCase(generation, FakeCardRepository()),
        acceptCards = AcceptGeneratedCardsUseCase(FakeCardRepository(), FakeMediaRepository()),
        bookHandoff = BookHandoff(),
        userSettings = settings,
        readPdfPages = ReadPdfPagesUseCase(sources, reading),
    )

    private val SmartExtractViewModel.state get() = uiState.value
    private val SmartExtractViewModel.pdf get() = assertNotNull(state.pdf)

    private fun SmartExtractViewModel.open(pdf: PdfHandle = handle): SmartExtractViewModel {
        sources.pdfs["content://doc/1"] = PdfOpenResult.Success(pdf)
        onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        return this
    }

    /** The PDF open in the image mode, with [pages] ticked. */
    private fun SmartExtractViewModel.openForCards(vararg pages: Int, pdf: PdfHandle = handle): SmartExtractViewModel {
        open(pdf)
        onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))
        pages.forEach { onAction(SmartExtractAction.TogglePdfPage(it)) }
        return this
    }

    private fun SmartExtractViewModel.generateAndAgree() {
        onAction(SmartExtractAction.Generate)
        onAction(SmartExtractAction.ConfirmPdfRead)
        while (state.disclosure != null) onAction(SmartExtractAction.AcceptDisclosure)
    }

    private fun pageFile(page: Int): File = File.createTempFile("page-$page-", ".jpg").apply { deleteOnExit() }

    @Test
    fun theImageModeNeedsAModelThatSeesImages() = runTest {
        setUpProvider(vision = false)
        val vm = viewModel().open()

        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))

        assertEquals(PdfReadMode.Text, vm.pdf.mode)
        assertFalse(vm.state.showsPageGrid)
    }

    @Test
    fun choosingTheImageModeShowsTheGridWithNothingTickedAndIsRemembered() = runTest {
        setUpProvider()
        val vm = viewModel().open()

        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))

        assertTrue(vm.state.showsPageGrid)
        // An empty field means every page to a text read; as ticks it would be nothing, so nothing is ticked.
        assertEquals("", vm.pdf.pages)
        assertEquals(emptySet(), vm.pdf.selectedPages)
        assertFalse(vm.state.canGenerate)
        assertEquals(PdfReadMode.PageImages, settings.settings.value.pdfReadMode)
    }

    @Test
    fun opening_a_pdf_in_the_image_mode_reads_no_text_and_says_nothing_about_scans() = runTest {
        setUpProvider()
        settings.setPdfReadOptions(PdfReadMode.PageImages, PdfQuality.Standard)
        sources.pdfText = { _, _ -> SourceResult.Failure(SourceProblem.NoText) }

        val vm = viewModel().open()

        assertEquals(PdfReadMode.PageImages, vm.pdf.mode)
        assertEquals(emptyList(), sources.pdfReads)
        assertEquals(emptyList(), sources.pageTextRequests)
        assertNull(vm.state.sourceProblem)
        assertFalse(vm.state.reading)
        assertEquals("Slides", vm.state.title)
        assertEquals(0, vm.pdf.pagesWithoutText)
    }

    @Test
    fun theTicksAreTheFieldAndTheFieldIsTheTicks() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards(2, 3, 4, 6)

        assertEquals("2-4, 6", vm.pdf.pages)
        assertEquals(setOf(2, 3, 4, 6), vm.pdf.selectedPages)

        vm.onAction(SmartExtractAction.TogglePdfPage(3))
        assertEquals("2, 4, 6", vm.pdf.pages)
        assertEquals(setOf(2, 4, 6), vm.pdf.selectedPages)

        // Typing in the field moves the ticks.
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-2, 5"))
        assertEquals(setOf(1, 2, 5), vm.pdf.selectedPages)
        assertNull(vm.pdf.error)

        // A field that can't be read ticks nothing and says why; generating waits for it.
        vm.onAction(SmartExtractAction.PdfPagesChanged("9"))
        assertEquals(PdfPagesError.Invalid(PageRangeError.OutOfRange(9)), vm.pdf.error)
        assertEquals(emptySet(), vm.pdf.selectedPages)
        assertFalse(vm.state.canGenerate)

        vm.onAction(SmartExtractAction.SelectAllPdfPages(true))
        assertEquals("1-6", vm.pdf.pages)
        assertEquals(6, vm.pdf.selectedPages.size)
        assertNull(vm.pdf.error)

        vm.onAction(SmartExtractAction.SelectAllPdfPages(false))
        assertEquals("", vm.pdf.pages)
        assertEquals(emptySet(), vm.pdf.selectedPages)
    }

    @Test
    fun tickingAfterAFieldThatCouldNotBeReadStartsOver() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards()
        vm.onAction(SmartExtractAction.PdfPagesChanged("2-"))
        vm.onAction(SmartExtractAction.PdfPagesChanged("2-x"))
        assertNotNull(vm.pdf.error)

        vm.onAction(SmartExtractAction.TogglePdfPage(4))

        assertEquals("4", vm.pdf.pages)
        assertEquals(setOf(4), vm.pdf.selectedPages)
        assertNull(vm.pdf.error)
    }

    @Test
    fun theFirstThreeHundredPagesAWholeLongPdfOpensWithAreNotTickedAsPages() = runTest {
        setUpProvider()
        val big = PdfHandle("1", PdfInfo(450, "Big"))
        val vm = viewModel().open(big)
        assertEquals("1-300", vm.pdf.pages)

        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))
        assertEquals("", vm.pdf.pages)
        assertEquals(emptySet(), vm.pdf.selectedPages)

        // Select all stops at the limit one read takes; one more page is over it.
        vm.onAction(SmartExtractAction.SelectAllPdfPages(true))
        assertEquals("1-300", vm.pdf.pages)
        vm.onAction(SmartExtractAction.TogglePdfPage(301))
        assertEquals(PdfPagesError.TooMany(301, 300), vm.pdf.error)
        assertFalse(vm.state.canGenerate)

        // Back to a text read, which starts from what it opened with.
        vm.onAction(SmartExtractAction.SelectAllPdfPages(false))
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.Text))
        assertEquals("1-300", vm.pdf.pages)
    }

    @Test
    fun theEstimateAndTheRequestsFollowTheTicks() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards(1, 2, 3, 4)

        assertEquals(listOf(listOf(1, 2, 3), listOf(4)), vm.state.pageBatches)
        // 900 words for the request of three pages (12 cards at 70 words a card) and 300 for the one page (4 cards).
        assertEquals(12 + 4, vm.state.estimatedCards)
        assertTrue(vm.state.canGenerate)
    }

    @Test
    fun generatingAsksAboutTheRequestsFirstAndSendsNothingUntilTheNoticesAreAccepted() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards(1, 2, 3, 4, 5, 6)

        vm.onAction(SmartExtractAction.Generate)

        assertEquals(
            PdfReadConfirmation(pages = 6, requests = 2, providerName = "Groq", modelId = "vision-model", quality = PdfQuality.Standard, forCards = true),
            vm.pdf.readConfirmation,
        )
        assertEquals(emptyList(), generation.requests)

        vm.onAction(SmartExtractAction.ConfirmPdfRead)
        // The provider's own notice was accepted before; the images' is new.
        assertEquals(DisclosureStep.Images, vm.state.disclosureStep)
        assertNotNull(vm.state.disclosure)
        assertEquals(emptyList(), generation.requests)

        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertEquals(setOf("p"), settings.settings.value.imageDisclosureProviders)
        assertEquals(2, generation.requests.size)
    }

    @Test
    fun aProviderNotSeenBeforeAsksForItsOwnNoticeFirstThenTheImages() = runTest {
        setUpProvider(textAccepted = false)
        val vm = viewModel().openForCards(1)
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)

        assertEquals(DisclosureStep.Text, vm.state.disclosureStep)
        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertEquals(DisclosureStep.Images, vm.state.disclosureStep)
        assertEquals(emptyList(), generation.requests)
        vm.onAction(SmartExtractAction.AcceptDisclosure)

        assertEquals(1, generation.requests.size)
    }

    @Test
    fun decliningTheConfirmationOrTheNoticeSendsNothing() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards(1, 2)

        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.DismissPdfReadConfirmation)
        assertNull(vm.pdf.readConfirmation)
        vm.onAction(SmartExtractAction.ConfirmPdfRead) // nothing is waiting any more
        assertNull(vm.state.disclosure)

        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)
        vm.onAction(SmartExtractAction.DismissDisclosure)

        assertEquals(emptyList(), generation.requests)
        assertEquals(emptySet(), settings.settings.value.imageDisclosureProviders)
        assertEquals(GenerationState.Idle, vm.state.generation)
    }

    @Test
    fun theRunSendsThreePagesToARequestOnTheReadPagesRouteWithTheTextLayerOfThoseThatHaveOne() = runTest {
        setUpProvider()
        sources.pageTextLayers = { _, pages -> PdfPageTextsResult.Success(pages.associateWith { if (it == 1) "A text layer long enough to count as text, page one." else "x" }) }
        generation.respond = { request ->
            flowOf(GenerationUpdate.Card(card("Q${request.part}", "A").copy(id = "c${request.part}", chunkIndex = request.part, page = request.pages!!.pages.first())), GenerationUpdate.Done())
        }
        val vm = viewModel().openForCards(1, 2, 3, 4, 5, 6)

        vm.generateAndAgree()

        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5, 6)), generation.requests.map { it.pages!!.pages })
        assertEquals(setOf(AiTask.ReadPages), generation.routes.map { it.task }.toSet())
        assertEquals(setOf(PdfQuality.Standard), generation.requests.map { it.pages!!.quality }.toSet())
        assertEquals("[Page 1]\nA text layer long enough to count as text, page one.", generation.requests[0].text)
        assertEquals("", generation.requests[1].text)
        assertEquals("Slides", generation.requests[0].title)

        assertEquals(GenerationState.Done(2), vm.state.generation)
        assertEquals(listOf(1, 4), vm.state.queue.map { it.card.page })
        assertEquals(PageImages(handle, PdfQuality.Standard, listOf(4, 5, 6)), vm.state.queue[1].pages)
        assertEquals(listOf(0, 1), vm.state.queue.map { it.card.chunkIndex })
    }

    @Test
    fun theQualityChosenIsTheQualityOfTheImages() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards(1)
        vm.onAction(SmartExtractAction.SetPdfQuality(PdfQuality.High))

        vm.generateAndAgree()

        assertEquals(PdfQuality.High, generation.requests.single().pages!!.quality)
        assertEquals(PdfQuality.High, settings.settings.value.pdfQuality)
    }

    @Test
    fun theProgressNamesThePagesOfEachRequest() = runTest {
        setUpProvider()
        val stream = generation.streamed()
        val vm = viewModel().openForCards(1, 2, 3, 4)
        vm.generateAndAgree()

        assertEquals(GenerationState.Running(0, 2, listOf(1, 2, 3)), vm.state.generation)
        stream.trySend(GenerationUpdate.Done())
        assertEquals(GenerationState.Running(1, 2, listOf(4)), vm.state.generation)
        stream.trySend(GenerationUpdate.Done())
        assertEquals(GenerationState.Done(0), vm.state.generation)
    }

    @Test
    fun aPageThatCannotBeDrawnStopsTheRunAndRetryDrawsItAgain() = runTest {
        setUpProvider()
        generation.respond = { request ->
            if (request.part == 1) {
                flowOf(GenerationUpdate.Done(unreadablePage = UnreadablePage(5, SourceProblem.Unsupported)))
            } else {
                flowOf(GenerationUpdate.Card(card("Q0", "A").copy(id = "c0", page = 2)), GenerationUpdate.Done())
            }
        }
        val vm = viewModel().openForCards(1, 2, 3, 4, 5, 6)
        vm.generateAndAgree()

        assertEquals(GenerationState.PageUnreadable(5, SourceProblem.Unsupported, part = 1, parts = 2), vm.state.generation)
        assertEquals(1, vm.state.queue.size)

        generation.requests.clear()
        generation.routes.clear()
        generation.respond = { flowOf(GenerationUpdate.Card(card("Q1", "A").copy(id = "c1", page = 5)), GenerationUpdate.Done()) }
        vm.onAction(SmartExtractAction.Retry)

        assertEquals(listOf(1), generation.requests.map { it.part })
        assertEquals(listOf(4, 5, 6), generation.requests.single().pages!!.pages)
        assertEquals(AiTask.ReadPages, generation.routes.single().task)
        assertEquals(GenerationState.Done(1), vm.state.generation)
        assertEquals(listOf(2, 5), vm.state.queue.map { it.card.page })
    }

    @Test
    fun regeneratingACardSendsTheSameImagesAgainWithoutAskingAboutTheNoticesAgain() = runTest {
        setUpProvider()
        generation.respond = { request ->
            flowOf(GenerationUpdate.Card(card("Q${request.part}", "A").copy(id = "c${request.part}", page = request.pages!!.pages.last())), GenerationUpdate.Done())
        }
        val vm = viewModel().openForCards(1, 2, 3, 4)
        vm.generateAndAgree()
        generation.requests.clear()
        generation.routes.clear()
        generation.respond = { flowOf(GenerationUpdate.Card(card("A better one", "A").copy(id = "new", page = 4)), GenerationUpdate.Done()) }

        vm.onAction(SmartExtractAction.Regenerate("c1"))

        assertNull(vm.state.disclosure)
        val sent = generation.requests.single()
        assertEquals(PageImages(handle, PdfQuality.Standard, listOf(4)), sent.pages)
        assertEquals("c1", sent.replacing?.id)
        assertEquals(AiTask.ReadPages, generation.routes.single().task)
        val replaced = vm.state.queue.single { it.card.id == "new" }
        assertEquals(PageImages(handle, PdfQuality.Standard, listOf(4)), replaced.pages)
        assertEquals(4, replaced.card.page)
    }

    @Test
    fun regeneratingTellsWhenThePageCannotBeDrawn() = runTest {
        setUpProvider()
        generation.respond = { flowOf(GenerationUpdate.Card(card("Q", "A").copy(id = "c0", page = 1)), GenerationUpdate.Done()) }
        val vm = viewModel().openForCards(1)
        vm.generateAndAgree()
        generation.respond = { flowOf(GenerationUpdate.Done(unreadablePage = UnreadablePage(1, SourceProblem.FileUnavailable))) }

        vm.onAction(SmartExtractAction.Regenerate("c0"))

        assertEquals(ExtractMessage.RegeneratePageUnreadable(1, SourceProblem.FileUnavailable), vm.state.message)
        assertFalse(vm.state.queue.single().regenerating)
    }

    @Test
    fun aTextRunIsUntouchedByAllThis() = runTest {
        setUpProvider()
        val vm = viewModel()
        vm.onAction(SmartExtractAction.TextChanged("Mitochondria make ATP."))
        vm.onAction(SmartExtractAction.Generate)

        val request = generation.requests.single()
        assertNull(request.pages)
        assertEquals(AiTask.Extract, generation.routes.single().task)
        assertNull(vm.state.disclosure)
    }

    @Test
    fun aPageOpensLargeAndTheViewerStaysInsideThePdf() = runTest {
        setUpProvider()
        val vm = viewModel().openForCards()

        vm.onAction(SmartExtractAction.OpenPdfPage(3))
        assertEquals(3, vm.pdf.viewPage)
        vm.onAction(SmartExtractAction.OpenPdfPage(7))
        assertEquals(3, vm.pdf.viewPage)
        vm.onAction(SmartExtractAction.OpenPdfPage(0))
        assertEquals(3, vm.pdf.viewPage)
        vm.onAction(SmartExtractAction.OpenPdfPage(6))
        assertEquals(6, vm.pdf.viewPage)

        vm.onAction(SmartExtractAction.ClosePdfPage)
        assertNull(vm.pdf.viewPage)

        // Leaving the image mode closes it too.
        vm.onAction(SmartExtractAction.OpenPdfPage(2))
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.Text))
        assertNull(vm.pdf.viewPage)
    }

    @Test
    fun thePictureOfAPageIsRenderedOnceAndABlankOneIsNotAskedAgain() = runTest {
        setUpProvider()
        sources.pdfPage = { _, page -> if (page == 2) PdfPageResult.Failure(SourceProblem.BlankPage) else PdfPageResult.Success(pageFile(page)) }
        val vm = viewModel().openForCards()

        assertNotNull(vm.pageFiles.thumbnail(1))
        assertNull(vm.pageFiles.thumbnail(2))
        assertNull(vm.pageFiles.thumbnail(2))
        assertEquals(listOf(1 to null, 2 to null), sources.renderedPages.map { it.second to it.third })

        vm.onAction(SmartExtractAction.SetPdfQuality(PdfQuality.High))
        assertNotNull(vm.pageFiles.page(1))
        assertEquals(PdfQuality.High, sources.renderedPages.last().third)
    }

    @Test
    fun noPictureIsAvailableOnceThePdfIsClosed() = runTest {
        setUpProvider()
        sources.pdfPage = { _, page -> PdfPageResult.Success(pageFile(page)) }
        val vm = viewModel().openForCards()
        assertNotNull(vm.pageFiles.thumbnail(1))

        vm.onAction(SmartExtractAction.ClearText)

        assertNull(vm.pageFiles.thumbnail(3))
        assertNull(vm.pageFiles.page(1))
        assertEquals(listOf(handle), sources.closedPdfs)
    }

    @Test
    fun theEditsOfTheTextBoxAreLeftAloneByTheGrid() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.TextChanged("My notes"))

        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))
        vm.onAction(SmartExtractAction.TogglePdfPage(2))
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.Text))

        assertEquals("My notes", vm.state.text)
        assertFalse(vm.pdf.replaceConfirmation)
    }
}
