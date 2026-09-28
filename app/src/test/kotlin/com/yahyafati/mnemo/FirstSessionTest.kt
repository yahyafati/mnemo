package com.yahyafati.mnemo

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * The Phase 1 exit path for a new user, end to end on a real (in-memory) database: a new deck,
 * add a card, study it, finish the session.
 *
 * The deck is created through the repository rather than the New Deck dialog: under Robolectric,
 * any text field inside a Compose `AlertDialog` keeps the test from ever going idle (a harness
 * limitation, not an app loop). `DecksViewModelTest` covers the dialog's logic.
 */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class FirstSessionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var deckRepository: DeckRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun createDeckAddCardAndFinishFirstSession() {
        awaitText("No decks yet")
        runBlocking { deckRepository.saveDeck("Biology") }

        // The new deck offers to add cards.
        awaitText("Biology")
        composeRule.onNodeWithText("Add cards").performClick()

        awaitText("Write a card")
        composeRule.onNode(hasSetTextAction() and hasText("Front")).performTextInput("What do mitochondria make?")
        composeRule.onNode(hasSetTextAction() and hasText("Back")).performTextInput("ATP")
        composeRule.onNodeWithText("Add card").performClick()
        awaitText("Card added")
        composeRule.onNodeWithContentDescription("Close").performClick()

        // Study it.
        awaitText("1 new")
        composeRule.onNodeWithText("Review").performClick()
        awaitText("What do mitochondria make?")
        composeRule.onNodeWithText("Show answer").performClick()
        awaitText("ATP")
        composeRule.onNodeWithText("Easy").performClick()

        awaitText("Session complete")
        composeRule.onNodeWithText("Done").performClick()
        awaitText("Biology")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
