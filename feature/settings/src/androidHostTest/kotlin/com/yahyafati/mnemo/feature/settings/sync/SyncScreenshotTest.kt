package com.yahyafati.mnemo.feature.settings.sync

import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Screenshots of Settings › Sync. Record: ./gradlew :feature:settings:recordRoborazziAndroidHostTest */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h1100dp-xxhdpi")
class SyncScreenshotTest {
    private val folder = SyncBackend.Folder("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMnemo")
    private val seen = Instant.parse("2026-10-03T10:00:00Z")
    private val devices = SyncDevices.Loaded(
        listOf(
            SyncDeviceSummary("a", "Pixel 9", "Android", "1.0.0", seen, isThisDevice = true),
            SyncDeviceSummary("b", "Work laptop", "Desktop (Windows)", "1.0.0", Instant.parse("2026-10-02T08:00:00Z"), isThisDevice = false),
        ),
    )

    private fun shot(name: String, state: SyncUiState) = captureRoboImage("src/androidHostTest/screenshots/$name.png") {
        MnemoTheme { SyncScreen(state, SyncCallbacks(), onBack = {}) }
    }

    @Test
    fun off() = shot("sync_off", SyncUiState())

    @Test
    fun on() = shot("sync_on", SyncUiState(status = SyncStatus.Idle(folder, seen), encrypted = true, devices = devices, pendingChanges = 2))

    @Test
    fun needsThePassphrase() = shot(
        "sync_passphrase_required",
        SyncUiState(status = SyncStatus.Error(folder, SyncProblem.PassphraseRequired, seen), devices = SyncDevices.Unavailable),
    )

    @Test
    fun restored() = shot("sync_restored", SyncUiState(status = SyncStatus.Restored(folder), devices = devices))
}
