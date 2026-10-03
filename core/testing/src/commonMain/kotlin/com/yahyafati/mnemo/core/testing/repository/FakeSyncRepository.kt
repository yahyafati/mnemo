package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.sync.JoinResult
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncReport
import com.yahyafati.mnemo.core.data.sync.SyncRepository
import com.yahyafati.mnemo.core.data.sync.SyncResult
import com.yahyafati.mnemo.core.data.sync.SyncStatus
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

    /** Thrown by the next create, join, rejoin, unlock or uploadAsNew, once. */
    var failure: Exception? = null

    private fun fail() {
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun syncNow(): SyncResult {
        syncs++
        return if (statusState.value is SyncStatus.Off) SyncResult.NotSyncing else SyncResult.Done(SyncReport())
    }

    override suspend fun create(backend: SyncBackend, passphrase: CharArray?) {
        fail()
        statusState.value = SyncStatus.Idle(backend, null)
    }

    override suspend fun join(backend: SyncBackend, passphrase: CharArray?, replaceLocalData: Boolean): JoinResult {
        fail()
        statusState.value = SyncStatus.Idle(backend, null)
        return JoinResult(null)
    }

    override suspend fun rejoin(passphrase: CharArray?): JoinResult {
        fail()
        return JoinResult(null)
    }

    override suspend fun uploadAsNew(passphrase: CharArray?) = fail()

    override suspend fun unlock(passphrase: CharArray) = fail()

    override suspend fun leave() {
        left++
        statusState.value = SyncStatus.Off
    }

    override suspend fun deleteSyncData() {
        statusState.value = SyncStatus.Off
    }
}
