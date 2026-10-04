package com.yahyafati.mnemo.feature.create

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.data.repository.GenerationUpdate
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.domain.AcceptGeneratedCardsUseCase
import com.yahyafati.mnemo.core.domain.GenerateCardsUseCase
import com.yahyafati.mnemo.core.domain.ReadPdfPagesUseCase
import com.yahyafati.mnemo.core.domain.RegenerateCardUseCase
import com.yahyafati.mnemo.core.model.AiCapabilities
import com.yahyafati.mnemo.core.model.AiModel
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.PdfHandle
import com.yahyafati.mnemo.core.model.PdfInfo
import com.yahyafati.mnemo.core.model.PdfOpenResult
import com.yahyafati.mnemo.core.model.PdfPageResult
import com.yahyafati.mnemo.core.model.PdfReadMode
import com.yahyafati.mnemo.core.testing.repository.FakeAiProviderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository
import com.yahyafati.mnemo.core.testing.repository.FakeCardGenerationRepository.Companion.card
import com.yahyafati.mnemo.core.testing.repository.FakeCardRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeMediaRepository
import com.yahyafati.mnemo.core.testing.repository.FakePdfReadRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSourceRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import com.yahyafati.mnemo.core.ui.card.LocalMediaImageLoader
import com.yahyafati.mnemo.core.ui.card.MediaImageLoader
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Cards from page images through the real screen and ViewModel (docs/pdf/ROADMAP.md, P6): tick pages, confirm, review, open a card's page. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Tall, so the whole workshop is on screen: a click on a page of the grid needs it in view, and the grid scrolls on its own.
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class SmartExtractPageImagesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val providers = FakeAiProviderRepository().apply {
        addProvider(
            AiProvider(
                id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "vision-model",
                disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
            listOf(AiModel("p", "vision-model", AiCapabilities(vision = true))),
        )
    }
    private val generation = FakeCardGenerationRepository()
    private val cards = FakeCardRepository()
    private val sources = FakeSourceRepository()
    private val settings = FakeUserSettingsRepository()

    private val viewModel = run {
        sources.pdfs["content://doc/1"] = PdfOpenResult.Success(PdfHandle("1", PdfInfo(6, "Slides")))
        sources.pdfPage = { _, page -> PdfPageResult.Success(File("p$page.jpg")) }
        sources.pdfCrop = { _, page, region -> PdfPageResult.Success(File("fig-p$page-${region.key}.png")) }
        runBlocking { settings.setPdfReadOptions(PdfReadMode.PageImages, com.yahyafati.mnemo.core.model.PdfQuality.Standard) }
        SmartExtractViewModel(
            providers, FakeDeckRepository(), sources,
            GenerateCardsUseCase(generation, cards), RegenerateCardUseCase(generation, cards), AcceptGeneratedCardsUseCase(cards, FakeMediaRepository()), BookHandoff(),
            settings, ReadPdfPagesUseCase(sources, FakePdfReadRepository()),
        )
    }

    /** Draws a grey picture for any page file, as a platform decoder would a JPEG. */
    private val loader = object : MediaImageLoader {
        override fun load(hash: String): ImageBitmap? = null

        override fun loadFile(file: File): ImageBitmap = ImageBitmap(40, 30).also { Canvas(it).drawRect(Rect(0f, 0f, 40f, 30f), Paint()) }
    }

    private fun show() = composeRule.setContent {
        MnemoTheme {
            CompositionLocalProvider(LocalMediaImageLoader provides loader) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SmartExtractScreen(state, viewModel::onAction, onSetUpAi = {}, pageFiles = viewModel.pageFiles)
            }
        }
    }

    @Test
    fun tickPagesConfirmReviewAndOpenTheCardsPage() {
        show()
        viewModel.onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        viewModel.onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        generation.respond = { request ->
            flowOf(GenerationUpdate.Card(card("What is on the slide?", "A cell").copy(id = "c0", chunkIndex = request.part, page = 2)), GenerationUpdate.Done())
        }
        composeRule.waitForIdle()

        // The grid stands where the box is: a picture for each page, nothing ticked, and nothing to generate from yet.
        composeRule.onNodeWithText("No pages ticked. Tick the pages to make cards from.").assertExists()
        composeRule.onAllNodesWithContentDescription("Page 1").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("Source text").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Page 2").performClick()
        composeRule.onNodeWithContentDescription("Page 3").performClick()
        composeRule.onNodeWithText("2 pages ticked · 1 request").assertExists()
        assertEquals(setOf(2, 3), viewModel.uiState.value.pdf?.selectedPages)
        composeRule.onNodeWithText("2-3").assertExists() // the Pages field says the same

        // Nothing is sent until the user has agreed to the request and, once, to the notice about images.
        composeRule.onNodeWithText("Generate ~8 cards").performClick()
        composeRule.onNodeWithText("Make cards from page images?").assertExists()
        assertEquals(0, generation.requests.size)
        composeRule.onNodeWithText("Make cards").performClick()
        composeRule.onNodeWithText("Send to Groq?").assertExists()
        composeRule.onNodeWithText("Continue").performClick()
        assertEquals(listOf(listOf(2, 3)), generation.requests.map { it.pages!!.pages })

        // The card names its page, and the label opens it large.
        composeRule.onNodeWithText("What is on the slide?").assertExists()
        composeRule.onNodeWithText("p. 2").assertExists()
        composeRule.onNodeWithContentDescription("Open page 2").performClick()
        composeRule.onNodeWithText("Page 2 of 6").assertExists()
        composeRule.onNodeWithContentDescription("Page 2 of the PDF").assertExists()

        composeRule.onNodeWithContentDescription("Next page").performClick()
        composeRule.onNodeWithText("Page 3 of 6").assertExists()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Page 3 of 6").assertDoesNotExist()
        assertNull(viewModel.uiState.value.pdf?.viewPage)
    }

    @Test
    fun cutAFigureForACardChangeItAndTakeItOff() {
        show()
        viewModel.onAction(SmartExtractAction.SelectSource(SourceKind.Pdf))
        viewModel.onAction(SmartExtractAction.PdfPicked("content://doc/1"))
        generation.respond = { request ->
            flowOf(GenerationUpdate.Card(card("What is on the slide?", "A cell").copy(id = "c0", chunkIndex = request.part, page = 2)), GenerationUpdate.Done())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Page 2").performClick()
        composeRule.onNodeWithText("Generate ~4 cards").performClick()
        composeRule.onNodeWithText("Make cards").performClick()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("What is on the slide?").assertExists()

        // The card names page 2, so a figure can be cut from it: the crop screen shows the page with its box.
        composeRule.onNodeWithText("Add figure").performClick()
        composeRule.onNodeWithText("Figure from page 2").assertExists()
        composeRule.onNodeWithContentDescription("Page 2 of the PDF").assertExists()
        composeRule.onNodeWithContentDescription("Part of the page to cut out").assertExists()
        composeRule.onNodeWithText("On the front").assertExists()
        composeRule.onNodeWithText("On the back").performClick()
        composeRule.onNodeWithText("Use this crop").performClick()

        // The crop is on the card, shown as it will be saved, on the side that was chosen.
        composeRule.onNodeWithText("Figure from page 2").assertDoesNotExist()
        composeRule.onNodeWithText("Figure on the back").assertExists()
        composeRule.onNodeWithContentDescription("Figure cut from page 2").assertExists()
        assertEquals(com.yahyafati.mnemo.core.model.FigureSide.Back, viewModel.uiState.value.queue.single().figure?.side)
        composeRule.onNodeWithText("Add figure").assertDoesNotExist()

        // Change reopens the screen on the same crop; Remove figure takes it off.
        composeRule.onNodeWithText("Change").performClick()
        composeRule.onNodeWithText("Figure from page 2").assertExists()
        composeRule.onNodeWithContentDescription("Close").performClick()
        composeRule.onNodeWithText("Remove figure").performClick()
        composeRule.onNodeWithText("Figure on the back").assertDoesNotExist()
        assertNull(viewModel.uiState.value.queue.single().figure)
        composeRule.onNodeWithText("Add figure").assertExists()
    }
}
