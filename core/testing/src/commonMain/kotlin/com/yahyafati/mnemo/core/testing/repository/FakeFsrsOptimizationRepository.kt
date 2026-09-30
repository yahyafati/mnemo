package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.data.repository.FsrsOptimizationRepository
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.MutableStateFlow

/** Counts starts; tests drive [state] directly. */
class FakeFsrsOptimizationRepository : FsrsOptimizationRepository {
    override val state = MutableStateFlow<TransferState<FsrsOptimizationOutcome>>(TransferState.Idle)
    var starts = 0
        private set

    override fun startOptimization() {
        starts++
        state.value = TransferState.Running(null)
    }

    override fun clearFinished() {
        if (state.value is TransferState.Succeeded || state.value is TransferState.Failed) state.value = TransferState.Idle
    }
}
