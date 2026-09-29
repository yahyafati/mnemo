package com.yahyafati.mnemo

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.NoteKind
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

/** The card browser is reachable from the library, lists cards, and opens them in the editor. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class BrowseFlowTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var deckRepository: DeckRepository

    @Inject
    lateinit var cardRepository: CardRepository

    @Before
    fun setUp() = hiltRule.inject()

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun browseFromTheLibraryAndOpenACard() {
        awaitText("No decks yet")
        runBlocking {
            val deck = deckRepository.saveDeck("Biology")
            cardRepository.addNote(deck, NoteKind.Basic, listOf("What do **mitochondria** make?", "ATP"), listOf("cells"))
        }
        awaitText("Biology")
        composeRule.onNodeWithContentDescription("Browse all cards").performClick()

        awaitText("Browse cards")
        awaitText("What do mitochondria make?")
        composeRule.onNodeWithText("1 card").assertExists()

        composeRule.onNodeWithText("What do mitochondria make?").performClick()
        awaitText("Edit note")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
