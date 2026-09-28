package com.yahyafati.mnemo.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every tab and Settings is reachable from the app shell (ROADMAP Phase 0 exit criteria). */
@RunWith(RobolectricTestRunner::class)
class MnemoAppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            MnemoTheme {
                MnemoApp()
            }
        }
    }

    @Test
    fun startsOnDecks() {
        composeRule.onNodeWithText("Decks").assertIsSelected()
        composeRule.onNodeWithText("No decks yet").assertExists()
    }

    @Test
    fun everyTabIsReachable() {
        val tabs = listOf(
            "Study" to "Nothing to study",
            "Create" to "Create cards",
            "Analytics" to "No reviews yet",
            "Decks" to "No decks yet",
        )
        for ((tab, placeholder) in tabs) {
            composeRule.onNodeWithText(tab).performClick()
            composeRule.onNodeWithText(tab).assertIsSelected()
            composeRule.onNodeWithText(placeholder).assertExists()
        }
    }

    @Test
    fun settingsOpensFromTopBarAndHidesTabs() {
        composeRule.onNodeWithText("Study").performClick()
        composeRule.onNodeWithContentDescription("Settings").performClick()

        composeRule.onNodeWithText("Nothing to configure yet").assertExists()
        composeRule.onNodeWithText("Analytics").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Study").assertIsSelected()
        composeRule.onNodeWithText("Nothing to study").assertExists()
    }
}
