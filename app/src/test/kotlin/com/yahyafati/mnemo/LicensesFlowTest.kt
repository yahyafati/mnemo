package com.yahyafati.mnemo

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mikepenz.aboutlibraries.Libs
import com.yahyafati.mnemo.di.TestMnemoApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2: the open-source licenses screen, from Settings › About, and the generated list behind it.
 * The GPL and the notices of what Mnemo bundles have to ship with the app.
 */
@Config(application = TestMnemoApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
@RunWith(RobolectricTestRunner::class)
class LicensesFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aboutLeadsToTheLicensesList() {
        awaitText("No decks yet")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        awaitText("About")
        composeRule.onNodeWithText("Source code").performScrollTo()
        composeRule.onNodeWithText("GPL-3.0-or-later. Mnemo is free software.").assertExists()

        composeRule.onNodeWithText("Open-source licenses").performScrollTo().performClick()
        awaitText("Mnemo is licensed under GPL-3.0-or-later")
        // The tab bars are gone, and the generated list is on screen.
        composeRule.onNodeWithText("Analytics").assertDoesNotExist()
        awaitText("Kotlin")
    }

    @Test
    fun generatedListHasBundledFilesAndFullLicenseTexts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        val libs = Libs.Builder().withJson(json).build()

        val names = libs.libraries.map { it.name }
        for (bundled in listOf("py-fsrs", "KaTeX", "Newsreader (font)", "Hanken Grotesk (font)", "JetBrains Mono (font)")) {
            assertTrue("$bundled is missing from the licenses list", bundled in names)
        }
        // The third-party notices need the license text itself, not only a name.
        val texts = libs.licenses.associate { it.hash to it.licenseContent.orEmpty() }
        for (id in listOf("Apache-2.0", "MIT", "OFL-1.1")) {
            assertTrue("$id has no license text", texts[id].orEmpty().length > 500)
        }
        // No proprietary SDKs: the F-Droid build must be the same source tree (ADR 0009).
        val proprietary = libs.libraries.filter { lib ->
            listOf("com.google.android.gms", "com.google.firebase", "com.android.billingclient").any { lib.uniqueId.startsWith(it) }
        }
        assertEquals(emptyList<String>(), proprietary.map { it.uniqueId })
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
