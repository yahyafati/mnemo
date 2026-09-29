package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.Flow

/**
 * Fits FSRS weights to the review log in the background (ADR 0007). Applied weights land in
 * [UserSettingsRepository]; reset them there with `setFsrsWeights(null)`.
 */
interface FsrsOptimizationRepository {
    /** The newest (or still running) optimization. */
    val state: Flow<TransferState<FsrsOptimizationOutcome>>

    /** Starts fitting, unless a run is already going. */
    fun startOptimization()

    /** Forgets finished runs, so their result stops showing. */
    fun clearFinished()
}
