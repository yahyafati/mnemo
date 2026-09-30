package com.yahyafati.mnemo.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.MainActivity
import com.yahyafati.mnemo.di.TestMnemoApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every tab and Settings is reachable from the app shell. */
@Config(application = TestMnemoApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class MnemoAppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun startsOnDecks() {
        awaitText("No decks yet")
        composeRule.onNodeWithText("Decks").assertIsSelected()
    }

    @Test
    fun everyTabIsReachable() {
        awaitText("No decks yet")
        val tabs = listOf(
            "Study" to "Nothing to study",
            "Create" to "Write a card",
            "Analytics" to "No reviews yet",
            "Decks" to "No decks yet",
        )
        for ((tab, content) in tabs) {
            composeRule.onNodeWithText(tab).performClick()
            awaitText(content)
            composeRule.onNodeWithText(tab).assertIsSelected()
        }
    }

    @Test
    fun settingsOpensFromTopBarAndHidesTabs() {
        awaitText("No decks yet")
        composeRule.onNodeWithText("Study").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        awaitText("Scheduling")
        composeRule.onNodeWithText("Analytics").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        awaitText("Nothing to study")
        composeRule.onNodeWithText("Study").assertIsSelected()
    }

    /** Tablets, unfolded foldables and landscape: the tabs move to a rail, Settings to its top. */
    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun wideWindowsUseANavigationRail() {
        awaitText("No decks yet")
        composeRule.onNodeWithText("Analytics").performClick()
        awaitText("No reviews yet")
        composeRule.onNodeWithText("Analytics").assertIsSelected()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        awaitText("Scheduling")
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
