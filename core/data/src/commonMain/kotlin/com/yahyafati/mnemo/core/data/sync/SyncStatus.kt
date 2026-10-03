package com.yahyafati.mnemo.core.data.sync

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
