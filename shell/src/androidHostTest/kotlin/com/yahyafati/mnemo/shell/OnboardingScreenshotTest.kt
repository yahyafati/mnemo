package com.yahyafati.mnemo.shell

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.shell.onboarding.OnboardingScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/** Screenshots of the first-run screen. Record: ./gradlew :shell:recordRoborazziAndroidHostTest */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingScreenshotTest {
    @Test
    fun light() = captureRoboImage("src/androidHostTest/screenshots/onboarding_light.png") {
        MnemoTheme(darkTheme = false) { OnboardingScreen(LocalTime.of(19, 0), { _, _ -> }, { _, _ -> }, {}) }
    }

    @Test
    fun dark() = captureRoboImage("src/androidHostTest/screenshots/onboarding_dark.png") {
        MnemoTheme(darkTheme = true) { OnboardingScreen(LocalTime.of(19, 0), { _, _ -> }, { _, _ -> }, {}) }
    }
}
