package com.yahyafati.mnemo.feature.settings

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ReminderSettings
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.model.UserSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshot of Settings with every section. Record: ./gradlew :feature:settings:recordRoborazziDebug */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h3000dp-xxhdpi")
class SettingsScreenshotTest {
    @Test
    fun settings() = captureRoboImage("src/test/screenshots/settings_light.png") {
        MnemoTheme {
            SettingsScreen(
                uiState = SettingsUiState.Success(UserSettings(reminder = ReminderSettings(enabled = true))),
                dataState = DataUiState(),
                aiSummary = AiSummary(2, "OpenAI"),
                optimizerState = TransferState.Idle,
                optimizerCallbacks = OptimizerCallbacks({}, {}, {}),
                onOpenAiProviders = {},
                onOpenLicenses = {},
                dataCallbacks = DataCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}),
                callbacks = SettingsCallbacks({}, {}, {}, { true }, { true }, {}, {}, {}),
                onBackClick = {},
            )
        }
    }
}
