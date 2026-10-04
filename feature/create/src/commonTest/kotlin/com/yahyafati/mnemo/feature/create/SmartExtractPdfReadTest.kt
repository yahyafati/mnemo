package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.PageReadFailure
import com.yahyafati.mnemo.core.data.repository.PageTranscription
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiFailure
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProblem
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.AiTask
import com.yahyafati.mnemo.core.model.DisclosureStep
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageTextsResult
import com.yahyafati.mnemo.core.model.PdfQuality
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reading PDF pages with AI in Smart Extract (docs/pdf/ROADMAP.md, P5): the modes, the confirmation, the notices, progress, resume and the cache. */
class SmartExtractPdfReadTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val settings = FakeUserSettingsRepository()
    private val sources = FakeSourceRepository()
    private val reading = FakePdfReadRepository()

    private fun provider(textAccepted: Boolean = true) = AiProvider(
        id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "vision-model",
        disclosureAcceptedAt = if (textAccepted) Instant.EPOCH else null, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    /** A provider whose model sees images (or, with [vision] false, one that doesn't). */
    private fun setUpProvider(vision: Boolean = true, textAccepted: Boolean = true) =
        providers.addProvider(provider(textAccepted), listOf(AiModel("p", "vision-model", AiCapabilities(vision = vision))))

    private fun viewModel() = SmartExtractViewModel(
        aiProviders = providers,
        deckRepository = FakeDeckRepository(),
        sources = sources,
        generateCards = GenerateCardsUseCase(FakeCardGenerationRepository(), FakeCardRepository()),
        regenerateCard = RegenerateCardUseCase(FakeCardGenerationRepository(), FakeCardRepository()),
        acceptCards = AcceptGeneratedCardsUseCase(FakeCardRepository()),
        bookHandoff = BookHandoff(),
        userSettings = settings,
        readPdfPages = ReadPdfPagesUseCase(sources, reading),
    )

    private val SmartExtractViewModel.state get() = uiState.value
    private val SmartExtractViewModel.pdf get() = assertNotNull(state.pdf)

    private val layer = "This page has a text layer of its own with plenty of words."
    private val handle = PdfHandle("1", PdfInfo(6, "Scans"))

    /** Pages 1, 3 and 5 have a text layer, 2, 4 and 6 are only a picture (the `mixed.pdf` fixture). */
    private fun mixed() {
        sources.pageTextLayers = { _, pages -> PdfPageTextsResult.Success(pages.associateWith { if (it % 2 == 1) "$layer ($it)" else "" }) }
    }

    /** Every page is only a picture, and the text read finds nothing. */
    private fun scanned() {
        sources.pageTextLayers = { _, pages -> PdfPageTextsResult.Success(pages.associateWith { "" }) }
        sources.pdfText = { _, _ -> SourceResult.Failure(SourceProblem.NoText) }
    }

    private fun SmartExtractViewModel.open(): SmartExtractViewModel {
        sources.pdfs["content://doc/1"] = PdfOpenResult.Success(handle)
        onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        return this
    }

    /** Reads with AI and agrees to the requests and, when they come, to the notices. */
    private fun SmartExtractViewModel.readAndAgree(mode: PdfReadMode = PdfReadMode.Auto) {
        onAction(SmartExtractAction.SetPdfReadMode(mode))
        onAction(SmartExtractAction.ApplyPdfPages)
        onAction(SmartExtractAction.ConfirmPdfRead)
        while (state.disclosure != null) onAction(SmartExtractAction.AcceptDisclosure)
    }

    @Test
    fun withoutAModelThatSeesImagesThePdfIsReadAsTextAndAiModesCannotBeChosen() = runTest {
        setUpProvider(vision = false)
        val vm = viewModel().open()

        assertFalse(vm.state.canReadPages)
        assertEquals(PdfReadMode.Text, vm.pdf.mode)
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.Auto))
        assertEquals(PdfReadMode.Text, vm.pdf.mode)
        assertNull(settings.settings.value.pdfReadMode)
    }

    @Test
    fun withoutAnyProviderToo() = runTest {
        val vm = viewModel().open()

        assertFalse(vm.state.canReadPages)
        assertEquals(PdfReadMode.Text, vm.pdf.mode)
        assertEquals("Text of 1-6.", vm.state.text)
    }

    @Test
    fun autoIsTheDefaultForAModelThatSeesImages() = runTest {
        setUpProvider()
        val vm = viewModel().open()

        assertTrue(vm.state.canReadPages)
        assertEquals(PdfReadMode.Auto, vm.pdf.mode)
        assertEquals(PdfQuality.Standard, vm.pdf.quality)
        // Opening reads the text layer whatever the mode: it is free and says which pages have none.
        assertEquals("Text of 1-6.", vm.state.text)
        assertEquals(emptyList(), reading.transcribed)
    }

    @Test
    fun theRememberedModeAndQualityComeBackButAnAiModeNeedsAModelThatSeesImages() = runTest {
        settings.setPdfReadOptions(PdfReadMode.ReadWithAi, PdfQuality.High)
        setUpProvider()
        val vm = viewModel().open()
        assertEquals(PdfReadMode.ReadWithAi, vm.pdf.mode)
        assertEquals(PdfQuality.High, vm.pdf.quality)

        // The model stops seeing images: the remembered AI mode falls back to the text layer.
        providers.addProvider(provider(), listOf(AiModel("p", "vision-model", AiCapabilities(vision = false))))
        assertEquals(PdfReadMode.Text, viewModel().open().pdf.mode)
    }

    @Test
    fun choosingAModeOrAQualityIsRemembered() = runTest {
        setUpProvider()
        val vm = viewModel().open()

        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.ReadWithAi))
        vm.onAction(SmartExtractAction.SetPdfQuality(PdfQuality.High))

        assertEquals(PdfReadMode.ReadWithAi, vm.pdf.mode)
        assertEquals(PdfReadMode.ReadWithAi, settings.settings.value.pdfReadMode)
        assertEquals(PdfQuality.High, settings.settings.value.pdfQuality)
    }

    @Test
    fun pagesWithoutTextAreCountedAfterTheTextRead() = runTest {
        setUpProvider()
        mixed()
        val vm = viewModel().open()

        assertEquals(3, vm.pdf.pagesWithoutText)
        assertEquals("Text of 1-6.", vm.state.text)
    }

    @Test
    fun aPdfWithNoTextAtAllCountsEveryPageAndSaysNoText() = runTest {
        setUpProvider()
        scanned()
        val vm = viewModel().open()

        assertEquals(SourceProblem.NoText, vm.state.sourceProblem)
        assertEquals(6, vm.pdf.pagesWithoutText)
        assertEquals("", vm.state.text)
    }

    @Test
    fun offeringToReadThemWithAiAsksAboutTheRequestsBeforeSendingAnything() = runTest {
        setUpProvider()
        mixed()
        val vm = viewModel().open()

        vm.onAction(SmartExtractAction.ReadPdfPagesWithAi)

        assertEquals(PdfReadMode.Auto, vm.pdf.mode)
        assertEquals(PdfReadConfirmation(pages = 6, requests = 3, providerName = "Groq", modelId = "vision-model", quality = PdfQuality.Standard), vm.pdf.readConfirmation)
        assertEquals(emptyList(), reading.transcribed)
        assertEquals("Text of 1-6.", vm.state.text) // the box is untouched until the user agrees
        assertFalse(vm.state.reading)
    }

    @Test
    fun decliningTheConfirmationSendsNothing() = runTest {
        setUpProvider()
        mixed()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.ReadPdfPagesWithAi)

        vm.onAction(SmartExtractAction.DismissPdfReadConfirmation)

        assertNull(vm.pdf.readConfirmation)
        assertEquals(emptyList(), reading.transcribed)
        vm.onAction(SmartExtractAction.ConfirmPdfRead) // nothing is waiting any more
        assertNull(vm.state.disclosure)
        assertEquals(emptyList(), reading.transcribed)
    }

    @Test
    fun agreeingAsksForTheImageNoticeOnceThenReadsThePagesIntoTheBox() = runTest {
        setUpProvider()
        mixed()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.ReadPdfPagesWithAi)

        vm.onAction(SmartExtractAction.ConfirmPdfRead)
        // The provider's own notice was accepted before; the images' is new.
        assertEquals(DisclosureStep.Images, vm.state.disclosureStep)
        assertNotNull(vm.state.disclosure)
        assertEquals(emptyList(), reading.transcribed)

        vm.onAction(SmartExtractAction.AcceptDisclosure)

        assertNull(vm.state.disclosure)
        assertEquals(setOf("p"), settings.settings.value.imageDisclosureProviders)
        assertEquals(listOf(2, 4, 6), reading.transcribed.map { it.page })
        assertEquals(AiTask.ReadPages, reading.transcribed.first().route.task)
        assertEquals(
            listOf("$layer (1)", "Transcription of page 2.", "$layer (3)", "Transcription of page 4.", "$layer (5)", "Transcription of page 6.").joinToString("\n\n"),
            vm.state.text,
        )
        assertFalse(vm.state.reading)
        assertNull(vm.pdf.readState)
        assertTrue(vm.pdf.transcribed)
        assertEquals(0, vm.pdf.pagesWithoutText)

        // The notice is not asked again for the next read (a new quality means new requests).
        vm.onAction(SmartExtractAction.SetPdfQuality(PdfQuality.High))
        vm.onAction(SmartExtractAction.ApplyPdfPages)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)
        assertNull(vm.state.disclosure)
        assertEquals(listOf(2, 4, 6, 2, 4, 6), reading.transcribed.map { it.page })
    }

    @Test
    fun aProviderNotSeenBeforeAsksForItsOwnNoticeFirstThenTheImagesAndNothingGoesOutUntilBoth() = runTest {
        setUpProvider(textAccepted = false)
        mixed()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.ReadPdfPagesWithAi)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)

        assertEquals(DisclosureStep.Text, vm.state.disclosureStep)
        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertEquals(DisclosureStep.Images, vm.state.disclosureStep)
        assertNotNull(vm.state.disclosure)
        assertEquals(emptyList(), reading.transcribed)

        vm.onAction(SmartExtractAction.AcceptDisclosure)
        assertNull(vm.state.disclosure)
        assertEquals(listOf(2, 4, 6), reading.transcribed.map { it.page })
    }

    @Test
    fun refusingTheImageNoticeSendsNothing() = runTest {
        setUpProvider()
        mixed()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.ReadPdfPagesWithAi)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)

        vm.onAction(SmartExtractAction.DismissDisclosure)

        assertNull(vm.state.disclosure)
        assertEquals(emptyList(), reading.transcribed)
        assertEquals(emptySet(), settings.settings.value.imageDisclosureProviders)
        assertEquals("Text of 1-6.", vm.state.text)
    }

    @Test
    fun anAutoReadOfAPdfWithTextOnEveryPageIsTheTextReadAndAsksNothing() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        val reads = sources.pdfReads.size

        vm.onAction(SmartExtractAction.ApplyPdfPages)

        assertNull(vm.pdf.readConfirmation)
        assertEquals(reads + 1, sources.pdfReads.size)
        assertEquals(emptyList(), reading.transcribed)
        assertFalse(vm.pdf.transcribed)
    }

    @Test
    fun readPagesWithAiSendsEveryPageAtTheChosenQuality() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.SetPdfQuality(PdfQuality.High))
        vm.onAction(SmartExtractAction.PdfPagesChanged("2-3"))

        vm.readAndAgree(PdfReadMode.ReadWithAi)

        assertEquals(listOf(2, 3), reading.transcribed.map { it.page })
        assertEquals(setOf(PdfQuality.High), reading.transcribed.map { it.quality }.toSet())
        assertEquals("Transcription of page 2.\n\nTranscription of page 3.", vm.state.text)
        assertEquals(PdfReadMode.ReadWithAi, vm.pdf.mode)
    }

    @Test
    fun theBoxFillsPageByPageAndTheProgressSaysWhereItIs() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        val second = CompletableDeferred<Unit>()
        reading.respond = { page ->
            if (page == 2) second.await()
            PageTranscription.Success("Page $page.")
        }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-3"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        // Page 1 is in; page 2 is out with the model.
        assertEquals("Page 1.", vm.state.text)
        assertEquals(PdfReadState.Running(done = 1, total = 3, page = 2), vm.pdf.readState)
        assertTrue(vm.state.reading)
        assertFalse(vm.state.canGenerate)

        second.complete(Unit)
        assertEquals("Page 1.\n\nPage 2.\n\nPage 3.", vm.state.text)
        assertNull(vm.pdf.readState)
        assertFalse(vm.state.reading)
    }

    @Test
    fun cancellingKeepsTheBoxAsItIsAndStopsTheRequests() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        val hold = CompletableDeferred<Unit>()
        reading.respond = { page ->
            if (page == 3) hold.await()
            PageTranscription.Success("Page $page.")
        }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-5"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)
        assertEquals("Page 1.\n\nPage 2.", vm.state.text)

        vm.onAction(SmartExtractAction.CancelPdfRead)
        hold.complete(Unit)

        assertEquals("Page 1.\n\nPage 2.", vm.state.text)
        assertFalse(vm.state.reading)
        assertNull(vm.pdf.readState)
        assertTrue(vm.pdf.transcribed)
        assertEquals(listOf(1, 2, 3), reading.transcribed.map { it.page })
    }

    @Test
    fun aFailureKeepsThePagesDoneAndResumeGoesOnFromThePageThatFailed() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        reading.respond = { page ->
            if (page == 3) PageTranscription.Failure(PageReadFailure.Ai(AiFailure(AiProblem.RateLimited))) else PageTranscription.Success("Page $page.")
        }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-4"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        val failed = assertIs<PdfReadState.Failed>(vm.pdf.readState)
        assertEquals(3, failed.page)
        assertEquals(2, failed.done)
        assertEquals(4, failed.total)
        assertEquals(AiProblem.RateLimited, assertIs<PageReadFailure.Ai>(failed.failure).failure.problem)
        assertEquals("Page 1.\n\nPage 2.", vm.state.text)
        assertFalse(vm.state.reading)

        reading.respond = { page -> PageTranscription.Success("Page $page.") }
        reading.transcribed.clear()
        vm.onAction(SmartExtractAction.ResumePdfRead)

        assertEquals(listOf(3, 4), reading.transcribed.map { it.page })
        assertEquals("Page 1.\n\nPage 2.\n\nPage 3.\n\nPage 4.", vm.state.text)
        assertNull(vm.pdf.readState)
    }

    @Test
    fun whatWasTranscribedIsNotPaidForAgainWhenThePagesOrTheModeChangeBack() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-2"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)
        assertEquals(2, reading.transcribed.size)

        // Back to the text layer, then to AI again: the same pages are in the cache.
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.Text))
        vm.onAction(SmartExtractAction.ApplyPdfPages)
        assertEquals("Text of 1-2.", vm.state.text)
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.ReadWithAi))
        vm.onAction(SmartExtractAction.ApplyPdfPages)

        assertNull(vm.pdf.readConfirmation)
        assertEquals(2, reading.transcribed.size)
        assertEquals("Transcription of page 1.\n\nTranscription of page 2.", vm.state.text)

        // One more page costs one request, and the confirmation says so.
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-3"))
        vm.onAction(SmartExtractAction.ApplyPdfPages)
        assertEquals(1, vm.pdf.readConfirmation?.requests)
        assertEquals(3, vm.pdf.readConfirmation?.pages)
    }

    @Test
    fun editedTextIsAskedAboutBeforeThePagesAreEvenPlanned() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.TextChanged("Text of 1-6. My own notes."))
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.ReadWithAi))

        vm.onAction(SmartExtractAction.ApplyPdfPages)
        assertTrue(vm.pdf.replaceConfirmation)
        assertNull(vm.pdf.readConfirmation)

        vm.onAction(SmartExtractAction.ConfirmPdfReplace)
        assertFalse(vm.pdf.replaceConfirmation)
        assertEquals(6, vm.pdf.readConfirmation?.requests)
        assertEquals("Text of 1-6. My own notes.", vm.state.text) // still theirs until they agree to the requests
    }

    @Test
    fun blankPagesAreLeftOutAndSaid() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        reading.respond = { page ->
            if (page == 2) PageTranscription.Failure(PageReadFailure.Page(SourceProblem.BlankPage)) else PageTranscription.Success("Page $page.")
        }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-3"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        assertEquals("Page 1.\n\nPage 3.", vm.state.text)
        assertEquals(ExtractMessage.PagesBlank(1), vm.state.message)
        assertNull(vm.pdf.readState)
    }

    @Test
    fun theBoxCanBeEditedAndGeneratedFromOnceTheReadIsDone() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.PdfPagesChanged("1"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        vm.onAction(SmartExtractAction.TextChanged("Transcription of page 1, corrected."))
        assertEquals("Transcription of page 1, corrected.", vm.state.text)
        assertTrue(vm.state.canGenerate)
    }

    @Test
    fun clearingTheBoxDuringAReadStopsIt() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        val hold = CompletableDeferred<Unit>()
        reading.respond = { page ->
            if (page == 2) hold.await()
            PageTranscription.Success("Page $page.")
        }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-4"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        vm.onAction(SmartExtractAction.ClearText)
        hold.complete(Unit)

        assertEquals("", vm.state.text)
        assertNull(vm.state.pdf)
        assertFalse(vm.state.reading)
        assertEquals(listOf(1, 2), reading.transcribed.map { it.page })
    }

    @Test
    fun aPageProblemThatIsNotABlankPageStopsTheReadWithItsOwnReason() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        reading.respond = { PageTranscription.Failure(PageReadFailure.Page(SourceProblem.Encrypted)) }
        vm.onAction(SmartExtractAction.PdfPagesChanged("1-2"))
        vm.readAndAgree(PdfReadMode.ReadWithAi)

        assertEquals(SourceProblem.Encrypted, assertIs<PageReadFailure.Page>(assertIs<PdfReadState.Failed>(vm.pdf.readState).failure).problem)
    }

    @Test
    fun aTextLayerThatCannotBePlannedShowsItsReason() = runTest {
        setUpProvider()
        val vm = viewModel().open()
        sources.pageTextLayers = { _, _ -> PdfPageTextsResult.Failure(SourceProblem.Encrypted) }

        vm.onAction(SmartExtractAction.ApplyPdfPages)

        assertEquals(SourceProblem.Encrypted, vm.state.sourceProblem)
        assertFalse(vm.state.reading)
        assertNull(vm.pdf.readConfirmation)
    }
}
