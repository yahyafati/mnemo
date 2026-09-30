package com.yahyafati.mnemo.feature.study

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.AiProvider
import com.yahyafati.mnemo.core.model.Rating
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The study flow through the real screen and ViewModel: flip, rate by button and by swipe, undo, finish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StudyScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = StudyTestFixture().apply {
        addBasic("What do mitochondria make?", "ATP")
        addBasic("Largest organ?", "Skin")
        addBasic("Unit of heredity?", "Gene")
    }

    private fun setContent(viewModel: StudyViewModel) = composeRule.setContent {
        MnemoTheme {
            StudyScreen(onEditNote = {}, onDone = {}, doneLabel = "Done", viewModel = viewModel, assistViewModel = fixture.assistViewModel())
        }
    }

    @Test
    fun studyFlow() {
        val viewModel = fixture.viewModel()
        setContent(viewModel)

        composeRule.onNodeWithText("Card 1 of 3").assertExists()
        composeRule.onNodeWithText("What do mitochondria make?").assertExists()
        composeRule.onNodeWithText("ATP").assertDoesNotExist()

        // Tap the card to flip it, then answer with a button.
        composeRule.onNodeWithText("What do mitochondria make?").performClick()
        composeRule.onNodeWithText("ATP").assertExists()
        composeRule.onNodeWithText("Good").performClick()

        // Good on a new card only moves it to the 10-minute learning step, so it stays in the session.
        composeRule.onNodeWithText("Card 2 of 4").assertExists()
        composeRule.onNodeWithText("Largest organ?").assertExists()

        // Undo goes back to the first card, question side up.
        composeRule.onNodeWithContentDescription("Undo").performClick()
        composeRule.onNodeWithText("What do mitochondria make?").assertExists()
        composeRule.onNodeWithText("Card 1 of 3").assertExists()
        composeRule.onNodeWithText("Show answer").performClick()
        composeRule.onNodeWithText("Easy").performClick()

        // Swipe right is Good, swipe left is Again.
        composeRule.onNodeWithText("Show answer").performClick()
        composeRule.onNodeWithText("Largest organ?").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Show answer").performClick()
        composeRule.onNodeWithText("Unit of heredity?").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        val ratings = fixture.reviews.logs.value.map { it.rating }
        assertEquals(listOf(Rating.Easy, Rating.Good, Rating.Again), ratings)
    }

    @Test
    fun finishedSessionShowsTheSummary() {
        val viewModel = fixture.viewModel()
        setContent(viewModel)
        repeat(3) {
            composeRule.onNodeWithText("Show answer").performClick()
            composeRule.onNodeWithText("Easy").performClick()
        }
        composeRule.onNodeWithText("Session complete").assertExists()
        composeRule.onNodeWithText("100%").assertExists()
    }

    @Test
    fun aiAssistShowsOnceTheAnswerIsShowing() {
        val viewModel = fixture.viewModel()
        setContent(viewModel)
        // No provider: no AI button, even with the answer showing.
        composeRule.onNodeWithText("Show answer").performClick()
        composeRule.onNodeWithContentDescription("Ask AI about this card").assertDoesNotExist()

        fixture.aiProviders.addProvider(
            AiProvider(
                id = "p", name = "Groq", baseUrl = "https://api.groq.com/openai/v1", defaultModel = "llama-3.3-70b",
                disclosureAcceptedAt = Instant.EPOCH, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
        composeRule.onNodeWithContentDescription("Ask AI about this card").performClick()
        composeRule.onNodeWithText("Rewrite this card").assertExists()
        composeRule.onNodeWithText("Explain this").performClick()
        composeRule.onNodeWithText("An explanation.").assertExists()
        assertEquals("What do mitochondria make?", fixture.assist.explained.single().second.note.fields[0])
    }

    /**
     * Accessibility (ROADMAP Phase 6): every control is labelled for TalkBack and at least 48dp to
     * touch, and the swipe answers are offered as accessibility actions too.
     */
    @Test
    fun controlsAreLabelledLargeEnoughAndSwipesHaveActions() {
        val viewModel = fixture.viewModel()
        setContent(viewModel)
        composeRule.onNodeWithText("Show answer").performClick()

        val minTouch = with(composeRule.density) { 48.dp.toPx() } - 0.5f
        composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue(!label.isNullOrBlank(), "Unlabelled control: ${node.config}")
            val touch = node.touchBoundsInRoot
            assertTrue(touch.width >= minTouch && touch.height >= minTouch, "Touch target of \"$label\" is ${touch.width}x${touch.height}px")
        }

        val card = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes().single()
        val actions = card.config[SemanticsActions.CustomActions]
        assertEquals(listOf("Answer Again", "Answer Good"), actions.map { it.label })
        composeRule.runOnIdle { actions.first { it.label == "Answer Good" }.action() }
        composeRule.waitForIdle()
        assertEquals(listOf(Rating.Good), fixture.reviews.logs.value.map { it.rating })
    }

    /** At the largest system font size the answer controls stay on screen. */
    @Test
    fun largeFontsKeepTheControlsVisible() {
        RuntimeEnvironment.setFontScale(2f)
        val viewModel = fixture.viewModel()
        setContent(viewModel)
        composeRule.onNodeWithText("Show answer").assertIsDisplayed().performClick()
        listOf("Again", "Hard", "Good", "Easy").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun readAloudNeedsTextToSpeechOrASoundOnTheCard() {
        // A platform without text-to-speech: a card with no sound of its own has nothing to play.
        composeRule.setContent {
            CompositionLocalProvider(LocalPlatformCapabilities provides PlatformCapabilities(textToSpeech = false)) {
                MnemoTheme {
                    StudyScreen(onEditNote = {}, onDone = {}, doneLabel = "Done", viewModel = fixture.viewModel(), assistViewModel = fixture.assistViewModel())
                }
            }
        }
        composeRule.onNodeWithText("What do mitochondria make?").assertExists()
        composeRule.onNodeWithContentDescription("Play audio or read aloud").assertDoesNotExist()
    }

    @Test
    fun readAloudShowsWhereThePlatformHasTextToSpeech() {
        setContent(fixture.viewModel())
        composeRule.onNodeWithContentDescription("Play audio or read aloud").assertExists()
    }
}
