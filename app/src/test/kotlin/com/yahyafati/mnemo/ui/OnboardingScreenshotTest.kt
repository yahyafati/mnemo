package com.yahyafati.mnemo.ui

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.ui.onboarding.OnboardingScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/** Screenshots of the first-run screen. Record: ./gradlew :app:recordRoborazziDebug */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenshotTest {
    @Test
    fun light() = captureRoboImage("src/test/screenshots/onboarding_light.png") {
        MnemoTheme(darkTheme = false) { OnboardingScreen(LocalTime.of(19, 0), { _, _ -> }, { _, _ -> }, {}) }
    }

    @Test
    fun dark() = captureRoboImage("src/test/screenshots/onboarding_dark.png") {
        MnemoTheme(darkTheme = true) { OnboardingScreen(LocalTime.of(19, 0), { _, _ -> }, { _, _ -> }, {}) }
    }
}
