package com.yahyafati.mnemo

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.core.data.repository.CardRepository
import com.yahyafati.mnemo.core.data.repository.DeckRepository
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.di.TestMnemoApplication
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.core.component.inject
import org.koin.test.KoinTest

/** The card browser is reachable from the library, lists cards, and opens them in the editor. */
@Config(application = TestMnemoApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class BrowseFlowTest : KoinTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val deckRepository: DeckRepository by inject()

    private val cardRepository: CardRepository by inject()

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
