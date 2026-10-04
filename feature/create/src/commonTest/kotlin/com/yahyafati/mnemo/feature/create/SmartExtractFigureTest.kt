package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.FigureSide
import com.yahyafati.mnemo.core.model.MediaRef
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.PageRegion
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.model.SourceProblem
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Figures on queued cards (docs/pdf/ROADMAP.md, P7): opening the crop screen, cutting, changing, removing, and what accepting stores. */
class SmartExtractFigureTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val providers = FakeAiProviderRepository()
    private val sources = FakeSourceRepository()
    private val generation = FakeCardGenerationRepository()
    private val decks = FakeDeckRepository()
    private val cards = FakeCardRepository()
    private val media = FakeMediaRepository()
    private val settings = FakeUserSettingsRepository()
    private val deckId = runBlocking { decks.saveDeck("Biology") }

    private val handle = PdfHandle("1", PdfInfo(6, "Slides"))
    private val region = PageRegion(0.2f, 0.25f, 0.7f, 0.8f)

    private fun viewModel(): SmartExtractViewModel {
        providers.addProvider(
            AiProvider(
                id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "vision-model",
                disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
            listOf(AiModel("p", "vision-model", AiCapabilities(vision = true))),
        )
        return SmartExtractViewModel(
            aiProviders = providers,
            deckRepository = decks,
            sources = sources,
            generateCards = GenerateCardsUseCase(generation, cards),
            regenerateCard = RegenerateCardUseCase(generation, cards),
            acceptCards = AcceptGeneratedCardsUseCase(cards, media),
            bookHandoff = BookHandoff(),
            userSettings = settings,
            readPdfPages = ReadPdfPagesUseCase(sources, FakePdfReadRepository()),
        )
    }

    private val SmartExtractViewModel.state get() = uiState.value

    private fun figureFile(name: String = "fig-p2.png", content: String = "figure"): File =
        File(kotlin.io.path.createTempDirectory("fig").toFile(), name).also { it.writeText(content) }

    /** A PDF open in the image mode with a card from page 2 in the queue: "c0", and "c1" from page 3 with no page named when [pageless]. */
    private fun withQueue(pageless: Boolean = false, kind: NoteKind = NoteKind.Basic): SmartExtractViewModel {
        sources.pdfs["content://doc/1"] = PdfOpenResult.Success(handle)
        sources.pdfCrop = { _, page, r -> PdfPageResult.Success(figureFile("fig-p$page-${r.key}.png")) }
        generation.respond = {
            flowOf(
                GenerationUpdate.Card(card("What is the organelle?", "Mitochondrion", kind = kind).copy(id = "c0", page = if (pageless) null else 2, wrongAnswers = if (kind == NoteKind.MultipleChoice) listOf("Nucleus") else emptyList())),
                GenerationUpdate.Done(),
            )
        }
        val vm = viewModel()
        vm.onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        vm.onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        vm.onAction(SmartExtractAction.SetPdfReadMode(PdfReadMode.PageImages))
        vm.onAction(SmartExtractAction.TogglePdfPage(2))
        vm.onAction(SmartExtractAction.Generate)
        vm.onAction(SmartExtractAction.ConfirmPdfRead)
        while (vm.state.disclosure != null) vm.onAction(SmartExtractAction.AcceptDisclosure)
        vm.onAction(SmartExtractAction.SelectDeck(deckId))
        assertEquals(listOf("c0"), vm.state.queue.map { it.card.id })
        return vm
    }

    @Test
    fun addFigureOpensTheCropScreenOnTheCardsPageWithABoxToStartFrom() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.OpenPdfPage(2))

        vm.onAction(SmartExtractAction.AddFigure("c0"))

        val edit = assertNotNull(vm.state.figureEdit)
        assertEquals("c0", edit.cardId)
        assertEquals(2, edit.page)
        assertEquals(PageRegion.inset(SmartExtractViewModel.FIGURE_START_INSET), edit.region)
        assertEquals(FigureSide.Front, edit.side)
        assertEquals(listOf(FigureSide.Front, FigureSide.Back), edit.sides)
        assertFalse(edit.hasFigure)
        assertNull(vm.state.pdf?.viewPage, "the crop screen takes the place of the page viewer")

        vm.onAction(SmartExtractAction.CloseFigure)
        assertNull(vm.state.figureEdit)
    }

    @Test
    fun aCardThatNamesNoPageOrWhosePdfIsClosedTakesNoFigure() = runTest {
        val pageless = withQueue(pageless = true)
        pageless.onAction(SmartExtractAction.AddFigure("c0"))
        assertNull(pageless.state.figureEdit)

        val closed = withQueue()
        closed.onAction(SmartExtractAction.ClearText)
        closed.onAction(SmartExtractAction.AddFigure("c0"))
        assertNull(closed.state.figureEdit)
        closed.onAction(SmartExtractAction.AddFigure("nobody"))
        assertNull(closed.state.figureEdit)
    }

    @Test
    fun savingTheCropCutsTheRegionFromThePageAndPutsItOnTheCard() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))

        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Back))

        assertEquals(listOf(Triple(handle, 2, region)), sources.croppedPages)
        assertNull(vm.state.figureEdit)
        val figure = assertNotNull(vm.state.queue.single().figure)
        assertEquals(2, figure.page)
        assertEquals(region, figure.region)
        assertEquals(FigureSide.Back, figure.side)
        assertEquals("fig-p2-200-250-700-800.png", figure.file.name)
    }

    @Test
    fun aCropWithNothingOnItKeepsTheScreenOpenAndSaysSo() = runTest {
        val vm = withQueue()
        sources.pdfCrop = { _, _, _ -> PdfPageResult.Failure(SourceProblem.BlankPage) }
        vm.onAction(SmartExtractAction.AddFigure("c0"))

        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        val edit = assertNotNull(vm.state.figureEdit)
        assertEquals(SourceProblem.BlankPage, edit.problem)
        assertFalse(edit.saving)
        assertNull(vm.state.queue.single().figure)

        // Another try that works closes it.
        sources.pdfCrop = { _, _, _ -> PdfPageResult.Success(figureFile()) }
        vm.onAction(SmartExtractAction.SaveFigure(PageRegion.Full, FigureSide.Front))
        assertNull(vm.state.figureEdit)
        assertNotNull(vm.state.queue.single().figure)
    }

    @Test
    fun aMultipleChoiceCardTakesItsFigureOnTheFrontOnly() = runTest {
        val vm = withQueue(kind = NoteKind.MultipleChoice)
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        assertEquals(listOf(FigureSide.Front), assertNotNull(vm.state.figureEdit).sides)

        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Back))

        assertEquals(emptyList(), sources.croppedPages, "a picture can't be the right option")
        assertNotNull(vm.state.figureEdit)
        assertNull(vm.state.queue.single().figure)
    }

    @Test
    fun changingAFigureStartsFromItsCropAndRemovingItTakesItOff() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Back))

        vm.onAction(SmartExtractAction.AddFigure("c0"))
        val edit = assertNotNull(vm.state.figureEdit)
        assertEquals(region, edit.region)
        assertEquals(FigureSide.Back, edit.side)
        assertTrue(edit.hasFigure)

        vm.onAction(SmartExtractAction.CloseFigure)
        vm.onAction(SmartExtractAction.RemoveFigure("c0"))
        assertNull(vm.state.queue.single().figure)
        assertEquals(1, sources.croppedPages.size)
    }

    @Test
    fun acceptingStoresTheFigureAndWritesItsReferenceOnTheChosenSide() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        vm.onAction(SmartExtractAction.Accept("c0"))

        val stored = media.stored.values.single()
        val note = cards.notes.value.values.single()
        assertEquals(listOf("What is the organelle?\n\n![](${MediaRef.of(stored.id)})", "Mitochondrion"), note.fields)
        assertEquals(ExtractMessage.Accepted(1, "Biology"), vm.state.message)
        assertTrue(vm.state.queue.isEmpty())
    }

    @Test
    fun aFigureThatIsGoneWhenAcceptingIsSaidSoAndTheCardIsStillAdded() = runTest {
        val vm = withQueue()
        sources.pdfCrop = { _, _, _ -> PdfPageResult.Success(File("never-written.png")) }
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        vm.onAction(SmartExtractAction.Accept("c0"))

        assertEquals(ExtractMessage.Accepted(1, "Biology", missingFigures = 1), vm.state.message)
        assertEquals(listOf("What is the organelle?", "Mitochondrion"), cards.notes.value.values.single().fields)
        assertTrue(media.stored.isEmpty())
    }

    @Test
    fun discardingACardStoresNothing() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        vm.onAction(SmartExtractAction.Discard("c0"))

        assertTrue(vm.state.queue.isEmpty())
        assertTrue(media.stored.isEmpty())
        assertTrue(cards.notes.value.isEmpty())
    }

    @Test
    fun closingThePdfTakesTheFiguresOffTheCardsWhoseFilesWentWithIt() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        vm.onAction(SmartExtractAction.ClearText)

        assertNull(vm.state.queue.single().figure)
        assertEquals(ExtractMessage.FiguresRemoved(1), vm.state.message)
        assertEquals(listOf(handle), sources.closedPdfs)
    }

    @Test
    fun closingThePdfWhileTheCropScreenIsOpenClosesIt() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))

        vm.onAction(SmartExtractAction.ClearText)

        assertNull(vm.state.figureEdit)
        assertNull(vm.state.message, "no figure was lost")
    }

    @Test
    fun aRegeneratedCardIsANewCardWithoutTheFigure() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))
        generation.respond = { flowOf(GenerationUpdate.Card(card("A different question", "Another answer").copy(id = "new", page = 2)), GenerationUpdate.Done()) }

        vm.onAction(SmartExtractAction.Regenerate("c0"))

        val replaced = vm.state.queue.single()
        assertEquals("new", replaced.card.id)
        assertNull(replaced.figure)
    }

    @Test
    fun editingACardKeepsItsFigure() = runTest {
        val vm = withQueue()
        vm.onAction(SmartExtractAction.AddFigure("c0"))
        vm.onAction(SmartExtractAction.SaveFigure(region, FigureSide.Front))

        vm.onAction(SmartExtractAction.EditFront("c0", "Which organelle makes ATP?"))

        assertNotNull(vm.state.queue.single().figure)
    }
}
