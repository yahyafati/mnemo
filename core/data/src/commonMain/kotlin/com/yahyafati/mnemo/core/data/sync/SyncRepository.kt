package com.yahyafati.mnemo.core.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * Syncing this device's collection with a location of the user's (docs/sync/ROADMAP.md S4, ADR 0013): the lifecycle
 * around the merge engine, which only knows how to run one round. One at a time: two rounds never overlap, and
 * creating, joining and leaving wait for a round that is running.
 *
 * Failures of the location are `SyncException`s thrown by the calls that set something up ([create], [join],
 * [unlock], [rejoin], [uploadAsNew]); the rounds ([syncNow]) report them as a [SyncResult] and in [status] instead.
 */
interface SyncRepository {
    /** What sync is doing. Always has a value. */
    val status: Flow<SyncStatus>

    /** How many local changes wait to be sent (0 while sync is off). */
    val pendingChanges: Flow<Int>

    /** Syncs once, now. Does nothing if sync is off or a round is already running. */
    suspend fun syncNow(): SyncResult

    /**
     * Starts the sync data in an **empty** location with this device's collection, encrypted if there is a [passphrase].
     * A location that has sync data (or anything of Mnemo's) is `SyncAlreadyExistsException`: that is [join]. Fails
     * with `IllegalStateException` if sync is on.
     */
    suspend fun create(backend: SyncBackend, passphrase: CharArray?)

    /**
     * Makes this device's collection a copy of the location's. A device with data would lose it, so it throws
     * [SyncReplacesLocalDataException] until the caller passes [replaceLocalData] (after asking the user); the old
     * collection is then saved first (see [JoinResult]). `SyncPassphraseException` for an encrypted location and a
     * missing or wrong [passphrase]; `SyncNotFoundException` for one with no sync data.
     */
    suspend fun join(backend: SyncBackend, passphrase: CharArray?, replaceLocalData: Boolean = false): JoinResult

    /**
     * Joins the location this device remembers again, replacing the collection (after saving it): what to do after a
     * restore that the user wants to discard ([SyncStatus.Restored]), after [SyncProblem.MustRejoin] and after
     * [SyncProblem.Replaced]. Uses the stored key unless a [passphrase] is given.
     */
    suspend fun rejoin(passphrase: CharArray? = null): JoinResult

    /**
     * Empties the remembered location and starts the sync data there again from this collection: the other choice
     * after a restore. The other devices find out at their next round ([SyncProblem.Replaced]) and join again.
     */
    suspend fun uploadAsNew(passphrase: CharArray?)

    /** Gives this device the key of an encrypted location after [SyncProblem.PassphraseRequired] or [SyncProblem.PassphraseWrong]. */
    suspend fun unlock(passphrase: CharArray)

    /** Turns sync off on this device. The collection stays; the location is not touched, other than saying this device left. */
    suspend fun leave()

    /** Removes every file of the remembered location, for every device, then leaves. */
    suspend fun deleteSyncData()
}

/** Joining would replace a collection that has data; ask the user, then call again with `replaceLocalData = true`. */
class SyncReplacesLocalDataException : Exception("Joining replaces the decks and notes on this device")

/** What this device says about itself in the location. */
fun interface DeviceDescriber {
    fun describe(): DeviceDescription
}

/** The syncing that goes on while the app isn't open: WorkManager on Android, nothing on the desktop (its timer runs in the app). */
interface SyncBackgroundWork {
    /** Makes sure the periodic sync is scheduled. Safe to call again. */
    fun schedule()

    fun cancel()
}
