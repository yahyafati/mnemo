package com.yahyafati.mnemo.core.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yahyafati.mnemo.core.data.sync.SyncRepository

/**
 * One sync round while the app isn't open (docs/sync/ROADMAP.md S4). It always succeeds: a round that couldn't reach the
 * location is in `SyncRepository.status`, and the periodic work runs again by itself, so a retry with backoff would only
 * spend battery.
 */
internal class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val repository: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        repository.syncNow()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "sync"
    }
}
