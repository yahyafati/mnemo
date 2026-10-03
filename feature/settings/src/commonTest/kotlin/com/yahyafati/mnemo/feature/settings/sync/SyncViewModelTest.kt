package com.yahyafati.mnemo.feature.settings.sync

import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncLocation
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncProblemException
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.data.sync.WebDavTestResult
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeSyncRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSyncRepository()
    private val viewModel by lazy { SyncViewModel(repository) }
    private val folder = SyncBackend.Folder("/Users/me/Sync/Mnemo")

    private fun runWithState(block: suspend () -> Unit) = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect {} }
        block()
    }

    private val state get() = viewModel.uiState.value

    private fun device(name: String, here: Boolean = false) =
        SyncDeviceSummary(name.lowercase(), name, "Desktop", "1.0", Instant.EPOCH, here)

    // --- set up -----------------------------------------------------------------------------------------------

    @Test
    fun anEmptyFolderOffersToStartSyncThere() = runWithState {
        repository.location = SyncLocation.Empty
        viewModel.onFolderPicked(folder.location)

        assertEquals(SyncDialog.Create(folder), state.dialog)
        assertFalse(state.working)
        // Looking at the folder started nothing.
        assertEquals(listOf("inspect"), repository.calls.map { it.name })

        viewModel.create(null)

        assertEquals("create", repository.calls.last().name)
        assertNull(repository.calls.last().passphrase)
        assertNull(state.dialog)
        assertIs<SyncStatus.Idle>(state.status)
    }

    @Test
    fun aPassphraseGoesToTheRepositoryAndIsMarkedEncrypted() = runWithState {
        viewModel.onFolderPicked(folder.location)
        viewModel.create("correct horse")

        assertEquals("correct horse", repository.calls.last().passphrase)
        assertTrue(state.encrypted)
    }

    @Test
    fun aFailedStartKeepsTheDialogAndSaysWhy() = runWithState {
        viewModel.onFolderPicked(folder.location)
        repository.failure = SyncProblemException(SyncProblem.Quota, "full")

        viewModel.create(null)

        assertEquals(SyncDialog.Create(folder, SyncProblem.Quota), state.dialog)
        assertIs<SyncStatus.Off>(state.status)
        assertFalse(state.working)
        // Trying again works.
        viewModel.create(null)
        assertNull(state.dialog)
    }

    @Test
    fun aFolderWithSyncDataOffersToJoinIt() = runWithState {
        repository.location = SyncLocation.SyncData(encrypted = false)
        viewModel.onFolderPicked(folder.location)
        assertEquals(SyncDialog.Join(folder, encrypted = false), state.dialog)

        viewModel.join(null)

        val call = repository.calls.last()
        assertEquals("join", call.name)
        assertFalse(call.replaceLocalData)
        assertNull(state.dialog)
        assertEquals(SyncNotice.Joined(null), state.notice)
        viewModel.dismissNotice()
        assertNull(state.notice)
    }

    @Test
    fun joiningOverDataAsksOnceMoreAndKeepsThePassphrase() = runWithState {
        repository.location = SyncLocation.SyncData(encrypted = true)
        repository.hasLocalData = true
        viewModel.onFolderPicked(folder.location)

        viewModel.join("correct horse")
        assertEquals(SyncDialog.ConfirmReplace(folder), state.dialog)
        assertIs<SyncStatus.Off>(state.status)

        viewModel.confirmReplace()

        val call = repository.calls.last()
        assertTrue(call.replaceLocalData)
        assertEquals("correct horse", call.passphrase)
        assertNull(state.dialog)
        assertEquals(SyncNotice.Joined("file:///before-sync.zip"), state.notice)
    }

    @Test
    fun aWrongPassphraseKeepsTheJoinDialogOpen() = runWithState {
        repository.location = SyncLocation.SyncData(encrypted = true)
        viewModel.onFolderPicked(folder.location)
        repository.failure = SyncProblemException(SyncProblem.PassphraseWrong, "wrong")

        viewModel.join("nope")

        assertEquals(SyncDialog.Join(folder, encrypted = true, failure = SyncProblem.PassphraseWrong), state.dialog)
    }

    @Test
    fun decliningTheReplacementLeavesTheDeviceAsItWas() = runWithState {
        repository.location = SyncLocation.SyncData(encrypted = false)
        repository.hasLocalData = true
        viewModel.onFolderPicked(folder.location)
        viewModel.join(null)

        viewModel.dismissDialog()

        assertNull(state.dialog)
        assertIs<SyncStatus.Off>(state.status)
        assertEquals(listOf("inspect", "join"), repository.calls.map { it.name })
    }

    @Test
    fun aFolderWithLeftoversCantBeUsed() = runWithState {
        repository.location = SyncLocation.Leftovers
        viewModel.onFolderPicked(folder.location)
        assertEquals(SyncDialog.Leftovers, state.dialog)
    }

    @Test
    fun aFolderThatCantBeReadSaysWhy() = runWithState {
        repository.readFailure = SyncProblemException(SyncProblem.Auth, "denied")
        viewModel.onFolderPicked(folder.location)
        assertEquals(SyncDialog.FolderProblem(SyncProblem.Auth, folder), state.dialog)
        assertFalse(state.working)
    }

    // --- running, problems, devices -----------------------------------------------------------------------------

    @Test
    fun syncNowAsksTheRepository() = runWithState {
        repository.statusState.value = SyncStatus.Idle(folder, null)
        viewModel.syncNow()
        assertEquals(1, repository.syncs)
    }

    @Test
    fun theDevicesAreReadWhileSyncIsOn() = runWithState {
        repository.deviceList = listOf(device("Phone"), device("Laptop", here = true))
        assertEquals(SyncDevices.Unavailable, state.devices)

        repository.statusState.value = SyncStatus.Idle(folder, Instant.EPOCH)

        assertEquals(SyncDevices.Loaded(repository.deviceList), state.devices)
        // After the next round they are read again.
        repository.deviceList = repository.deviceList + device("Tablet")
        repository.statusState.value = SyncStatus.Idle(folder, Instant.ofEpochSecond(60))
        assertEquals(3, (state.devices as SyncDevices.Loaded).devices.size)
    }

    @Test
    fun aLocationThatCantBeReadHasNoDeviceList() = runWithState {
        repository.readFailure = SyncProblemException(SyncProblem.Offline, "off")
        repository.statusState.value = SyncStatus.Idle(folder, null)
        assertEquals(SyncDevices.Unavailable, state.devices)
    }

    @Test
    fun theMissingPassphraseIsEnteredInADialog() = runWithState {
        repository.statusState.value = SyncStatus.Error(folder, SyncProblem.PassphraseRequired, null)

        viewModel.askForPassphrase()
        assertEquals(SyncDialog.Unlock(), state.dialog)

        repository.failure = SyncProblemException(SyncProblem.PassphraseWrong, "wrong")
        viewModel.unlock("nope")
        assertEquals(SyncDialog.Unlock(SyncProblem.PassphraseWrong), state.dialog)

        viewModel.unlock("correct horse")
        assertNull(state.dialog)
        assertEquals("correct horse", repository.calls.last { it.name == "unlock" }.passphrase)
        assertIs<SyncStatus.Idle>(state.status)
        // And it syncs again at once.
        assertEquals(1, repository.syncs)
    }

    @Test
    fun joiningAgainAsksFirst() = runWithState {
        repository.statusState.value = SyncStatus.Error(folder, SyncProblem.MustRejoin, null)

        viewModel.askToRejoin()
        assertEquals(SyncDialog.ConfirmRejoin(), state.dialog)
        assertTrue(repository.calls.none { it.name == "rejoin" })

        viewModel.confirmRejoin()

        assertEquals("rejoin", repository.calls.last().name)
        assertEquals(SyncNotice.Joined("file:///before-sync.zip"), state.notice)
        assertNull(state.dialog)
    }

    // --- after a restore ---------------------------------------------------------------------------------------

    @Test
    fun startingTheSyncDataAgainNamesTheDevicesThatMustJoinAgain() = runWithState {
        repository.deviceList = listOf(device("Phone"), device("Laptop", here = true))
        repository.statusState.value = SyncStatus.Restored(folder)

        viewModel.askToUploadAsNew()
        assertEquals(SyncDialog.Upload(listOf("Phone")), state.dialog)

        viewModel.uploadAsNew("correct horse")

        assertEquals("uploadAsNew", repository.calls.last().name)
        assertEquals("correct horse", repository.calls.last().passphrase)
        assertNull(state.dialog)
        assertIs<SyncStatus.Idle>(state.status)
    }

    @Test
    fun theRestoreCanBeDiscardedForTheLocationsData() = runWithState {
        repository.statusState.value = SyncStatus.Restored(folder)
        viewModel.askToRejoin()
        viewModel.confirmRejoin()
        assertIs<SyncStatus.Idle>(state.status)
    }

    // --- leaving ---------------------------------------------------------------------------------------------

    @Test
    fun leavingAsksFirst() = runWithState {
        repository.statusState.value = SyncStatus.Idle(folder, null)

        viewModel.askToLeave()
        assertEquals(SyncDialog.ConfirmLeave, state.dialog)
        assertEquals(0, repository.left)

        viewModel.confirmLeave()

        assertEquals(1, repository.left)
        assertNull(state.dialog)
        assertIs<SyncStatus.Off>(state.status)
    }

    @Test
    fun deletingTheSyncDataNamesWhoUsesIt() = runWithState {
        repository.deviceList = listOf(device("Phone"), device("Laptop", here = true))
        repository.statusState.value = SyncStatus.Idle(folder, Instant.EPOCH)

        viewModel.askToDelete()
        assertEquals(SyncDialog.ConfirmDelete(listOf("Phone")), state.dialog)

        viewModel.confirmDelete()

        assertEquals("deleteSyncData", repository.calls.last().name)
        assertIs<SyncStatus.Off>(state.status)
    }

    @Test
    fun aConfirmationThatWasNeverAskedForDoesNothing() = runWithState {
        repository.statusState.value = SyncStatus.Idle(folder, null)
        viewModel.confirmLeave()
        viewModel.confirmDelete()
        viewModel.confirmRejoin()
        viewModel.confirmReplace()
        assertTrue(repository.calls.isEmpty())
        assertEquals(0, repository.left)
    }

    // --- passphrases -------------------------------------------------------------------------------------------

    // --- Google Drive (S6) -------------------------------------------------------------------------------------

    private val drive = SyncBackend.GoogleDrive

    @Test
    fun googleDriveIsOfferedOnlyWhenTheBuildHasAGoogleClient() = runWithState {
        assertFalse(state.googleDriveAvailable)
        viewModel.useGoogleDrive()
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun anEmptyDriveOffersToStartSyncThereAfterSigningIn() = runWithState {
        repository.googleDriveAvailable = true
        repository.location = SyncLocation.Empty

        viewModel.useGoogleDrive()

        assertEquals(listOf("signInToGoogleDrive", "inspect"), repository.calls.map { it.name })
        assertEquals(drive, repository.calls.last().backend)
        assertEquals(SyncDialog.Create(drive), state.dialog)
        assertFalse(state.working)
        assertFalse(state.signingIn)
        assertTrue(state.googleDriveAvailable)

        viewModel.create("correct horse")

        assertEquals(drive, repository.calls.last().backend)
        assertEquals("correct horse", repository.calls.last().passphrase)
        assertIs<SyncStatus.Idle>(state.status)
    }

    @Test
    fun aDriveWithSyncDataOffersToJoinIt() = runWithState {
        repository.googleDriveAvailable = true
        repository.location = SyncLocation.SyncData(encrypted = true)

        viewModel.useGoogleDrive()

        assertEquals(SyncDialog.Join(drive, encrypted = true), state.dialog)
    }

    @Test
    fun givingUpInTheBrowserIsNotAnError() = runWithState {
        repository.googleDriveAvailable = true
        repository.signInFailure = SyncProblemException(SyncProblem.SignInCancelled, "cancelled")

        viewModel.useGoogleDrive()

        assertNull(state.dialog)
        assertFalse(state.working)
        // Nothing was looked at: the user never signed in.
        assertEquals(listOf("signInToGoogleDrive"), repository.calls.map { it.name })
    }

    @Test
    fun aSignInThatGoogleRefusesSaysWhy() = runWithState {
        repository.googleDriveAvailable = true
        repository.signInFailure = SyncProblemException(SyncProblem.Auth, "refused")

        viewModel.useGoogleDrive()

        assertEquals(SyncDialog.FolderProblem(SyncProblem.Auth, drive), state.dialog)
        assertFalse(state.working)
    }

    @Test
    fun aDriveThatCantBeReadAfterSigningInSaysWhy() = runWithState {
        repository.googleDriveAvailable = true
        repository.readFailure = SyncProblemException(SyncProblem.Offline, "no network")

        viewModel.useGoogleDrive()

        assertEquals(SyncDialog.FolderProblem(SyncProblem.Offline, drive), state.dialog)
    }

    @Test
    fun theWaitForTheBrowserCanBeCancelled() = runWithState {
        repository.googleDriveAvailable = true
        repository.signInWait = CompletableDeferred()

        viewModel.useGoogleDrive()

        assertTrue(state.signingIn)
        assertTrue(state.working)
        viewModel.cancelSignIn()
        assertFalse(state.signingIn)
        assertFalse(state.working)
        assertNull(state.dialog)
        // A second try is possible.
        repository.signInWait = null
        viewModel.useGoogleDrive()
        assertEquals(SyncDialog.Create(drive), state.dialog)
    }

    @Test
    fun signingInAgainAfterARefusalSyncsAtOnce() = runWithState {
        repository.googleDriveAvailable = true
        repository.statusState.value = SyncStatus.Error(drive, SyncProblem.Auth, Instant.EPOCH)

        viewModel.signInAgain()

        assertEquals(listOf("signInToGoogleDrive"), repository.calls.map { it.name })
        assertEquals(1, repository.syncs)
        assertFalse(state.working)
    }

    @Test
    fun aFailedSignInAgainKeepsTheProblemAndDoesNotSync() = runWithState {
        repository.googleDriveAvailable = true
        repository.statusState.value = SyncStatus.Error(drive, SyncProblem.Auth, Instant.EPOCH)
        repository.signInFailure = SyncProblemException(SyncProblem.Offline, "no network")

        viewModel.signInAgain()

        assertEquals(0, repository.syncs)
        assertEquals(SyncDialog.FolderProblem(SyncProblem.Offline, drive), state.dialog)
    }

    @Test
    fun startingTheSyncDataAgainOnDriveEncryptsByDefault() = runWithState {
        repository.statusState.value = SyncStatus.Restored(drive)
        viewModel.askToUploadAsNew()
        assertEquals(SyncDialog.Upload(emptyList(), encryptByDefault = true), state.dialog)
        viewModel.dismissDialog()

        repository.statusState.value = SyncStatus.Restored(folder)
        viewModel.askToUploadAsNew()
        assertEquals(SyncDialog.Upload(emptyList(), encryptByDefault = false), state.dialog)
    }

    // --- WebDAV (S7) ----------------------------------------------------------------------------------------------

    private val dav = SyncBackend.WebDav("https://cloud.example.org/remote.php/dav/files/alice/Mnemo/", "alice")

    @Test
    fun theWebDavFormOpensEmptyAndTestingReportsWithoutSettingAnythingUp() = runWithState {
        viewModel.useWebDav()
        assertEquals(SyncDialog.WebDavSetup(), state.dialog)

        repository.webDavTestResult = WebDavTestResult.FolderWillBeCreated
        viewModel.testWebDav("https://cloud.example.org/dav", "alice", "pw")

        assertEquals(WebDavTest.FolderWillBeCreated, (state.dialog as SyncDialog.WebDavSetup).test)
        assertEquals(listOf("test"), repository.webDavCalls.map { it.name })
        // Nothing was looked at or set up, and what was typed stays in the form for "Continue".
        assertTrue(repository.calls.isEmpty())
        assertEquals("https://cloud.example.org/dav", (state.dialog as SyncDialog.WebDavSetup).url)
        assertFalse(state.working)
    }

    @Test
    fun aTestThatFailsSaysWhyInTheForm() = runWithState {
        viewModel.useWebDav()
        repository.webDavFailure = SyncProblemException(SyncProblem.Auth, "401")

        viewModel.testWebDav("https://cloud.example.org/dav", "alice", "wrong")

        assertEquals(WebDavTest.Failed(SyncProblem.Auth), (state.dialog as SyncDialog.WebDavSetup).test)
    }

    @Test
    fun continuingConnectsThenLooksAtTheFolderLikeAnyOther() = runWithState {
        repository.location = SyncLocation.Empty
        viewModel.useWebDav()

        viewModel.connectWebDav("https://cloud.example.org/remote.php/dav/files/alice/Mnemo", " alice ", "pw")

        assertEquals(listOf("connect"), repository.webDavCalls.map { it.name })
        assertEquals(SyncDialog.Create(dav), state.dialog)
        assertEquals(dav, repository.calls.single { it.name == "inspect" }.backend)

        repository.location = SyncLocation.SyncData(encrypted = true)
        viewModel.dismissDialog()
        viewModel.useWebDav()
        viewModel.connectWebDav("https://cloud.example.org/remote.php/dav/files/alice/Mnemo", "alice", "pw")
        assertEquals(SyncDialog.Join(dav, encrypted = true), state.dialog)
    }

    @Test
    fun aRefusedPasswordKeepsTheFormOpenAndSetsNothingUp() = runWithState {
        viewModel.useWebDav()
        repository.webDavFailure = SyncProblemException(SyncProblem.Auth, "401")

        viewModel.connectWebDav("https://cloud.example.org/dav", "alice", "wrong")

        assertEquals(
            SyncDialog.WebDavSetup(url = "https://cloud.example.org/dav", username = "alice", failure = SyncProblem.Auth),
            state.dialog,
        )
        assertTrue(repository.calls.isEmpty())
        assertIs<SyncStatus.Off>(state.status)
    }

    @Test
    fun aFolderThatCantBeReadAfterConnectingSaysWhy() = runWithState {
        viewModel.useWebDav()
        repository.readFailure = SyncProblemException(SyncProblem.Offline, "no network")

        viewModel.connectWebDav("https://cloud.example.org/dav", "alice", "pw")

        assertEquals(SyncDialog.FolderProblem(SyncProblem.Offline, SyncBackend.WebDav("https://cloud.example.org/dav/", "alice")), state.dialog)
    }

    @Test
    fun theFormIsRefusedWhenItWasNeverOpened() = runWithState {
        viewModel.connectWebDav("https://cloud.example.org/dav", "alice", "pw")
        viewModel.testWebDav("https://cloud.example.org/dav", "alice", "pw")
        assertTrue(repository.webDavCalls.isEmpty())
        assertNull(state.dialog)
    }

    @Test
    fun aRefusedPasswordAfterwardsIsEnteredAgainForTheSameServerAndSyncs() = runWithState {
        repository.statusState.value = SyncStatus.Error(dav, SyncProblem.Auth, Instant.EPOCH)

        viewModel.askForWebDavPassword()
        assertEquals(SyncDialog.WebDavSetup(dav.url, "alice", reconnect = true), state.dialog)

        viewModel.connectWebDav(dav.url, "alice", "new-password")

        assertEquals(listOf("updatePassword"), repository.webDavCalls.map { it.name })
        assertEquals("new-password", repository.webDavCalls.single().password)
        assertNull(state.dialog)
        assertEquals(1, repository.syncs)
    }

    @Test
    fun aNewPasswordThatIsRefusedKeepsTheFormAndDoesNotSync() = runWithState {
        repository.statusState.value = SyncStatus.Error(dav, SyncProblem.Auth, Instant.EPOCH)
        viewModel.askForWebDavPassword()
        repository.webDavFailure = SyncProblemException(SyncProblem.Auth, "401")

        viewModel.connectWebDav(dav.url, "alice", "still-wrong")

        assertEquals(SyncDialog.WebDavSetup(dav.url, "alice", reconnect = true, failure = SyncProblem.Auth), state.dialog)
        assertEquals(0, repository.syncs)
    }

    @Test
    fun askingForAWebDavPasswordOnAnotherLocationDoesNothing() = runWithState {
        repository.statusState.value = SyncStatus.Error(folder, SyncProblem.Auth, Instant.EPOCH)
        viewModel.askForWebDavPassword()
        assertNull(state.dialog)
    }

    @Test
    fun startingTheSyncDataAgainOnWebDavEncryptsByDefault() = runWithState {
        repository.statusState.value = SyncStatus.Restored(dav)
        viewModel.askToUploadAsNew()
        assertEquals(SyncDialog.Upload(emptyList(), encryptByDefault = true), state.dialog)
    }

    @Test
    fun aNewPassphraseNeedsEightCharactersAndARepeat() {
        assertEquals(PassphraseProblem.Empty, SyncPassphrase.check("", ""))
        assertEquals(PassphraseProblem.Empty, SyncPassphrase.check("   ", "   "))
        assertEquals(PassphraseProblem.TooShort, SyncPassphrase.check("short", "short"))
        assertEquals(PassphraseProblem.Mismatch, SyncPassphrase.check("long enough", "long enough!"))
        assertNull(SyncPassphrase.check("long enough", "long enough"))
    }
}
