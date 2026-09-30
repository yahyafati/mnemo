package com.yahyafati.mnemo.core.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

/**
 * The desktop baseline of every design-system component, in light and dark (the Android one is
 * `ComponentCatalogScreenshotTest`).
 *
 * Record:  ./gradlew :core:designsystem:recordRoborazziDesktop
 * Verify:  ./gradlew :core:designsystem:verifyRoborazziDesktop
 */
@OptIn(ExperimentalTestApi::class)
class ComponentCatalogDesktopScreenshotTest {
    @Test
    fun catalogLight() = capture(darkTheme = false, file = "src/desktopTest/screenshots/component_catalog_light.png")

    @Test
    fun catalogDark() = capture(darkTheme = true, file = "src/desktopTest/screenshots/component_catalog_dark.png")

    private fun capture(darkTheme: Boolean, file: String) = runDesktopComposeUiTest(width = 400, height = 1500) {
        setContent {
            MnemoTheme(darkTheme = darkTheme) {
                ComponentCatalog()
            }
        }
        waitForIdle()
        onRoot().captureRoboImage(file)
    }
}
