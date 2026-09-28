package com.yahyafati.mnemo.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every tab and Settings is reachable from the app shell. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class MnemoAppNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
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

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
