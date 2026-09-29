package com.yahyafati.mnemo

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.yahyafati.mnemo.core.data.repository.ReviewRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject
import org.junit.Assert.assertEquals

/**
 * The new-user path end to end (ROADMAP Phase 6) on a real (in-memory) database: onboarding
 * creates the first deck, a card is added, studied and rated, and Analytics counts the review.
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
    lateinit var settingsRepository: UserSettingsRepository

    @Inject
    lateinit var reviewRepository: ReviewRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // The test storage starts past onboarding; this test is the first run.
        runBlocking { settingsRepository.setOnboardingCompleted(false) }
    }

    private fun awaitText(text: String) = composeRule.waitUntil(TIMEOUT_MS) {
        composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun onboardingCreateDeckStudyAndStats() {
        // Onboarding: two pages of introduction, then the first deck.
        awaitText("Remember more, study less")
        composeRule.onNodeWithText("Next").performClick()
        awaitText("How studying works")
        composeRule.onNodeWithText("Next").performClick()
        awaitText("Start your first deck")
        composeRule.onNode(hasSetTextAction() and hasText("Deck name")).performTextInput("Biology")
        composeRule.onNodeWithText("Create deck and add cards").performClick()

        // The editor opens on the new deck.
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

        // Stats: the review is counted.
        composeRule.onNodeWithText("Analytics").performClick()
        awaitText("Reviews, last 30 days")
        assertEquals(1, runBlocking { reviewRepository.observeTodayCounts().first().total })
        assertEquals(true, runBlocking { settingsRepository.settings.first().onboardingCompleted })
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
