package com.yahyafati.mnemo.feature.create

import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.SourceInput
import com.yahyafati.mnemo.core.model.SourceProblem
import com.yahyafati.mnemo.core.model.SourceResult
import com.yahyafati.mnemo.core.model.SourceSection
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Smart Extract's link sections (docs/web/ROADMAP.md, W4): a page's sections can be chosen, and the box follows. */
class SmartExtractSectionsTest : PlatformTest() {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sources = FakeSourceRepository()

    private val parts = listOf(
        Triple(null, 0, "The amygdala is a small structure."),
        Triple("Structure", 2, "## Structure\n\nIt has many nuclei."),
        Triple("Hemispheric specializations", 3, "### Hemispheric specializations\n\nLeft and right differ."),
        Triple("Function", 2, "## Function\n\nIt processes fear."),
    )

    private val page: SourceText = run {
        var text = ""
        val sections = parts.mapIndexed { id, (title, level, body) ->
            if (text.isNotEmpty()) text += "\n\n"
            val start = text.length
            text += body
            SourceSection(id, title, level, start, text.length)
        }
        SourceText(text, title = "Amygdala", sections = sections)
    }

    private fun body(vararg ids: Int) = ids.joinToString("\n\n") { parts[it].third }

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

    private fun SmartExtractViewModel.read(link: String, result: SourceResult = SourceResult.Success(page)): SmartExtractViewModel {
        sources.results[SourceInput.Link(link.trim())] = result
        onAction(SmartExtractAction.SelectSource(SourceKind.Link))
        onAction(SmartExtractAction.LinkChanged(link))
        onAction(SmartExtractAction.FetchLink)
        return this
    }

    @Test
    fun aPageWithSectionsFillsTheBoxWithAllOfThem() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala")

        assertEquals(page.text, vm.state.text)
        assertEquals("Amygdala", vm.state.title)
        val sections = assertNotNull(vm.state.sections)
        assertEquals(setOf(0, 1, 2, 3), sections.selected)
        assertEquals(listOf(null, "Structure", "Hemispheric specializations", "Function"), sections.options.map { it.title })
        assertEquals(listOf(0, 2, 3, 2), sections.options.map { it.level })
        assertEquals(SourceText.countWords(parts[1].third), sections.options[1].words)
        assertFalse(vm.state.showSections)
    }

    @Test
    fun aPageWithoutSectionsOrWithOneHasNoPicker() = runTest {
        val vm = viewModel().read("example.com/a", SourceResult.Success(SourceText("Plain.", "Page")))
        assertNull(vm.state.sections)
        assertEquals("Plain.", vm.state.text)

        vm.read("example.com/b", SourceResult.Success(SourceText("Lead only.", "Page", sections = listOf(SourceSection(0, null, 0, 0, 10)))))
        assertNull(vm.state.sections)
    }

    @Test
    fun choosingSectionsRewritesTheBoxInTextOrder() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala")
        vm.onAction(SmartExtractAction.ShowSections)
        assertTrue(vm.state.showSections)

        vm.onAction(SmartExtractAction.ToggleSection(1))
        assertEquals(body(0, 2, 3), vm.state.text)
        assertEquals(setOf(0, 2, 3), vm.state.sections?.selected)
        assertTrue(vm.state.showSections) // the picker stays open while choosing

        vm.onAction(SmartExtractAction.SelectAllSections(false))
        assertEquals("", vm.state.text)
        assertEquals(0, vm.state.requests)
        vm.onAction(SmartExtractAction.ToggleSection(3))
        vm.onAction(SmartExtractAction.ToggleSection(1))
        assertEquals(body(1, 3), vm.state.text)
        assertEquals(SourceText.countWords(body(1, 3)), vm.state.wordCount)

        vm.onAction(SmartExtractAction.SelectAllSections(true))
        assertEquals(page.text, vm.state.text)
        vm.onAction(SmartExtractAction.DismissSections)
        assertFalse(vm.state.showSections)
        assertNotNull(vm.state.sections)
    }

    @Test
    fun aFragmentPreselectsThatSectionAndItsSubsections() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala#Structure")

        assertEquals(setOf(1, 2), vm.state.sections?.selected)
        assertEquals(body(1, 2), vm.state.text)
        assertEquals("Amygdala", vm.state.title)

        // The rest of the article is still there to add.
        vm.onAction(SmartExtractAction.ToggleSection(3))
        assertEquals(body(1, 2, 3), vm.state.text)
    }

    @Test
    fun aFragmentThatNamesNoSectionKeepsThemAll() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala#Nowhere")
        assertEquals(setOf(0, 1, 2, 3), vm.state.sections?.selected)
        assertEquals(page.text, vm.state.text)
    }

    @Test
    fun editedTextIsKeptUntilTheUserAgreesToReplaceIt() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala")
        val edited = page.text + "\n\nMy own note."
        vm.onAction(SmartExtractAction.TextChanged(edited))

        vm.onAction(SmartExtractAction.ToggleSection(3))
        assertTrue(vm.state.sectionsConfirmation)
        assertEquals(edited, vm.state.text)
        assertEquals(setOf(0, 1, 2, 3), vm.state.sections?.selected)

        vm.onAction(SmartExtractAction.CancelSectionReplace)
        assertFalse(vm.state.sectionsConfirmation)
        assertEquals(edited, vm.state.text)

        vm.onAction(SmartExtractAction.ToggleSection(3))
        vm.onAction(SmartExtractAction.ConfirmSectionReplace)
        assertFalse(vm.state.sectionsConfirmation)
        assertEquals(body(0, 1, 2), vm.state.text)
        assertEquals(setOf(0, 1, 2), vm.state.sections?.selected)

        // Back to the sections' own text: nothing of the user's is left to ask about.
        vm.onAction(SmartExtractAction.ToggleSection(3))
        assertFalse(vm.state.sectionsConfirmation)
        assertEquals(page.text, vm.state.text)
    }

    @Test
    fun anotherSourceOrClearingForgetsTheSections() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala")
        vm.onAction(SmartExtractAction.ShowSections)

        vm.read("example.com/plain", SourceResult.Success(SourceText("Plain.", "Page")))
        assertNull(vm.state.sections)
        assertFalse(vm.state.showSections)
        assertEquals("Plain.", vm.state.text)

        vm.read("https://en.wikipedia.org/wiki/Amygdala")
        assertNotNull(vm.state.sections)
        vm.onAction(SmartExtractAction.ClearText)
        assertNull(vm.state.sections)
        assertEquals("", vm.state.text)
    }

    @Test
    fun aFailedReadKeepsWhatWasThere() = runTest {
        val vm = viewModel().read("https://en.wikipedia.org/wiki/Amygdala")
        vm.read("https://en.wikipedia.org/wiki/Missing", SourceResult.Failure(SourceProblem.HttpError))

        assertEquals(SourceProblem.HttpError, vm.state.sourceProblem)
        assertEquals(page.text, vm.state.text)
        assertEquals(setOf(0, 1, 2, 3), vm.state.sections?.selected)
    }
}
