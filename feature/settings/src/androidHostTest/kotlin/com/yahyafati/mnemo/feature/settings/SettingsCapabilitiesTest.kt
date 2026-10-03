package com.yahyafati.mnemo.feature.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.model.UserSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings shows only what the platform has (`PlatformCapabilities`), instead of asking which Android it is on. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3000dp-xxhdpi")
class SettingsCapabilitiesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSettings(capabilities: PlatformCapabilities) = composeRule.setContent {
        CompositionLocalProvider(LocalPlatformCapabilities provides capabilities) {
            MnemoTheme {
                SettingsScreen(
                    uiState = SettingsUiState.Success(UserSettings()),
                    syncStatus = SyncStatus.Off,
                    dataState = DataUiState(),
                    aiSummary = AiSummary(0, null),
                    optimizerState = TransferState.Idle,
                    optimizerCallbacks = OptimizerCallbacks({}, {}, {}),
                    onOpenAiProviders = {},
                    onOpenSync = {},
                    onOpenLicenses = {},
                    dataCallbacks = DataCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}),
                    callbacks = SettingsCallbacks({}, {}, {}, { true }, { true }, {}, {}, {}),
                    onBackClick = {},
                )
            }
        }
    }

    @Test
    fun remindersAreThereWhereThePlatformHasThem() {
        setSettings(PlatformCapabilities(reminders = true))
        composeRule.onNodeWithText("Reminders").assertExists()
        composeRule.onNodeWithText("Daily study reminder").assertExists()
    }

    @Test
    fun remindersAreHiddenWithoutThem() {
        setSettings(PlatformCapabilities(reminders = false))
        composeRule.onNodeWithText("Daily study reminder").assertDoesNotExist()
        composeRule.onNodeWithText("Reminders").assertDoesNotExist()
        // The rest of Settings is unchanged.
        composeRule.onNodeWithText("Dynamic color").assertExists()
    }

    @Test
    fun dynamicColorSaysWhyWhenThePlatformLacksIt() {
        setSettings(PlatformCapabilities(dynamicColor = false))
        composeRule.onNodeWithText("Needs Android 12 or newer.").assertExists()
    }

    @Test
    fun dynamicColorIsNotListedWhereThePlatformHasNoWallpaper() {
        setSettings(PlatformCapabilities(dynamicColor = false, dynamicColorSetting = false))
        composeRule.onNodeWithText("Dynamic color").assertDoesNotExist()
        composeRule.onNodeWithText("Needs Android 12 or newer.").assertDoesNotExist()
    }
}
