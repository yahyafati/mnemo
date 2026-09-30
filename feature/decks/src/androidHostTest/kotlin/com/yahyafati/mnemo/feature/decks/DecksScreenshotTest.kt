package com.yahyafati.mnemo.feature.decks

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.RetentionOverview
import com.yahyafati.mnemo.core.model.TodaySummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

/**
 * Screenshots of the Decks screen (docs/design/decks.html), phone in both themes and a tablet.
 * Record: ./gradlew :feature:decks:recordRoborazziAndroidHostTest · Verify: verifyRoborazziAndroidHostTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class DecksScreenshotTest {
    @Test
    fun phoneLight() = captureRoboImage("src/androidHostTest/screenshots/decks_phone_light.png") { Decks(dark = false) }

    @Test
    fun phoneDark() = captureRoboImage("src/androidHostTest/screenshots/decks_phone_dark.png") { Decks(dark = true) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() = captureRoboImage("src/androidHostTest/screenshots/decks_tablet_light.png") { Decks(dark = false) }

    @Composable
    private fun Decks(dark: Boolean) {
        val child = DeckItem("c", "Kanji N3", null, false, 4, 0, 120, null, emptyList(), false)
        MnemoTheme(darkTheme = dark) {
            DecksScreen(
                uiState = DecksUiState(
                    isLoading = false,
                    now = Instant.parse("2026-10-22T09:00:00Z"),
                    date = LocalDate.of(2026, 10, 22),
                    today = TodaySummary(14, 8, 6, 12, 14, 32, 1420),
                    retention = RetentionOverview.Empty,
                    hasDecks = true,
                    decks = listOf(
                        DeckItem(
                            "a", "Cognitive Neuroscience", "Exam Prep", true, 18, 5, 320,
                            Instant.parse("2026-10-22T07:00:00Z"), emptyList(), false, recall = 0.94, examInDays = 12,
                        ),
                        DeckItem("b", "Japanese", "Language", false, 4, 0, 450, null, listOf(child), true, recall = 0.87),
                        DeckItem("d", "Organic Chemistry", "Exam Prep", false, 0, 12, 210, null, emptyList(), false, examInDays = 2),
                    ),
                    filterCounts = FilterCounts(3, 3, 1),
                    categories = listOf("Exam Prep", "Language"),
                ),
                onAction = {},
                onStudyDeck = {},
                onStartDailyMix = {},
                onAddCards = {},
                onBrowse = {},
                onImport = {},
                onExportDeck = { _, _ -> },
            )
        }
    }
}
