package com.yahyafati.mnemo.feature.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncLocation
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncReplacesLocalDataException
import com.yahyafati.mnemo.core.data.sync.SyncRepository
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.data.sync.WebDavTestResult
import com.yahyafati.mnemo.core.data.sync.backend
import com.yahyafati.mnemo.core.data.sync.lastSyncAt
import com.yahyafati.mnemo.core.data.sync.syncProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The devices of the sync location, as far as they could be read. */
sealed interface SyncDevices {
    data object Loading : SyncDevices

    data class Loaded(val devices: List<SyncDeviceSummary>) : SyncDevices

    /** The location couldn't be read (offline, a passphrase is missing, ...): the status says why. */
    data object Unavailable : SyncDevices
}

/** A question or a form that is open over the Sync screen. [failure] is why the last attempt at it didn't work. */
sealed interface SyncDialog {
    val failure: SyncProblem? get() = null

    /** The folder is empty: sync data can start here, encrypted or not. */
    data class Create(val backend: SyncBackend, override val failure: SyncProblem? = null) : SyncDialog

    /** The folder holds sync data: use it on this device. An encrypted one needs its passphrase. */
    data class Join(val backend: SyncBackend, val encrypted: Boolean, override val failure: SyncProblem? = null) : SyncDialog

    /** Joining replaces what is on this device (it is saved first): the user has to agree. */
    data class ConfirmReplace(val backend: SyncBackend, override val failure: SyncProblem? = null) : SyncDialog

    /** The folder has files that aren't Mnemo's. */
    data object Leftovers : SyncDialog

    /**
     * The form for a WebDAV server: [url] and [username] are what the fields start with (the saved ones when the password is
     * asked for again, [reconnect]); [test] is the answer to "Test connection". The password is never here.
     */
    data class WebDavSetup(
        val url: String = "",
        val username: String = "",
        val reconnect: Boolean = false,
        val test: WebDavTest? = null,
        override val failure: SyncProblem? = null,
    ) : SyncDialog

    /** The folder (or Google Drive, or a WebDAV server, for [backend]) couldn't be looked at or signed in to. */
    data class FolderProblem(val problem: SyncProblem, val backend: SyncBackend? = null) : SyncDialog

    /** The passphrase of encrypted sync data that this device doesn't have the key to. */
    data class Unlock(override val failure: SyncProblem? = null) : SyncDialog

    /** After a restore: start the sync data again from this collection. [otherDevices] are the names that will have to join again. */
    data class Upload(
        val otherDevices: List<String>,
        override val failure: SyncProblem? = null,
        /** The location is Google Drive, where encryption is on unless the user switches it off. */
        val encryptByDefault: Boolean = false,
    ) : SyncDialog

    /** Take the location's data again, replacing this collection (saved first). */
    data class ConfirmRejoin(override val failure: SyncProblem? = null) : SyncDialog

    data object ConfirmLeave : SyncDialog

    /** Deletes the sync data for every device. */
    data class ConfirmDelete(val otherDevices: List<String>, override val failure: SyncProblem? = null) : SyncDialog
}

/** What "Test connection" found out about a WebDAV server. */
sealed interface WebDavTest {
    /** The server answered, the credentials work and the folder is there. */
    data object FolderFound : WebDavTest

    /** The same, but the folder is made when sync is set up. */
    data object FolderWillBeCreated : WebDavTest

    data class Failed(val problem: SyncProblem) : WebDavTest
}

/** Something that happened that the screen keeps saying until it is dismissed. */
sealed interface SyncNotice {
    /** This device now has the location's collection; what it had is at [safetyBackup], if it had anything. */
    data class Joined(val safetyBackup: String?) : SyncNotice
}

data class SyncUiState(
    val status: SyncStatus = SyncStatus.Off,
    val pendingChanges: Int = 0,
    val encrypted: Boolean = false,
    val devices: SyncDevices = SyncDevices.Loading,
    val dialog: SyncDialog? = null,
    /** A set-up step is running: the buttons wait. */
    val working: Boolean = false,
    /** The step is waiting for the user in the browser (Google sign-in): they can cancel it. */
    val signingIn: Boolean = false,
    /** This build can use Google Drive, so the screen offers it. */
    val googleDriveAvailable: Boolean = false,
    val notice: SyncNotice? = null,
)

/** Why a passphrase can't be used. */
enum class PassphraseProblem { Empty, TooShort, Mismatch }

/** The rules for a new passphrase: sync data is only as private as it is. */
object SyncPassphrase {
    const val MIN_LENGTH = 8

    fun check(passphrase: String, confirmation: String): PassphraseProblem? = when {
        passphrase.isBlank() -> PassphraseProblem.Empty
        passphrase.length < MIN_LENGTH -> PassphraseProblem.TooShort
        passphrase != confirmation -> PassphraseProblem.Mismatch
        else -> null
    }
}

/**
 * Settings › Sync (docs/sync/ROADMAP.md S5): turns the lifecycle of [SyncRepository] into dialogs and one state. Passphrases
 * live only in the arguments of the calls and, while the user answers "replace this device?", in [pendingPassphrase];
 * they are never part of the state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModel(private val repository: SyncRepository) : ViewModel() {
    private val dialog = MutableStateFlow<SyncDialog?>(null)
    private val working = MutableStateFlow(false)
    private val notice = MutableStateFlow<SyncNotice?>(null)
    private var pendingPassphrase: CharArray? = null

    private val status = repository.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.Off)

    // The devices and the encryption are read again whenever a round has finished (the last sync time changed) or the
    // location changed.
    private val details = status
        .map { it.backend to it.lastSyncAt }
        .distinctUntilChanged()
        .mapLatest { (backend, _) ->
            if (backend == null) {
                false to SyncDevices.Unavailable
            } else {
                repository.isEncrypted() to loadDevices()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false to SyncDevices.Loading)

    private suspend fun loadDevices(): SyncDevices = try {
        SyncDevices.Loaded(repository.devices())
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        SyncDevices.Unavailable
    }

    private val signingIn = MutableStateFlow(false)
    private var signInJob: Job? = null

    private class Controls(val dialog: SyncDialog?, val working: Boolean, val signingIn: Boolean, val notice: SyncNotice?)

    private val controls = combine(dialog, working, signingIn, notice, ::Controls)

    val uiState: StateFlow<SyncUiState> = combine(status, repository.pendingChanges, details, controls) { s, pending, (encrypted, devices), c ->
        SyncUiState(s, pending, encrypted, devices, c.dialog, c.working, c.signingIn, repository.googleDriveAvailable, c.notice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState(googleDriveAvailable = repository.googleDriveAvailable))

    // --- set up ---------------------------------------------------------------------------------------------------

    /** The user picked a folder: look at it, then ask the right question. */
    fun onFolderPicked(location: String) {
        val backend = SyncBackend.Folder(location)
        launchWorking { askAbout(backend) }
    }

    /** The user chose Google Drive: sign in in the browser, then look at the Drive folder and ask the right question. */
    fun useGoogleDrive() {
        if (!repository.googleDriveAvailable) return
        signIn { askAbout(SyncBackend.GoogleDrive) }
    }

    /** The user chose a WebDAV server: ask for its address and account. */
    fun useWebDav() {
        dialog.value = SyncDialog.WebDavSetup()
    }

    /** "Test connection": reaches the server with what was typed and says what it found. Nothing is kept or changed. */
    fun testWebDav(url: String, username: String, password: String) {
        val open = dialog.value as? SyncDialog.WebDavSetup ?: return
        launchWorking {
            val result = try {
                when (repository.testWebDav(url, username, password)) {
                    WebDavTestResult.FolderFound -> WebDavTest.FolderFound
                    WebDavTestResult.FolderWillBeCreated -> WebDavTest.FolderWillBeCreated
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WebDavTest.Failed(e.syncProblem())
            }
            dialog.value = open.copy(url = url, username = username, test = result, failure = null)
        }
    }

    /** "Continue": checks the account, makes the folder if needed and keeps the password; then looks at the folder like for any other. */
    fun connectWebDav(url: String, username: String, password: String) {
        val open = dialog.value as? SyncDialog.WebDavSetup ?: return
        if (open.reconnect) return updateWebDavPassword(password)
        launchWorking {
            try {
                askAbout(repository.connectWebDav(url, username, password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(url = url, username = username, test = null, failure = e.syncProblem())
            }
        }
    }

    /** After [SyncProblem.Auth] on WebDAV: ask for the password again, for the address and user this device already has. */
    fun askForWebDavPassword() {
        val backend = uiState.value.status.backend as? SyncBackend.WebDav ?: return
        dialog.value = SyncDialog.WebDavSetup(backend.url, backend.username, reconnect = true)
    }

    private fun updateWebDavPassword(password: String) {
        val open = dialog.value as? SyncDialog.WebDavSetup ?: return
        launchWorking {
            try {
                repository.updateWebDavPassword(password)
                dialog.value = null
                repository.syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(test = null, failure = e.syncProblem())
            }
        }
    }

    /** After [SyncProblem.Auth] on Google Drive: sign in again, and sync. */
    fun signInAgain() {
        signIn { repository.syncNow() }
    }

    /** Stops waiting for the browser. */
    fun cancelSignIn() {
        signInJob?.cancel()
    }

    /** Looks at what [backend] holds and opens the question that fits it. */
    private suspend fun askAbout(backend: SyncBackend) {
        dialog.value = try {
            when (val found = repository.inspect(backend)) {
                SyncLocation.Empty -> SyncDialog.Create(backend)
                is SyncLocation.SyncData -> SyncDialog.Join(backend, found.encrypted)
                SyncLocation.Leftovers -> SyncDialog.Leftovers
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncDialog.FolderProblem(e.syncProblem(), backend)
        }
    }

    /** Signs in to Google, then runs [then]. A user who gives up or cancels is not an error; anything else is a dialog. */
    private fun signIn(then: suspend () -> Unit) {
        if (working.value) return
        signInJob = viewModelScope.launch {
            working.value = true
            signingIn.value = true
            try {
                repository.signInToGoogleDrive()
                signingIn.value = false
                then()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val problem = e.syncProblem()
                if (problem != SyncProblem.SignInCancelled) dialog.value = SyncDialog.FolderProblem(problem, SyncBackend.GoogleDrive)
            } finally {
                signingIn.value = false
                working.value = false
            }
        }
    }

    /** Starts the sync data in the empty folder; with a [passphrase] it is encrypted. The caller has checked it with [SyncPassphrase]. */
    fun create(passphrase: String?) {
        val open = dialog.value as? SyncDialog.Create ?: return
        launchWorking {
            try {
                repository.create(open.backend, passphrase?.toCharArray())
                dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(failure = e.syncProblem())
            }
        }
    }

    /** Uses the folder's sync data on this device. */
    fun join(passphrase: String?) {
        val open = dialog.value as? SyncDialog.Join ?: return
        launchWorking {
            try {
                val result = repository.join(open.backend, passphrase?.toCharArray(), replaceLocalData = false)
                dialog.value = null
                notice.value = SyncNotice.Joined(result.safetyBackup)
            } catch (_: SyncReplacesLocalDataException) {
                pendingPassphrase = passphrase?.toCharArray()
                dialog.value = SyncDialog.ConfirmReplace(open.backend)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(failure = e.syncProblem())
            }
        }
    }

    /** The user agreed to replace what is on this device. */
    fun confirmReplace() {
        val open = dialog.value as? SyncDialog.ConfirmReplace ?: return
        val passphrase = pendingPassphrase
        launchWorking {
            try {
                val result = repository.join(open.backend, passphrase, replaceLocalData = true)
                pendingPassphrase = null
                dialog.value = null
                notice.value = SyncNotice.Joined(result.safetyBackup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(failure = e.syncProblem())
            }
        }
    }

    // --- running ----------------------------------------------------------------------------------------------------

    /** Syncs now. How it went is in the status. */
    fun syncNow() {
        viewModelScope.launch { repository.syncNow() }
    }

    // --- after a problem or a restore ---------------------------------------------------------------------------------

    fun askForPassphrase() {
        dialog.value = SyncDialog.Unlock()
    }

    fun unlock(passphrase: String) {
        if (dialog.value !is SyncDialog.Unlock) return
        launchWorking {
            try {
                repository.unlock(passphrase.toCharArray())
                dialog.value = null
                repository.syncNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = SyncDialog.Unlock(e.syncProblem())
            }
        }
    }

    fun askToRejoin() {
        dialog.value = SyncDialog.ConfirmRejoin()
    }

    fun confirmRejoin() {
        if (dialog.value !is SyncDialog.ConfirmRejoin) return
        launchWorking {
            try {
                val result = repository.rejoin()
                dialog.value = null
                notice.value = SyncNotice.Joined(result.safetyBackup)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = SyncDialog.ConfirmRejoin(e.syncProblem())
            }
        }
    }

    /** After a restore: asks before the sync data is started again from this collection, naming the devices that would have to join again. */
    fun askToUploadAsNew() {
        viewModelScope.launch {
            val others = (uiState.value.devices as? SyncDevices.Loaded)?.devices.orEmpty().filterNot { it.isThisDevice }.map { it.name }
            dialog.value = SyncDialog.Upload(others, encryptByDefault = uiState.value.status.backend.isCloud)
        }
    }

    /** Starts the sync data again from this collection, encrypted if there is a [passphrase]. */
    fun uploadAsNew(passphrase: String?) {
        val open = dialog.value as? SyncDialog.Upload ?: return
        launchWorking {
            try {
                repository.uploadAsNew(passphrase?.toCharArray())
                dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(failure = e.syncProblem())
            }
        }
    }

    // --- leaving ------------------------------------------------------------------------------------------------------

    fun askToLeave() {
        dialog.value = SyncDialog.ConfirmLeave
    }

    fun confirmLeave() {
        if (dialog.value !is SyncDialog.ConfirmLeave) return
        launchWorking {
            repository.leave()
            dialog.value = null
        }
    }

    fun askToDelete() {
        val others = (uiState.value.devices as? SyncDevices.Loaded)?.devices.orEmpty().filterNot { it.isThisDevice }.map { it.name }
        dialog.value = SyncDialog.ConfirmDelete(others)
    }

    fun confirmDelete() {
        val open = dialog.value as? SyncDialog.ConfirmDelete ?: return
        launchWorking {
            try {
                repository.deleteSyncData()
                dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dialog.value = open.copy(failure = e.syncProblem())
            }
        }
    }

    fun dismissDialog() {
        pendingPassphrase = null
        dialog.value = null
    }

    fun dismissNotice() {
        notice.value = null
    }

    // --- plumbing -----------------------------------------------------------------------------------------------------

    private fun launchWorking(block: suspend () -> Unit) {
        if (working.value) return
        viewModelScope.launch {
            working.value = true
            try {
                block()
            } finally {
                working.value = false
            }
        }
    }
}

/** Google Drive and WebDAV servers belong to someone else (Google, whoever runs the server): sync data there is encrypted unless the user opts out. */
internal val SyncBackend?.isCloud: Boolean get() = this == SyncBackend.GoogleDrive || this is SyncBackend.WebDav
