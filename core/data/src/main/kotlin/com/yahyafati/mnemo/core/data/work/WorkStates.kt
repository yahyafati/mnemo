package com.yahyafati.mnemo.core.data.work

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The state of the newest (or still running) work under [uniqueName], with a successful run's
 * output read by [result] and a failed one's error from [WorkKeys.ERROR].
 */
internal fun <R> WorkManager.workState(uniqueName: String, result: (Data) -> R): Flow<TransferState<R>> =
    getWorkInfosForUniqueWorkFlow(uniqueName).map { infos ->
        val info = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
        when (info?.state) {
            null, WorkInfo.State.CANCELLED -> TransferState.Idle
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> TransferState.Running(null)
            WorkInfo.State.RUNNING -> TransferState.Running(
                info.progress.keyValueMap[WorkKeys.PROGRESS]?.let { (it as? Float)?.coerceIn(0f, 1f) },
            )
            WorkInfo.State.SUCCEEDED -> TransferState.Succeeded(result(info.outputData))
            WorkInfo.State.FAILED -> TransferState.Failed(WorkKeys.error(info.outputData))
        }
    }
