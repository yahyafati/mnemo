package com.yahyafati.mnemo.feature.analytics

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of Analytics (docs/design/analytics.html). Record: ./gradlew :feature:analytics:recordRoborazziDebug */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h2200dp-xxhdpi")
class AnalyticsScreenshotTest {
    @Test
    fun light() = captureRoboImage("src/test/screenshots/analytics_light.png") {
        MnemoTheme(darkTheme = false) { AnalyticsScreen(uiState = sampleAnalyticsState(), onEditNote = {}) }
    }

    @Test
    fun dark() = captureRoboImage("src/test/screenshots/analytics_dark.png") {
        MnemoTheme(darkTheme = true) { AnalyticsScreen(uiState = sampleAnalyticsState(), onEditNote = {}) }
    }
}
