package com.yahyafati.mnemo.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yahyafati.mnemo.core.data.scheduling.FsrsOptimization
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.TransferError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Fits FSRS weights to the review log (Settings › Scheduling › Optimize; ADR 0007). */
@HiltWorker
internal class OptimizeFsrsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val optimization: FsrsOptimization,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        var reported = -1
        val outcome = optimization.run { progress ->
            // Every whole percent at most: progress goes through WorkManager's database.
            val percent = (progress * 100).toInt()
            if (percent > reported) {
                reported = percent
                setProgressAsync(workDataOf(WorkKeys.PROGRESS to progress))
            }
        }
        Result.success(outcome.toData())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(workDataOf(WorkKeys.ERROR to TransferError.Unknown.name))
    }

    companion object {
        const val UNIQUE_NAME = "fsrs-optimize"

        private const val OUTCOME = "outcome"
        private const val REVIEWS = "reviews"
        private const val REQUIRED = "required"
        private const val PREVIOUS_LOSS = "previousLoss"
        private const val LOSS = "loss"

        private fun FsrsOptimizationOutcome.toData(): Data = when (this) {
            is FsrsOptimizationOutcome.Applied ->
                workDataOf(OUTCOME to "applied", REVIEWS to trainingReviews, PREVIOUS_LOSS to previousLoss, LOSS to loss)
            is FsrsOptimizationOutcome.NoImprovement ->
                workDataOf(OUTCOME to "unchanged", REVIEWS to trainingReviews, PREVIOUS_LOSS to previousLoss, LOSS to loss)
            is FsrsOptimizationOutcome.NotEnoughReviews ->
                workDataOf(OUTCOME to "too-few", REVIEWS to trainingReviews, REQUIRED to required)
        }

        /** The outcome a finished run stored with [toData]. */
        fun outcome(data: Data): FsrsOptimizationOutcome {
            val reviews = data.getInt(REVIEWS, 0)
            val previousLoss = data.getDouble(PREVIOUS_LOSS, Double.NaN)
            val loss = data.getDouble(LOSS, Double.NaN)
            return when (data.getString(OUTCOME)) {
                "applied" -> FsrsOptimizationOutcome.Applied(reviews, previousLoss, loss)
                "unchanged" -> FsrsOptimizationOutcome.NoImprovement(reviews, previousLoss, loss)
                else -> FsrsOptimizationOutcome.NotEnoughReviews(reviews, data.getInt(REQUIRED, 0))
            }
        }
    }
}
