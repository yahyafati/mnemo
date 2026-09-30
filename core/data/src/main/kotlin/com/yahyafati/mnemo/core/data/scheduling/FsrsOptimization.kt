package com.yahyafati.mnemo.core.data.scheduling

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.database.dao.ReviewLogDao
import com.yahyafati.mnemo.core.model.FsrsOptimizationOutcome
import com.yahyafati.mnemo.core.model.FsrsWeights
import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import com.yahyafati.mnemo.core.scheduler.optimizer.FsrsOptimizer
import com.yahyafati.mnemo.core.scheduler.optimizer.FsrsReviewHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * One optimizer run (ADR 0007): reads every card's review history, fits the FSRS weights, and
 * applies them only if they predict that history better than the weights in use.
 */
internal class FsrsOptimization(
    private val reviewLogDao: ReviewLogDao,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
    private val dispatcher: CoroutineDispatcher,
) {
    /** Runs the optimizer; [onProgress] gets 0–1. Cancelling the caller stops it between steps. */
    suspend fun run(onProgress: (Float) -> Unit = {}): FsrsOptimizationOutcome {
        val histories = readHistories()
        val optimizer = FsrsOptimizer()
        val reviews = optimizer.trainingReviewCount(histories)
        if (reviews < FsrsOptimizer.MIN_TRAINING_REVIEWS) {
            return FsrsOptimizationOutcome.NotEnoughReviews(reviews, FsrsOptimizer.MIN_TRAINING_REVIEWS)
        }
        val current = settingsRepository.settings.first().fsrsWeights?.values ?: FsrsParameters.DEFAULT_WEIGHTS
        val context = coroutineContext
        val (result, previousLoss) = withContext(dispatcher) {
            val fitted = checkNotNull(
                optimizer.optimize(histories) { progress ->
                    context.ensureActive()
                    onProgress(progress)
                },
            )
            fitted to optimizer.loss(current, histories)
        }
        if (result.loss >= previousLoss) {
            return FsrsOptimizationOutcome.NoImprovement(reviews, previousLoss, result.loss)
        }
        settingsRepository.setFsrsWeights(
            FsrsWeights(
                values = result.weights,
                optimizedAt = clock.now(),
                trainingReviews = reviews,
                previousLoss = previousLoss,
                loss = result.loss,
            ),
        )
        return FsrsOptimizationOutcome.Applied(reviews, previousLoss, result.loss)
    }

    /**
     * Every card's reviews, read a few hundred cards at a time and kept as compact arrays, so a
     * large log never sits in memory as rows. Only the first reviews of each card are kept.
     */
    private suspend fun readHistories(): List<FsrsReviewHistory> =
        reviewLogDao.getReviewedCardIds().chunked(CARDS_PER_READ).flatMap { cardIds ->
            reviewLogDao.getReviewPoints(cardIds).groupBy { it.cardId }.values.map { points ->
                val kept = points.take(FsrsOptimizer.MAX_SEQUENCE_LENGTH)
                FsrsReviewHistory(
                    reviewedAt = LongArray(kept.size) { kept[it].reviewedAt },
                    ratings = IntArray(kept.size) { kept[it].rating },
                )
            }
        }

    private companion object {
        // Below SQLite's bound-variable limit on older Android versions.
        const val CARDS_PER_READ = 500
    }
}
