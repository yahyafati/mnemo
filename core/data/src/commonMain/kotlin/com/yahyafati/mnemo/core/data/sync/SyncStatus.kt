package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncAuthException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import com.yahyafati.mnemo.core.sync.SyncQuotaException
import com.yahyafati.mnemo.core.sync.SyncUnsupportedVersionException
import java.time.Duration
import java.time.Instant

/** Why a sync can't go on until something changes. What the user can do about it is the screen's to say (S5). */
enum class SyncProblem {
    /** The location can't be reached (no network, a drive that isn't connected). Retry later; not an error. */
    Offline,

    /** The location refuses this device: a revoked folder permission, a sign-in that expired. */
    Auth,

    /** There is no room left in the location. */
    Quota,

    /** The location is encrypted and this device has no key for it: ask for the passphrase ([SyncRepository.unlock]). */
    PassphraseRequired,

    /** The passphrase that was given is wrong. */
    PassphraseWrong,

    /** The sync data is in a newer format: "Update Mnemo on this device to keep syncing". */
    UpdateRequired,

    /** The location has no sync data any more (deleted, or the folder is another one). */
    LocationGone,

    /** The sync data was started again by another device ([SyncRepository.uploadAsNew]); this device has to join again. */
    Replaced,

    /** This device was away so long that the change files it needs were cleaned up: it has to join again. */
    MustRejoin,

    /** Creating the sync data needs an empty location, and this one has files in it. */
    LocationNotEmpty,

    /** Anything else; the message says what. */
    Other,
}

/** What the sync is doing, for Settings and the indicators (docs/sync/ROADMAP.md S4). */
sealed interface SyncStatus {
    /** Sync was never set up on this device, or it left. */
    data object Off : SyncStatus

    /**
     * This device remembers a location but is not syncing with it. That is what a restored backup leaves: the user
     * chooses between uploading this collection as the new sync data ([SyncRepository.uploadAsNew]) and taking the
     * location's data again ([SyncRepository.rejoin]), or leaves.
     */
    data class Restored(val backend: SyncBackend) : SyncStatus

    /** Syncing is on and nothing is running. [lastSyncAt] is null until the first round has finished. */
    data class Idle(val backend: SyncBackend, val lastSyncAt: Instant?) : SyncStatus

    data class Syncing(val backend: SyncBackend, val lastSyncAt: Instant?) : SyncStatus

    /** The last round couldn't reach the location. The next trigger tries again. */
    data class WaitingForNetwork(val backend: SyncBackend, val lastSyncAt: Instant?) : SyncStatus

    /** The last round stopped for [problem]; [message] is a detail for the log, not for the user. */
    data class Error(val backend: SyncBackend, val problem: SyncProblem, val lastSyncAt: Instant?, val message: String? = null) : SyncStatus
}

/** True when this device records and sends changes. */
val SyncStatus.isOn: Boolean
    get() = this !is SyncStatus.Off && this !is SyncStatus.Restored

/** The location of a status that has one. */
val SyncStatus.backend: SyncBackend?
    get() = when (this) {
        SyncStatus.Off -> null
        is SyncStatus.Restored -> backend
        is SyncStatus.Idle -> backend
        is SyncStatus.Syncing -> backend
        is SyncStatus.WaitingForNetwork -> backend
        is SyncStatus.Error -> backend
    }

/** When the last round finished, if one ever did. */
val SyncStatus.lastSyncAt: Instant?
    get() = when (this) {
        SyncStatus.Off, is SyncStatus.Restored -> null
        is SyncStatus.Idle -> lastSyncAt
        is SyncStatus.Syncing -> lastSyncAt
        is SyncStatus.WaitingForNetwork -> lastSyncAt
        is SyncStatus.Error -> lastSyncAt
    }

/** How long sync may fail by itself (no network, a folder that is away) before the Decks screen says so. */
val SYNC_QUIET_PERIOD: Duration = Duration.ofDays(1)

/**
 * From when the user should be told that sync needs them (docs/sync/ROADMAP.md S5), or null while nothing is wrong:
 * [Instant.EPOCH] for what only a person can fix (a restore waiting for a choice, a passphrase, a location that needs
 * joining again, a refused folder), a day after the last good round for what may mend itself (no network, anything
 * unexplained). While everything works there is no indicator at all.
 */
fun SyncStatus.attentionFrom(): Instant? = when (this) {
    SyncStatus.Off, is SyncStatus.Idle, is SyncStatus.Syncing -> null
    is SyncStatus.Restored -> Instant.EPOCH
    is SyncStatus.WaitingForNetwork -> lastSyncAt?.plus(SYNC_QUIET_PERIOD) ?: Instant.EPOCH
    is SyncStatus.Error -> when (problem) {
        SyncProblem.Offline, SyncProblem.Other -> lastSyncAt?.plus(SYNC_QUIET_PERIOD) ?: Instant.EPOCH
        else -> Instant.EPOCH
    }
}

/** What a failure of the location comes to, for the calls that set sync up and for the rounds. */
fun Throwable.syncProblem(): SyncProblem = when (this) {
    is SyncOfflineException -> SyncProblem.Offline
    is SyncAuthException -> SyncProblem.Auth
    is SyncQuotaException -> SyncProblem.Quota
    is SyncNotFoundException -> SyncProblem.LocationGone
    is SyncAlreadyExistsException -> SyncProblem.LocationNotEmpty
    is SyncPassphraseException -> if (required) SyncProblem.PassphraseRequired else SyncProblem.PassphraseWrong
    is SyncUnsupportedVersionException -> SyncProblem.UpdateRequired
    is SyncProblemException -> problem
    else -> SyncProblem.Other
}

/** What [SyncRepository.syncNow] came to. */
sealed interface SyncResult {
    data class Done(val report: SyncReport) : SyncResult

    /** Sync is off on this device. */
    data object NotSyncing : SyncResult

    /** Another round is running; this call did nothing. */
    data object Busy : SyncResult

    data class Failed(val problem: SyncProblem) : SyncResult
}

/** What a join did to this device: [safetyBackup] is where the collection it replaced was saved, or null if it was empty. */
data class JoinResult(val safetyBackup: String?)

/** What a location holds, as far as setting sync up there is concerned ([SyncRepository.inspect]). */
sealed interface SyncLocation {
    /** Nothing in it: sync data can be created here. */
    data object Empty : SyncLocation

    /** Another device (or this one, earlier) started sync data here: join it. */
    data class SyncData(val encrypted: Boolean) : SyncLocation

    /**
     * Files of an earlier sync location but no manifest (a failed create that couldn't clean up, a `sync.json` deleted by
     * hand): neither creating nor joining is possible here. Files that aren't Mnemo's at all are not seen by a folder
     * store, so a folder with only those counts as [Empty] and gets Mnemo's files beside them.
     */
    data object Leftovers : SyncLocation
}

/** A device that syncs with the location, from its `device.json`. [lastSeenAt] is that device's own clock. */
data class SyncDeviceSummary(
    val deviceId: String,
    val name: String,
    val platform: String,
    val appVersion: String,
    val lastSeenAt: Instant,
    val isThisDevice: Boolean,
)
