package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.PageRangeError
import com.yahyafati.mnemo.core.model.PageRanges
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceText
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Smart Extract's PDF pages (docs/pdf/ROADMAP.md, P1): the Pages field chooses what the box holds. */
class SmartExtractPdfTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sources = FakeSourceRepository()

    private fun viewModel() = SmartExtractViewModel(
        aiProviders = FakeAiProviderRepository(),
        deckRepository = FakeDeckRepository(),
        sources = sources,
        generateCards = GenerateCardsUseCase(FakeCardGenerationRepository(), FakeCardRepository()),
        regenerateCard = RegenerateCardUseCase(FakeCardGenerationRepository(), FakeCardRepository()),
        acceptCards = AcceptGeneratedCardsUseCase(FakeCardRepository()),
        bookHandoff = BookHandoff(),
    )

    private val SmartExtractViewModel.state get() = uiState.value

    private fun handle(id: String = "1", pages: Int = 40, title: String? = "Cell biology") = PdfHandle(id, PdfInfo(pages, title))

    private fun SmartExtractViewModel.open(location: String = "content://doc/1", opened: PdfHandle = handle()): SmartExtractViewModel {
        sources.pdfs[location] = PdfOpenResult.Success(opened)
        onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        onAction(SmartExtractAction.PdfPicked(location))
        return this
    }

    private fun SmartExtractViewModel.apply(field: String) {
        onAction(SmartExtractAction.PdfPagesChanged(field))
        onAction(SmartExtractAction.ApplyPdfPages)
    }

    private val SmartExtractViewModel.lastRead get() = sources.pdfReads.last().second

    @Test
    fun aPdfOfThreeHundredPagesOrFewerIsReadWholeWithAnEmptyField() = runTest {
        val vm = viewModel().open(opened = handle(pages = 300))

        val pdf = assertNotNull(vm.state.pdf)
        assertEquals("", pdf.pages)
        assertEquals(300, pdf.pageCount)
        assertNull(pdf.error)
        assertEquals(PageRanges.all(300), vm.lastRead)
        assertEquals("Text of 1-300.", vm.state.text)
        assertEquals("Cell biology", vm.state.title)
        assertFalse(vm.state.reading)
    }

    @Test
    fun aLongerPdfStartsWithItsFirstThreeHundredPagesInTheField() = runTest {
        val vm = viewModel().open(opened = handle(pages = 600))

        assertEquals("1-300", vm.state.pdf?.pages)
        assertEquals(PageRanges.all(300), vm.lastRead)

        // Page 450 of it can be read.
        vm.apply("450")
        assertEquals(PageRanges.of(450), vm.lastRead)
        assertEquals("Text of 450.", vm.state.text)
    }

    @Test
    fun applyingPagesReadsExactlyThose() = runTest {
        val vm = viewModel().open()
        vm.apply(" 1-10, 14 ,20- ")

        assertEquals(PageRanges.of((1..10).toList() + 14 + (20..40).toList()), vm.lastRead)
        assertEquals("Text of 1-10, 14, 20-40.", vm.state.text)
        assertEquals(" 1-10, 14 ,20- ", vm.state.pdf?.pages) // the field is the user's own
    }

    @Test
    fun aFieldWithAnErrorIsNotReadAndTheBoxStays() = runTest {
        val vm = viewModel().open()
        val reads = sources.pdfReads.size

        vm.onAction(SmartExtractAction.PdfPagesChanged("1-x"))
        assertEquals(PdfPagesError.Invalid(PageRangeError.Malformed(2)), vm.state.pdf?.error)
        vm.onAction(SmartExtractAction.ApplyPdfPages)
        vm.onAction(SmartExtractAction.PdfPagesChanged("41"))
        assertEquals(PdfPagesError.Invalid(PageRangeError.OutOfRange(41)), vm.state.pdf?.error)
        vm.onAction(SmartExtractAction.PdfPagesChanged("9-3"))
        assertEquals(PdfPagesError.Invalid(PageRangeError.Reversed(9, 3)), vm.state.pdf?.error)
        vm.onAction(SmartExtractAction.ApplyPdfPages)

        assertEquals(reads, sources.pdfReads.size)
        assertEquals("Text of 1-40.", vm.state.text)

        // Fixing the field clears the error.
        vm.onAction(SmartExtractAction.PdfPagesChanged("3-9"))
        assertNull(vm.state.pdf?.error)
    }

    @Test
    fun moreThanThreeHundredPagesIsAnErrorNotASilentCut() = runTest {
        val vm = viewModel().open(opened = handle(pages = 600))
        val reads = sources.pdfReads.size

        vm.apply("1-400")
        assertEquals(PdfPagesError.TooMany(selected = 400, limit = 300), vm.state.pdf?.error)
        // An empty field is every page, which is the same thing for this PDF.
        vm.apply("")
        assertEquals(PdfPagesError.TooMany(selected = 600, limit = 300), vm.state.pdf?.error)

        assertEquals(reads, sources.pdfReads.size)
        vm.apply("301-600")
        assertNull(vm.state.pdf?.error)
        assertEquals(PageRanges.of((301..600).toList()), vm.lastRead)
    }

    @Test
    fun editedTextIsNotReplacedWithoutAsking() = runTest {
        val vm = viewModel().open()
        vm.onAction(SmartExtractAction.TextChanged("Text of 1-40. My own notes."))
        val reads = sources.pdfReads.size

        vm.apply("5-6")
        assertTrue(vm.state.pdf?.replaceConfirmation == true)
        assertEquals("Text of 1-40. My own notes.", vm.state.text)
        assertEquals(reads, sources.pdfReads.size)

        vm.onAction(SmartExtractAction.CancelPdfReplace)
        assertFalse(vm.state.pdf?.replaceConfirmation == true)
        assertEquals("Text of 1-40. My own notes.", vm.state.text)
        assertEquals("5-6", vm.state.pdf?.pages)

        vm.onAction(SmartExtractAction.ApplyPdfPages)
        vm.onAction(SmartExtractAction.ConfirmPdfReplace)
        assertFalse(vm.state.pdf?.replaceConfirmation == true)
        assertEquals("Text of 5-6.", vm.state.text)
        assertEquals(PageRanges.of(5, 6), vm.lastRead)
    }

    @Test
    fun textThatWasNotEditedOrIsGoneIsReplacedAtOnce() = runTest {
        val vm = viewModel().open()
        vm.apply("2-3")
        assertEquals("Text of 2-3.", vm.state.text)
        assertFalse(vm.state.pdf?.replaceConfirmation == true)

        // Nothing is lost when the box is empty.
        vm.onAction(SmartExtractAction.TextChanged(""))
        vm.apply("4")
        assertEquals("Text of 4.", vm.state.text)
    }

    @Test
    fun pagesWithoutTextAreSaidAndKeepTheBox() = runTest {
        val vm = viewModel().open()
        sources.pdfText = { _, _ -> SourceResult.Failure(SourceProblem.NoText) }

        vm.apply("5-6")
        assertEquals(SourceProblem.NoText, vm.state.sourceProblem)
        assertEquals("Text of 1-40.", vm.state.text)
        assertFalse(vm.state.reading)
        assertNotNull(vm.state.pdf)
    }

    @Test
    fun aPdfThatCannotBeOpenedFailsWithAReasonAndKeepsTheOneBefore() = runTest {
        val vm = viewModel().open()
        sources.pdfs["content://doc/2"] = PdfOpenResult.Failure(SourceProblem.Encrypted)

        vm.onAction(SmartExtractAction.PdfPicked("content://doc/2"))
        assertEquals(SourceProblem.Encrypted, vm.state.sourceProblem)
        assertFalse(vm.state.reading)
        assertEquals("Text of 1-40.", vm.state.text)
        assertEquals(40, vm.state.pdf?.pageCount)
        assertTrue(sources.closedPdfs.isEmpty())
    }

    @Test
    fun anotherPdfReplacesAndClosesTheFirst() = runTest {
        val first = handle("1")
        val vm = viewModel().open("content://doc/1", first)
        sources.pdfs["content://doc/2"] = PdfOpenResult.Success(handle("2", pages = 7, title = "Genetics"))

        vm.onAction(SmartExtractAction.PdfPicked("content://doc/2"))
        assertEquals(listOf(first), sources.closedPdfs)
        assertEquals(7, vm.state.pdf?.pageCount)
        assertEquals("Genetics", vm.state.title)
        assertEquals("Text of 1-7.", vm.state.text)

        vm.apply("2")
        assertEquals("2", sources.pdfReads.last().first.id.let { "2" })
        assertEquals("2", sources.pdfReads.last().first.id)
    }

    @Test
    fun clearingTheTextClosesThePdf() = runTest {
        val opened = handle()
        val vm = viewModel().open(opened = opened)

        vm.onAction(SmartExtractAction.ClearText)
        assertNull(vm.state.pdf)
        assertEquals(listOf(opened), sources.closedPdfs)
        assertEquals("", vm.state.text)

        // Nothing is left to apply.
        val reads = sources.pdfReads.size
        vm.onAction(SmartExtractAction.ApplyPdfPages)
        assertEquals(reads, sources.pdfReads.size)
    }

    @Test
    fun aLinkReplacesAndClosesThePdfOnlyOnceItIsRead() = runTest {
        val opened = handle()
        val vm = viewModel().open(opened = opened)
        sources.results[SourceInput.Link("example.com/a")] = SourceResult.Failure(SourceProblem.Unreachable)
        sources.results[SourceInput.Link("example.com/b")] = SourceResult.Success(SourceText("From the web.", "Page"))

        vm.onAction(SmartExtractAction.LinkChanged("example.com/a"))
        vm.onAction(SmartExtractAction.FetchLink)
        assertNotNull(vm.state.pdf)
        assertTrue(sources.closedPdfs.isEmpty())

        vm.onAction(SmartExtractAction.LinkChanged("example.com/b"))
        vm.onAction(SmartExtractAction.FetchLink)
        assertNull(vm.state.pdf)
        assertEquals(listOf(opened), sources.closedPdfs)
        assertEquals("From the web.", vm.state.text)
    }

    @Test
    fun aDroppedPdfIsOpenedLikeAPickedOne() = runTest {
        val vm = viewModel()
        sources.pdfs["/home/me/Lecture 3.pdf"] = PdfOpenResult.Success(handle(pages = 12, title = "Lecture 3"))

        vm.onAction(SmartExtractAction.FileDropped("/home/me/Lecture 3.pdf"))
        assertEquals(SourceKind.Pdf, vm.state.sourceKind)
        assertEquals(12, vm.state.pdf?.pageCount)
        assertEquals("Text of 1-12.", vm.state.text)
        assertIs<PdfSummary>(vm.state.pdf)
    }
}
