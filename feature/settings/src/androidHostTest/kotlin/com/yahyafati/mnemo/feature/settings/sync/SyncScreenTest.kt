package com.yahyafati.mnemo.feature.settings.sync

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals

/** What Settings › Sync shows in each state, and what its buttons ask for. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class SyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val folder = SyncBackend.Folder("/Users/me/Sync/Mnemo")
    private val devices = SyncDevices.Loaded(
        listOf(
            SyncDeviceSummary("a", "Pixel 9", "Android", "1.0.0", Instant.parse("2026-10-03T10:00:00Z"), isThisDevice = true),
            SyncDeviceSummary("b", "Work laptop", "Desktop (Windows)", "1.0.0", Instant.parse("2026-10-02T08:00:00Z"), isThisDevice = false),
        ),
    )

    private fun show(
        state: SyncUiState,
        callbacks: SyncCallbacks = SyncCallbacks(),
        capabilities: PlatformCapabilities = PlatformCapabilities(),
    ) = composeRule.setContent {
        CompositionLocalProvider(LocalPlatformCapabilities provides capabilities) {
            MnemoTheme { SyncScreen(state, callbacks, onBack = {}) }
        }
    }

    @Test
    fun offExplainsAndOffersAFolder() {
        show(SyncUiState())
        composeRule.onNodeWithText("Use a folder").assertIsDisplayed()
        composeRule.onNodeWithText("Each device writes its changes", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Devices").assertDoesNotExist()
        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun aComputerMentionsTheSyncAppsAPhoneDoesNot() {
        show(SyncUiState(), capabilities = PlatformCapabilities(syncAppFolders = true))
        composeRule.onNodeWithText("Google Drive, Dropbox, Nextcloud or Syncthing", substring = true).assertIsDisplayed()
    }

    @Test
    fun aPhoneSaysToPickAnEmptyFolder() {
        show(SyncUiState(), capabilities = PlatformCapabilities(syncAppFolders = false))
        composeRule.onNodeWithText("Pick an empty folder", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Google Drive, Dropbox, Nextcloud or Syncthing", substring = true).assertDoesNotExist()
    }

    @Test
    fun onShowsTheStatusTheDevicesAndSyncsOnRequest() {
        var synced = 0
        show(
            SyncUiState(
                status = SyncStatus.Idle(folder, Instant.parse("2026-10-03T10:00:00Z")),
                encrypted = true,
                devices = devices,
                pendingChanges = 3,
            ),
            SyncCallbacks(onSyncNow = { synced++ }),
        )
        composeRule.onNodeWithText("3 changes are waiting to be sent").assertIsDisplayed()
        composeRule.onNodeWithText("Folder: /Users/me/Sync/Mnemo").assertIsDisplayed()
        composeRule.onNodeWithText("End-to-end encrypted with your passphrase").assertIsDisplayed()
        composeRule.onNodeWithText("Pixel 9 (this device)").assertIsDisplayed()
        composeRule.onNodeWithText("Work laptop").assertIsDisplayed()

        composeRule.onNodeWithText("Sync now").performClick()
        assertEquals(1, synced)
    }

    @Test
    fun anUnencryptedFolderSaysAnyoneCanReadIt() {
        show(SyncUiState(status = SyncStatus.Idle(folder, null), encrypted = false, devices = SyncDevices.Loading))
        composeRule.onNodeWithText("Not encrypted", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Not synced yet").assertIsDisplayed()
    }

    @Test
    fun aRoundThatIsRunningCantBeStartedAgain() {
        show(SyncUiState(status = SyncStatus.Syncing(folder, null)))
        composeRule.onNodeWithText("Sync now").assertIsNotEnabled()
        composeRule.onNodeWithText("Syncing…").assertIsDisplayed()
    }

    @Test
    fun aMissingPassphraseCanBeEntered() {
        var asked = 0
        show(
            SyncUiState(status = SyncStatus.Error(folder, SyncProblem.PassphraseRequired, null)),
            SyncCallbacks(onAskForPassphrase = { asked++ }),
        )
        composeRule.onNodeWithText("doesn't have its passphrase", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Enter passphrase").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun aDeviceThatMustJoinAgainCanDoSo() {
        var asked = 0
        show(
            SyncUiState(status = SyncStatus.Error(folder, SyncProblem.MustRejoin, null)),
            SyncCallbacks(onAskToRejoin = { asked++ }),
        )
        composeRule.onNodeWithText("Join again").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun aRestoreOffersBothWays() {
        var uploads = 0
        var rejoins = 0
        show(
            SyncUiState(status = SyncStatus.Restored(folder)),
            SyncCallbacks(onAskToUploadAsNew = { uploads++ }, onAskToRejoin = { rejoins++ }),
        )
        composeRule.onNodeWithText("Sync is paused").assertIsDisplayed()
        composeRule.onNodeWithText("Upload this collection as the new sync data", useUnmergedTree = true).performClick()
        assertEquals(1, uploads)
        composeRule.onNodeWithText("Use the sync data again", useUnmergedTree = true).performClick()
        assertEquals(1, rejoins)
        // Syncing now makes no sense until one is chosen.
        composeRule.onNodeWithText("Sync now").assertDoesNotExist()
    }

    @Test
    fun leavingAsksBeforeItDoesAnything() {
        var asked = 0
        show(
            SyncUiState(status = SyncStatus.Idle(folder, null), devices = devices),
            SyncCallbacks(onAskToLeave = { asked++ }),
        )
        composeRule.onNodeWithText("Stop syncing on this device").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun theLeaveQuestionNamesWhatStays() {
        var confirmed = 0
        show(
            SyncUiState(status = SyncStatus.Idle(folder, null), dialog = SyncDialog.ConfirmLeave),
            SyncCallbacks(dialogs = SyncDialogCallbacks(onConfirmLeave = { confirmed++ })),
        )
        composeRule.onNodeWithText("Your decks and cards stay here", substring = true).assertIsDisplayed()
        composeRule.onNode(hasText("Stop syncing") and hasClickAction()).performClick()
        assertEquals(1, confirmed)
    }

    @Test
    fun deletingNamesTheDevicesThatUseTheSyncData() {
        show(
            SyncUiState(status = SyncStatus.Idle(folder, null), dialog = SyncDialog.ConfirmDelete(listOf("Work laptop"))),
        )
        composeRule.onNodeWithText("Devices that use it: Work laptop.", substring = true).assertIsDisplayed()
    }

    @Test
    fun aFolderThatCantBeUsedSaysWhy() {
        show(SyncUiState(dialog = SyncDialog.FolderProblem(SyncProblem.Auth, folder)))
        composeRule.onNodeWithText("Can't use this folder").assertIsDisplayed()
        composeRule.onNodeWithText("can't open the sync folder", substring = true).assertIsDisplayed()
    }

    @Test
    fun aJoinSaysWhereTheOldCollectionWasSaved() {
        show(SyncUiState(status = SyncStatus.Idle(folder, null), notice = SyncNotice.Joined("file:///files/sync-backups/before-sync.zip")))
        composeRule.onNodeWithText("This device now has the synced collection.").assertIsDisplayed()
        composeRule.onNodeWithText("file:///files/sync-backups/before-sync.zip", substring = true).assertIsDisplayed()
    }

    // --- Google Drive (S6) -------------------------------------------------------------------------------------

    @Test
    fun driveIsNotOfferedByABuildWithoutAGoogleClient() {
        show(SyncUiState(googleDriveAvailable = false))
        composeRule.onNodeWithText("Use Google Drive").assertDoesNotExist()
    }

    @Test
    fun driveIsOfferedNextToTheFolderWithWhatItCanSee() {
        var chosen = 0
        show(SyncUiState(googleDriveAvailable = true), SyncCallbacks(onUseGoogleDrive = { chosen++ }))
        composeRule.onNodeWithText("Use a folder").assertIsDisplayed()
        composeRule.onNodeWithText("hidden folder of your Google Drive that only Mnemo can open", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Use Google Drive").performClick()
        assertEquals(1, chosen)
    }

    @Test
    fun whileSigningInTheButtonsWaitAndTheBrowserWaitCanBeCancelled() {
        var cancelled = 0
        show(SyncUiState(googleDriveAvailable = true, working = true, signingIn = true), SyncCallbacks(onCancelSignIn = { cancelled++ }))
        composeRule.onNodeWithText("Use Google Drive").assertIsNotEnabled()
        composeRule.onNodeWithText("Waiting for you in the browser…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun driveShowsItsNameAsTheLocation() {
        show(SyncUiState(status = SyncStatus.Idle(SyncBackend.GoogleDrive, null), encrypted = true, devices = devices))
        composeRule.onNodeWithText("Google Drive (a hidden folder only Mnemo can open)").assertIsDisplayed()
        composeRule.onNodeWithText("Folder:", substring = true).assertDoesNotExist()
    }

    @Test
    fun aRefusedGoogleSignInCanBeRepeated() {
        var signedIn = 0
        show(
            SyncUiState(status = SyncStatus.Error(SyncBackend.GoogleDrive, SyncProblem.Auth, null)),
            SyncCallbacks(onSignInAgain = { signedIn++ }),
        )
        composeRule.onNodeWithText("no longer signed in to Google", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sign in to Google").performClick()
        assertEquals(1, signedIn)
    }

    @Test
    fun aRefusedFolderOffersNoSignIn() {
        show(SyncUiState(status = SyncStatus.Error(folder, SyncProblem.Auth, null)))
        composeRule.onNodeWithText("Sign in to Google").assertDoesNotExist()
    }

    @Test
    fun aFullDriveSaysSo() {
        show(SyncUiState(status = SyncStatus.Error(SyncBackend.GoogleDrive, SyncProblem.Quota, null)))
        composeRule.onNodeWithText("Your Google Drive is full", substring = true).assertIsDisplayed()
    }

    @Test
    fun aDriveThatCantBeUsedHasItsOwnTitle() {
        show(SyncUiState(dialog = SyncDialog.FolderProblem(SyncProblem.Auth, SyncBackend.GoogleDrive)))
        composeRule.onNodeWithText("Can't use Google Drive").assertIsDisplayed()
        composeRule.onNodeWithText("Sign in again to keep syncing", substring = true).assertIsDisplayed()
    }

    @Test
    fun aSafAndAPathAreShownReadably() {
        assertEquals("Documents/Mnemo", folderLabel("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FMnemo"))
        assertEquals("C:\\Users\\me\\Mnemo", folderLabel("C:\\Users\\me\\Mnemo"))
        assertEquals("/Users/me/Sync/Mnemo", folderLabel("/Users/me/Sync/Mnemo"))
    }
}
