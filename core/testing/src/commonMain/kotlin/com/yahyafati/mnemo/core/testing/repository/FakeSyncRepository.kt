package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.sync.JoinResult
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncLocation
import com.yahyafati.mnemo.core.data.sync.SyncReport
import com.yahyafati.mnemo.core.data.sync.SyncRepository
import com.yahyafati.mnemo.core.data.sync.SyncResult
import com.yahyafati.mnemo.core.data.sync.SyncReplacesLocalDataException
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.data.sync.backend
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SyncRepository] for screens and ViewModels: [status] is whatever the test sets, the calls change it the way the
 * real ones do (create and join turn sync on, leave turns it off) and are counted.
 */
class FakeSyncRepository(initial: SyncStatus = SyncStatus.Off) : SyncRepository {
    val statusState = MutableStateFlow(initial)
    override val status = statusState

    val pending = MutableStateFlow(0)
    override val pendingChanges = pending

    var syncs = 0
        private set
    var left = 0
        private set

    /** What [inspect] says every location holds. */
    var location: SyncLocation = SyncLocation.Empty

    /** What [devices] returns, and whether [isEncrypted] says yes. */
    var deviceList: List<SyncDeviceSummary> = emptyList()
    var encrypted = false

    /** What the calls were given, in order: the passphrase as a string, or null for none. */
    class Call(val name: String, val backend: SyncBackend? = null, val passphrase: String? = null, val replaceLocalData: Boolean = false)

    val calls = mutableListOf<Call>()

    /** Thrown by [inspect] and [devices], once. */
    var readFailure: Exception? = null

    /** Joining throws `SyncReplacesLocalDataException` until `replaceLocalData` is true. */
    var hasLocalData = false

    /** Thrown by the next create, join, rejoin, unlock or uploadAsNew, once. */
    var failure: Exception? = null

    private fun fail() {
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun inspect(backend: SyncBackend): SyncLocation {
        calls += Call("inspect", backend)
        readFailure?.let {
            readFailure = null
            throw it
        }
        return location
    }

    override suspend fun isEncrypted(): Boolean = encrypted

    override suspend fun devices(): List<SyncDeviceSummary> {
        readFailure?.let {
            readFailure = null
            throw it
        }
        return deviceList
    }

    /** What the next [syncNow] returns, instead of the default. */
    var syncResult: SyncResult? = null

    override suspend fun syncNow(): SyncResult {
        syncs++
        syncResult?.let { return it }
        return if (statusState.value is SyncStatus.Off) SyncResult.NotSyncing else SyncResult.Done(SyncReport())
    }

    override suspend fun create(backend: SyncBackend, passphrase: CharArray?) {
        calls += Call("create", backend, passphrase?.concatToString())
        fail()
        encrypted = passphrase != null
        statusState.value = SyncStatus.Idle(backend, null)
    }

    override suspend fun join(backend: SyncBackend, passphrase: CharArray?, replaceLocalData: Boolean): JoinResult {
        calls += Call("join", backend, passphrase?.concatToString(), replaceLocalData)
        fail()
        if (hasLocalData && !replaceLocalData) throw SyncReplacesLocalDataException()
        statusState.value = SyncStatus.Idle(backend, null)
        return JoinResult(if (hasLocalData) "file:///before-sync.zip" else null)
    }

    override suspend fun rejoin(passphrase: CharArray?): JoinResult {
        calls += Call("rejoin", passphrase = passphrase?.concatToString())
        fail()
        val backend = statusState.value.backend ?: return JoinResult(null)
        statusState.value = SyncStatus.Idle(backend, null)
        return JoinResult("file:///before-sync.zip")
    }

    override suspend fun uploadAsNew(passphrase: CharArray?) {
        calls += Call("uploadAsNew", passphrase = passphrase?.concatToString())
        fail()
        val backend = statusState.value.backend ?: return
        statusState.value = SyncStatus.Idle(backend, null)
    }

    override suspend fun unlock(passphrase: CharArray) {
        calls += Call("unlock", passphrase = passphrase.concatToString())
        fail()
        val current = statusState.value
        if (current is SyncStatus.Error) statusState.value = SyncStatus.Idle(current.backend, current.lastSyncAt)
    }

    override suspend fun leave() {
        calls += Call("leave")
        left++
        statusState.value = SyncStatus.Off
    }

    override suspend fun deleteSyncData() {
        calls += Call("deleteSyncData")
        statusState.value = SyncStatus.Off
    }
}
