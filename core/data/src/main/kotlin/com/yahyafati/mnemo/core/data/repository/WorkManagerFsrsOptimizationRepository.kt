package com.yahyafati.mnemo.core.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.data.work.OptimizeFsrsWorker
import com.yahyafati.mnemo.core.data.work.workState
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class WorkManagerFsrsOptimizationRepository @Inject constructor(
    private val workManager: WorkManager,
) : FsrsOptimizationRepository {
    override val state: Flow<TransferState<FsrsOptimizationOutcome>> =
        workManager.workState(OptimizeFsrsWorker.UNIQUE_NAME, OptimizeFsrsWorker::outcome)

    override fun startOptimization() {
        workManager.enqueueUniqueWork(
            OptimizeFsrsWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<OptimizeFsrsWorker>().build(),
        )
    }

    override fun clearFinished() {
        workManager.pruneWork()
    }
}
