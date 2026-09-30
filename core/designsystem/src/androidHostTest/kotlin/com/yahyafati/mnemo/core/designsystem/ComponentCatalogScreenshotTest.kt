package com.yahyafati.mnemo.core.designsystem

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Baseline screenshots of every design-system component, in light and dark.
 *
 * Record:  ./gradlew :core:designsystem:recordRoborazziAndroidHostTest
 * Verify:  ./gradlew :core:designsystem:verifyRoborazziAndroidHostTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h1500dp-xhdpi")
class ComponentCatalogScreenshotTest {
    @Test
    fun catalogLight() = captureRoboImage("src/androidHostTest/screenshots/component_catalog_light.png") {
        MnemoTheme(darkTheme = false) { ComponentCatalog() }
    }

    @Test
    fun catalogDark() = captureRoboImage("src/androidHostTest/screenshots/component_catalog_dark.png") {
        MnemoTheme(darkTheme = true) { ComponentCatalog() }
    }
}
