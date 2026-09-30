package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.data.repository.FsrsOptimizationRepository
import com.yahyafati.mnemo.core.data.scheduling.FsrsOptimization
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** [FsrsOptimizationRepository] on coroutines: one run at a time, a second click while it runs does nothing. */
internal class DesktopFsrsOptimizationRepository(
    scope: CoroutineScope,
    private val optimization: FsrsOptimization,
) : FsrsOptimizationRepository {
    private val queue = TransferQueue<FsrsOptimizationOutcome>(scope)

    override val state: Flow<TransferState<FsrsOptimizationOutcome>> = queue.state

    override fun startOptimization() {
        queue.enqueueIfIdle { report ->
            var reported = -1
            optimization.run { progress ->
                // Every whole percent at most: each one is a state change for the screen.
                val percent = (progress * 100).toInt()
                if (percent > reported) {
                    reported = percent
                    report(progress)
                }
            }
        }
    }

    override fun clearFinished() = queue.clearFinished()
}
