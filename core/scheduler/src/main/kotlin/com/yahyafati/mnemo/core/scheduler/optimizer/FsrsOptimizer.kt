package com.yahyafati.mnemo.core.scheduler.optimizer

import com.yahyafati.mnemo.core.scheduler.FsrsParameters
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One card's reviews for the optimizer, oldest first: when (epoch millis) and the rating (1 Again
 * … 4 Easy). Only the first [FsrsOptimizer.MAX_SEQUENCE_LENGTH] are used.
 */
class FsrsReviewHistory(
    internal val reviewedAt: LongArray,
    internal val ratings: IntArray,
) {
    init {
        require(reviewedAt.isNotEmpty()) { "A history needs at least one review" }
        require(reviewedAt.size == ratings.size) { "Expected one rating per review" }
        require(ratings.all { it in 1..4 }) { "Ratings are 1 (Again) to 4 (Easy)" }
        require((1 until reviewedAt.size).all { reviewedAt[it - 1] <= reviewedAt[it] }) { "Reviews must be sorted by time" }
    }

    val size: Int get() = reviewedAt.size
}

/** The fitted weights and their mean log loss on the history they were fitted to. */
data class FsrsOptimizerResult(
    val weights: List<Double>,
    val loss: Double,
    /** Reviews the loss is measured on (see [FsrsOptimizer.trainingReviewCount]). */
    val trainingReviews: Int,
)

/**
 * Fits the 21 FSRS-6 weights to a user's review history, on device (ADR 0007).
 *
 * A port of py-fsrs 6's `Optimizer.compute_optimal_parameters`, checked against it in
 * `FsrsOptimizerTest`. FSRS is treated as a sequence model: each card's reviews are replayed from
 * the defaults' first-review state, the predicted retrievability at every review made at least a
 * day after the previous one is scored against whether the card was recalled (binary cross
 * entropy), and the weights follow Adam with cosine learning-rate annealing: 5 epochs of
 * 512-review mini-batches, the card order shuffled each epoch, weights clipped to their bounds
 * after each step. The epoch with the lowest loss on the whole history wins.
 *
 * py-fsrs gets gradients from PyTorch's autograd. Here they are forward-mode: every card state
 * carries its stability's and difficulty's partial derivatives with respect to all 21 weights,
 * updated with hand-derived formulas at each review. That gives the same gradients (mini-batch
 * truncation included) with no autodiff library, in a few microseconds per review.
 *
 * Memory state (stability, difficulty) doesn't depend on learning steps, desired retention or
 * fuzz, so only review times and ratings are needed.
 */
class FsrsOptimizer {
    /**
     * Reviews that count for the loss: every one made at least a whole day after the card's
     * previous review, within the first [MAX_SEQUENCE_LENGTH] of each card. [optimize] needs at
     * least [MIN_TRAINING_REVIEWS].
     */
    fun trainingReviewCount(histories: List<FsrsReviewHistory>): Int = histories.sumOf { history ->
        (1 until min(history.size, MAX_SEQUENCE_LENGTH)).count { floorDays(history.reviewedAt[it - 1], history.reviewedAt[it]) > 0 }
    }

    /** Mean log loss of [weights] on [histories]: lower means better predictions. NaN with no training reviews. */
    fun loss(weights: List<Double>, histories: List<FsrsReviewHistory>): Double {
        val model = Model(weights.toDoubleArray())
        val card = CardState()
        var sum = 0.0
        var count = 0
        for (history in histories) {
            card.reset()
            for (i in 0 until min(history.size, MAX_SEQUENCE_LENGTH)) {
                val time = history.reviewedAt[i]
                val rating = history.ratings[i]
                if (card.seen) {
                    val days = floorDays(card.lastReview, time)
                    if (days > 0) {
                        sum += logLoss(model.retrievability(card, days, null), rating)
                        count++
                    }
                }
                model.review(card, time, rating, track = false)
            }
        }
        return sum / count
    }

    /**
     * Fits the weights to [histories], starting from the FSRS-6 defaults, or returns null when
     * there are fewer than [MIN_TRAINING_REVIEWS] training reviews. [onProgress] gets 0–1 after
     * each step; it may throw to cancel.
     */
    fun optimize(
        histories: List<FsrsReviewHistory>,
        random: Random = Random(SEED),
        onProgress: (Float) -> Unit = {},
    ): FsrsOptimizerResult? = optimize(histories, { _, cards -> cards.shuffle(random) }, onProgress)

    /** [optimize] with the card order of each epoch chosen by [shuffle] (the reference test replays py-fsrs's). */
    internal fun optimize(
        histories: List<FsrsReviewHistory>,
        shuffle: (epoch: Int, cards: MutableList<FsrsReviewHistory>) -> Unit,
        onProgress: (Float) -> Unit,
    ): FsrsOptimizerResult? {
        val reviews = trainingReviewCount(histories)
        if (reviews < MIN_TRAINING_REVIEWS) return null

        val weights = FsrsParameters.DEFAULT_WEIGHTS.toDoubleArray()
        val adam = Adam(weights.size)
        val learningRate = CosineAnnealing(LEARNING_RATE, totalSteps = ceil(reviews / BATCH_SIZE.toDouble()).toInt() * EPOCHS)
        val gradient = DoubleArray(weights.size)
        val dR = DoubleArray(weights.size)
        val card = CardState()
        val order = histories.toMutableList()
        var best: DoubleArray? = null
        var bestLoss = Double.POSITIVE_INFINITY

        fun step() {
            adam.step(weights, gradient, learningRate.current)
            clip(weights)
            learningRate.advance()
            gradient.fill(0.0)
            onProgress(learningRate.steps.toFloat() / learningRate.totalSteps)
        }

        repeat(EPOCHS) { epoch ->
            shuffle(epoch, order)
            var model = Model(weights.copyOf())
            var inBatch = 0
            for (history in order) {
                card.reset()
                for (i in 0 until min(history.size, MAX_SEQUENCE_LENGTH)) {
                    val time = history.reviewedAt[i]
                    val rating = history.ratings[i]
                    if (card.seen) {
                        val days = floorDays(card.lastReview, time)
                        if (days > 0) {
                            val r = model.retrievability(card, days, dR)
                            val dLoss = logLossDerivative(r, rating)
                            for (j in gradient.indices) gradient[j] += dLoss * dR[j]
                            inBatch++
                        }
                    }
                    model.review(card, time, rating, track = true)
                    if (inBatch == BATCH_SIZE) {
                        step()
                        model = Model(weights.copyOf())
                        inBatch = 0
                        // Truncated backpropagation: the card carries on with the new weights, and
                        // its history so far no longer contributes to the gradient.
                        card.detach()
                    }
                }
            }
            if (inBatch > 0) step()

            val epochLoss = loss(weights.toList(), histories)
            if (epochLoss < bestLoss) {
                bestLoss = epochLoss
                best = weights.copyOf()
            }
        }
        return FsrsOptimizerResult(checkNotNull(best).toList(), bestLoss, reviews)
    }

    /**
     * The mean log loss of [weights] on [histories] and its gradient, with every card's whole
     * history in the gradient (no mini-batch truncation). For checking the derivatives.
     */
    internal fun lossAndGradient(weights: List<Double>, histories: List<FsrsReviewHistory>): Pair<Double, DoubleArray> {
        val model = Model(weights.toDoubleArray())
        val card = CardState()
        val gradient = DoubleArray(weights.size)
        val dR = DoubleArray(weights.size)
        var sum = 0.0
        var count = 0
        for (history in histories) {
            card.reset()
            for (i in 0 until min(history.size, MAX_SEQUENCE_LENGTH)) {
                val time = history.reviewedAt[i]
                val rating = history.ratings[i]
                if (card.seen) {
                    val days = floorDays(card.lastReview, time)
                    if (days > 0) {
                        val r = model.retrievability(card, days, dR)
                        sum += logLoss(r, rating)
                        val dLoss = logLossDerivative(r, rating)
                        for (j in gradient.indices) gradient[j] += dLoss * dR[j]
                        count++
                    }
                }
                model.review(card, time, rating, track = true)
            }
        }
        for (j in gradient.indices) gradient[j] /= count
        return sum / count to gradient
    }

    companion object {
        /** Fewer training reviews than one mini-batch: the defaults are kept (as in py-fsrs). */
        const val MIN_TRAINING_REVIEWS = 512

        /** Only a card's first reviews are used. */
        const val MAX_SEQUENCE_LENGTH = 64

        private const val EPOCHS = 5
        private const val BATCH_SIZE = 512
        private const val LEARNING_RATE = 4e-2
        private const val SEED = 42
        private const val DAY_MS = 86_400_000L

        private fun floorDays(from: Long, to: Long): Long = Math.floorDiv(to - from, DAY_MS)

        private fun clip(weights: DoubleArray) {
            for (i in weights.indices) {
                weights[i] = weights[i].coerceIn(FsrsParameters.LOWER_BOUNDS[i], FsrsParameters.UPPER_BOUNDS[i])
            }
        }

        // Binary cross entropy against "recalled" (anything but Again), as PyTorch's BCELoss:
        // logs clamped at -100, gradient (p - y) / max(p (1 - p), 1e-12).
        private fun logLoss(r: Double, rating: Int): Double =
            if (rating == 1) -max(ln1p(-r), -100.0) else -max(ln(r), -100.0)

        private fun logLossDerivative(r: Double, rating: Int): Double {
            val recalled = if (rating == 1) 0.0 else 1.0
            return (r - recalled) / max(r * (1 - r), 1e-12)
        }
    }
}

/** Stability and difficulty of the card being replayed, with their partials with respect to each weight. */
private class CardState {
    var seen = false
    var lastReview = 0L
    var stability = 0.0
    var difficulty = 0.0
    val dStability = DoubleArray(WEIGHTS)
    val dDifficulty = DoubleArray(WEIGHTS)

    fun reset() {
        seen = false
        detach()
    }

    fun detach() {
        dStability.fill(0.0)
        dDifficulty.fill(0.0)
    }
}

private const val WEIGHTS = 21
private const val STABILITY_MIN = 0.001
private const val MIN_DIFFICULTY = 1.0
private const val MAX_DIFFICULTY = 10.0

/**
 * The FSRS-6 memory model (the same formulas as `Fsrs`) for one set of weights, with derivatives.
 *
 * Clamps follow PyTorch: `clamp` passes the gradient on its closed range and blocks it outside;
 * `min(a, b)` picks `a` on a tie.
 */
private class Model(private val w: DoubleArray) {
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    /** d factor / d w20, with factor = 0.9^(-1/w20) - 1. */
    private val dFactor = 0.9.pow(1 / decay) * ln(0.9) / (w[20] * w[20])

    // Scratch space for the next state's partials.
    private val nextDStability = DoubleArray(WEIGHTS)
    private val nextDDifficulty = DoubleArray(WEIGHTS)
    private val dR = DoubleArray(WEIGHTS)

    /** Retrievability [days] after the last review; its partials go to [dR] when given. */
    fun retrievability(card: CardState, days: Long, dR: DoubleArray?): Double {
        val s = card.stability
        val base = 1 + factor * days / s
        val r = base.pow(decay)
        if (dR != null) {
            val dRdS = r * w[20] * factor * days / (base * s * s)
            for (j in 0 until WEIGHTS) dR[j] = dRdS * card.dStability[j]
            dR[20] += -r * ln(base) - w[20] * r / base * (days / s) * dFactor
        }
        return r
    }

    fun review(card: CardState, time: Long, rating: Int, track: Boolean) {
        if (!card.seen) {
            firstReview(card, rating, track)
        } else {
            val days = Math.floorDiv(time - card.lastReview, 86_400_000L)
            // Stability uses the difficulty from before this review.
            if (days < 1) shortTermStability(card, rating, track) else nextStability(card, days, rating, track)
            nextDifficulty(card, rating, track)
            if (track) {
                nextDStability.copyInto(card.dStability)
                nextDDifficulty.copyInto(card.dDifficulty)
            }
        }
        card.seen = true
        card.lastReview = time
    }

    private fun firstReview(card: CardState, rating: Int, track: Boolean) {
        val s = w[rating - 1]
        val d = w[4] - exp(w[5] * (rating - 1)) + 1
        card.stability = max(s, STABILITY_MIN)
        card.difficulty = d.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        if (!track) return
        card.detach()
        if (s >= STABILITY_MIN) card.dStability[rating - 1] = 1.0
        if (d in MIN_DIFFICULTY..MAX_DIFFICULTY) {
            card.dDifficulty[4] = 1.0
            card.dDifficulty[5] = -(rating - 1) * exp(w[5] * (rating - 1))
        }
    }

    private fun shortTermStability(card: CardState, rating: Int, track: Boolean) {
        val s = card.stability
        val increase = exp(w[17] * (rating - 3 + w[18])) * s.pow(-w[19])
        val floored = rating != 1 && increase < 1.0
        val raw = s * (if (floored) 1.0 else increase)
        card.stability = max(raw, STABILITY_MIN)
        if (!track) return
        val ds = nextDStability
        when {
            raw < STABILITY_MIN -> ds.fill(0.0)
            floored -> card.dStability.copyInto(ds)
            else -> {
                // s * increase = e^(w17 (rating - 3 + w18)) * s^(1 - w19)
                val dS = increase * (1 - w[19])
                for (j in 0 until WEIGHTS) ds[j] = dS * card.dStability[j]
                ds[17] += raw * (rating - 3 + w[18])
                ds[18] += raw * w[17]
                ds[19] += raw * -ln(s)
            }
        }
    }

    private fun nextStability(card: CardState, days: Long, rating: Int, track: Boolean) {
        val r = retrievability(card, days, if (track) dR else null)
        if (rating == 1) forgetStability(card, r, track) else recallStability(card, r, rating, track)
    }

    private fun recallStability(card: CardState, r: Double, rating: Int, track: Boolean) {
        val s = card.stability
        val d = card.difficulty
        val hardPenalty = if (rating == 2) w[15] else 1.0
        val easyBonus = if (rating == 4) w[16] else 1.0
        val e8 = exp(w[8])
        val sPow = s.pow(-w[9])
        val growth = exp((1 - r) * w[10])
        val k = e8 * (11 - d) * sPow * (growth - 1)
        val g = k * hardPenalty * easyBonus
        val raw = s * (1 + g)
        card.stability = max(raw, STABILITY_MIN)
        if (!track) return
        val ds = nextDStability
        if (raw < STABILITY_MIN) {
            ds.fill(0.0)
            return
        }
        val dS = 1 + g * (1 - w[9])
        val dD = -s * e8 * sPow * (growth - 1) * hardPenalty * easyBonus
        val dGrowth = s * e8 * (11 - d) * sPow * hardPenalty * easyBonus * growth
        val dRCoefficient = dGrowth * -w[10]
        for (j in 0 until WEIGHTS) {
            ds[j] = dS * card.dStability[j] + dD * card.dDifficulty[j] + dRCoefficient * dR[j]
        }
        ds[8] += s * g
        ds[9] += s * g * -ln(s)
        ds[10] += dGrowth * (1 - r)
        if (rating == 2) ds[15] += s * k * easyBonus
        if (rating == 4) ds[16] += s * k * hardPenalty
    }

    private fun forgetStability(card: CardState, r: Double, track: Boolean) {
        val s = card.stability
        val d = card.difficulty
        val dPow = d.pow(-w[12])
        val sPlusOne = s + 1
        val sGrowth = sPlusOne.pow(w[13])
        val retention = exp((1 - r) * w[14])
        val longTerm = w[11] * dPow * (sGrowth - 1) * retention
        val shortTermDivisor = exp(w[17] * w[18])
        val shortTerm = s / shortTermDivisor
        val useLongTerm = !(shortTerm < longTerm)
        val raw = if (useLongTerm) longTerm else shortTerm
        card.stability = max(raw, STABILITY_MIN)
        if (!track) return
        val ds = nextDStability
        when {
            raw < STABILITY_MIN -> ds.fill(0.0)
            useLongTerm -> {
                val dS = w[11] * dPow * retention * w[13] * sPlusOne.pow(w[13] - 1)
                val dD = longTerm * -w[12] / d
                val dRCoefficient = longTerm * -w[14]
                for (j in 0 until WEIGHTS) {
                    ds[j] = dS * card.dStability[j] + dD * card.dDifficulty[j] + dRCoefficient * dR[j]
                }
                ds[11] += dPow * (sGrowth - 1) * retention
                ds[12] += longTerm * -ln(d)
                ds[13] += w[11] * dPow * retention * sGrowth * ln(sPlusOne)
                ds[14] += longTerm * (1 - r)
            }
            else -> {
                for (j in 0 until WEIGHTS) ds[j] = card.dStability[j] / shortTermDivisor
                ds[17] += -shortTerm * w[18]
                ds[18] += -shortTerm * w[17]
            }
        }
    }

    private fun nextDifficulty(card: CardState, rating: Int, track: Boolean) {
        val d = card.difficulty
        val delta = -(w[6] * (rating - 3))
        val damped = d + (10.0 - d) * delta / 9.0
        val easyInitial = w[4] - exp(w[5] * 3) + 1
        val raw = w[7] * easyInitial + (1 - w[7]) * damped
        card.difficulty = raw.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)
        if (!track) return
        val dd = nextDDifficulty
        if (raw !in MIN_DIFFICULTY..MAX_DIFFICULTY) {
            dd.fill(0.0)
            return
        }
        val dD = (1 - w[7]) * (1 - delta / 9.0)
        for (j in 0 until WEIGHTS) dd[j] = dD * card.dDifficulty[j]
        dd[4] += w[7]
        dd[5] += w[7] * -3 * exp(w[5] * 3)
        dd[6] += (1 - w[7]) * (10.0 - d) * -(rating - 3) / 9.0
        dd[7] += easyInitial - damped
    }
}

/** Adam with PyTorch's defaults and update order. */
private class Adam(size: Int) {
    private val m = DoubleArray(size)
    private val v = DoubleArray(size)
    private var t = 0

    fun step(weights: DoubleArray, gradient: DoubleArray, learningRate: Double) {
        t++
        val biasCorrection1 = 1 - BETA1.pow(t)
        val biasCorrection2 = 1 - BETA2.pow(t)
        val stepSize = learningRate / biasCorrection1
        val biasCorrection2Sqrt = sqrt(biasCorrection2)
        for (i in weights.indices) {
            val g = gradient[i]
            m[i] += (1 - BETA1) * (g - m[i])
            v[i] = v[i] * BETA2 + (1 - BETA2) * g * g
            weights[i] += -stepSize * m[i] / (sqrt(v[i]) / biasCorrection2Sqrt + EPSILON)
        }
    }

    private companion object {
        const val BETA1 = 0.9
        const val BETA2 = 0.999
        const val EPSILON = 1e-8
    }
}

/** PyTorch's `CosineAnnealingLR` (to zero), in its step-by-step form. */
private class CosineAnnealing(initial: Double, val totalSteps: Int) {
    var current = initial
        private set
    var steps = 0
        private set

    fun advance() {
        steps++
        current *= (1 + cos(PI * steps / totalSteps)) / (1 + cos(PI * (steps - 1) / totalSteps))
    }
}
