package com.yahyafati.mnemo.core.data.repository

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.data.sync.SyncBackgroundWork
import com.yahyafati.mnemo.core.data.work.SyncWorker
import java.time.Duration

/** Unique periodic work that needs a network, every few hours; kept as it is if it already exists. */
internal class WorkManagerSyncBackgroundWork(private val workManager: WorkManager) : SyncBackgroundWork {
    override fun schedule() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(Duration.ofHours(3))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_NAME)
    }
}
