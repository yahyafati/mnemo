package com.yahyafati.mnemo.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mikepenz.aboutlibraries.Libs
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/** Settings › About › Open-source licenses, fed the JSON format the AboutLibraries plugin generates. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LicensesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val libraries = Libs.Builder().withJson(
        """
        {
          "libraries": [
            {"uniqueId": "org.jsoup:jsoup", "name": "jsoup", "artifactVersion": "1.23.2", "licenses": ["MIT"],
             "developers": [{"name": "Jonathan Hedley"}]},
            {"uniqueId": "bundled:py-fsrs", "name": "py-fsrs", "licenses": ["MIT"],
             "developers": [{"name": "Open Spaced Repetition"}]}
          ],
          "licenses": {"MIT": {"hash": "MIT", "name": "MIT License", "content": "Permission is hereby granted, free of charge"}}
        }
        """.trimIndent(),
    ).build()

    @Test
    fun listsMnemosOwnLicenseAndTheBundledLibraries() {
        composeRule.setContent { MnemoTheme { LicensesScreen(libraries, onBack = {}) } }

        composeRule.onNodeWithText("Open-source licenses").assertIsDisplayed()
        composeRule.onNodeWithText("Mnemo is licensed under GPL-3.0-or-later").assertIsDisplayed()
        composeRule.onNodeWithText("jsoup").assertIsDisplayed()
        composeRule.onNodeWithText("py-fsrs").assertIsDisplayed()
    }

    @Test
    fun aLibraryOpensItsLicenseText() {
        composeRule.setContent { MnemoTheme { LicensesScreen(libraries, onBack = {}) } }

        composeRule.onNodeWithText("py-fsrs").performClick()
        composeRule.onNodeWithText("View license").performClick()
        composeRule.onNodeWithText("Permission is hereby granted, free of charge", substring = true).assertIsDisplayed()
    }

    @Test
    fun backLeavesTheScreen() {
        var left = false
        composeRule.setContent { MnemoTheme { LicensesScreen(libraries, onBack = { left = true }) } }

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(left)
    }
}
