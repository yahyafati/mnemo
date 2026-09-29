package com.yahyafati.mnemo.core.scheduler

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

/**
 * The FSRS-6 scheduler: a line-for-line port of the reference implementation (py-fsrs 6,
 * `fsrs/scheduler.py`), checked against its test vectors in `FsrsTest`.
 *
 * Pure and fast (a few microseconds per call), so it is safe on the main thread.
 *
 * Python's `round` rounds half to even; Kotlin's [round] does the same, which keeps intervals identical to
 * the reference.
 */
class Fsrs(val parameters: FsrsParameters = FsrsParameters()) {
    private val w = parameters.weights
    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    /**
     * Reviews [card] with [rating] at [reviewedAt] and returns its next state.
     *
     * [random] draws the interval fuzz. Pass a seeded one to make the result reproducible (the
     * study screen previews all four outcomes before the user answers, then saves the chosen one).
     */
    fun review(
        card: FsrsCard,
        rating: FsrsRating,
        reviewedAt: Instant,
        random: Random = Random.Default,
    ): FsrsCard {
        val daysSinceLastReview = card.lastReview?.let { floorDays(it, reviewedAt) }
        val sameDay = daysSinceLastReview != null && daysSinceLastReview < 1

        var stability: Double
        var difficulty: Double
        var state = card.state
        var step = card.step
        val interval: Duration

        when (card.state) {
            FsrsState.Learning, FsrsState.Relearning -> {
                val currentStep = checkNotNull(card.step) { "A ${card.state} card needs a step" }
                if (card.stability == null || card.difficulty == null) {
                    stability = initialStability(rating)
                    difficulty = initialDifficulty(rating, clamp = true)
                } else if (sameDay) {
                    stability = shortTermStability(card.stability, rating)
                    difficulty = nextDifficulty(card.difficulty, rating)
                } else {
                    stability = nextStability(
                        difficulty = card.difficulty,
                        stability = card.stability,
                        retrievability = retrievability(card, reviewedAt),
                        rating = rating,
                    )
                    difficulty = nextDifficulty(card.difficulty, rating)
                }

                val steps = if (card.state == FsrsState.Learning) parameters.learningSteps else parameters.relearningSteps
                if (steps.isEmpty() || (currentStep >= steps.size && rating != FsrsRating.Again)) {
                    state = FsrsState.Review
                    step = null
                    interval = Duration.ofDays(nextIntervalDays(stability).toLong())
                } else {
                    when (rating) {
                        FsrsRating.Again -> {
                            step = 0
                            interval = steps[0]
                        }

                        FsrsRating.Hard -> interval = when {
                            currentStep == 0 && steps.size == 1 -> steps[0].multipliedBy(3).dividedBy(2)
                            currentStep == 0 -> steps[0].plus(steps[1]).dividedBy(2)
                            else -> steps[currentStep]
                        }

                        FsrsRating.Good -> if (currentStep + 1 == steps.size) {
                            state = FsrsState.Review
                            step = null
                            interval = Duration.ofDays(nextIntervalDays(stability).toLong())
                        } else {
                            step = currentStep + 1
                            interval = steps[currentStep + 1]
                        }

                        FsrsRating.Easy -> {
                            state = FsrsState.Review
                            step = null
                            interval = Duration.ofDays(nextIntervalDays(stability).toLong())
                        }
                    }
                }
            }

            FsrsState.Review -> {
                val s = checkNotNull(card.stability) { "A review card needs a stability" }
                val d = checkNotNull(card.difficulty) { "A review card needs a difficulty" }
                stability = if (sameDay) {
                    shortTermStability(s, rating)
                } else {
                    nextStability(d, s, retrievability(card, reviewedAt), rating)
                }
                difficulty = nextDifficulty(d, rating)

                if (rating == FsrsRating.Again && parameters.relearningSteps.isNotEmpty()) {
                    state = FsrsState.Relearning
                    step = 0
                    interval = parameters.relearningSteps[0]
                } else {
                    interval = Duration.ofDays(nextIntervalDays(stability).toLong())
                }
            }
        }

        val finalInterval = if (parameters.enableFuzzing && state == FsrsState.Review) {
            fuzzedInterval(interval, random)
        } else {
            interval
        }

        return FsrsCard(
            due = reviewedAt.plus(finalInterval),
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            lastReview = reviewedAt,
        )
    }

    /** Predicted probability of recalling [card] at [now]; 0 for a card with no memory state. */
    fun retrievability(card: FsrsCard, now: Instant): Double {
        val lastReview = card.lastReview ?: return 0.0
        val stability = card.stability ?: return 0.0
        val elapsedDays = max(0L, floorDays(lastReview, now))
        return (1 + factor * elapsedDays / stability).pow(decay)
    }

    /**
     * Memory state for a card scheduled by SM-2 that has no usable review history (ADR 0001):
     * stability from its current interval, difficulty from its ease factor. The same inversion
     * as fsrs-rs `memory_state_from_sm2`: SM-2 multiplied the interval by the ease on each
     * success, so the ease is read back as FSRS's stability growth at [sm2Retention].
     *
     * @param easeFactor SM-2 ease as a multiplier (Anki stores 2500 for 2.5).
     */
    fun memoryStateFromSm2(easeFactor: Double, intervalDays: Double, sm2Retention: Double = 0.9): FsrsMemoryState {
        val stability = max(intervalDays, FsrsParameters.STABILITY_MIN) * factor / (sm2Retention.pow(1 / decay) - 1)
        val growth = exp(w[8]) * stability.pow(-w[9]) * (exp((1 - sm2Retention) * w[10]) - 1)
        val difficulty = 11.0 - (easeFactor - 1.0) / growth
        return FsrsMemoryState(clampStability(stability), clampDifficulty(difficulty))
    }

    /**
     * The inverse of [memoryStateFromSm2]: the SM-2 ease multiplier matching [state], for
     * exporting to Anki users who still schedule with SM-2. Clamped to SM-2's usual 1.3–5.0.
     */
    fun sm2EaseFactor(state: FsrsMemoryState, sm2Retention: Double = 0.9): Double {
        val growth = exp(w[8]) * state.stability.pow(-w[9]) * (exp((1 - sm2Retention) * w[10]) - 1)
        return (1.0 + (11.0 - state.difficulty) * growth).coerceIn(1.3, 5.0)
    }

    /** What each rating would do to [card] if answered at [now]. Drives the rating-button labels. */
    fun preview(card: FsrsCard, now: Instant, random: () -> Random = { Random.Default }): SchedulingInfo =
        SchedulingInfo(
            reviewedAt = now,
            outcomes = FsrsRating.entries.associateWith { review(card, it, now, random()) },
        )

    private fun initialStability(rating: FsrsRating): Double =
        clampStability(w[rating.value - 1])

    private fun initialDifficulty(rating: FsrsRating, clamp: Boolean): Double {
        val d = w[4] - exp(w[5] * (rating.value - 1)) + 1
        return if (clamp) clampDifficulty(d) else d
    }

    private fun nextIntervalDays(stability: Double): Int {
        val raw = (stability / factor) * (parameters.desiredRetention.pow(1 / decay) - 1)
        return round(raw).toInt().coerceIn(1, parameters.maximumInterval)
    }

    private fun shortTermStability(stability: Double, rating: FsrsRating): Double {
        var increase = exp(w[17] * (rating.value - 3 + w[18])) * stability.pow(-w[19])
        if (rating != FsrsRating.Again) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: FsrsRating): Double {
        val deltaDifficulty = -(w[6] * (rating.value - 3))
        val damped = difficulty + (10.0 - difficulty) * deltaDifficulty / 9.0
        val meanReverted = w[7] * initialDifficulty(FsrsRating.Easy, clamp = false) + (1 - w[7]) * damped
        return clampDifficulty(meanReverted)
    }

    private fun nextStability(difficulty: Double, stability: Double, retrievability: Double, rating: FsrsRating): Double {
        val next = if (rating == FsrsRating.Again) {
            nextForgetStability(difficulty, stability, retrievability)
        } else {
            nextRecallStability(difficulty, stability, retrievability, rating)
        }
        return clampStability(next)
    }

    private fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val longTerm = w[11] * difficulty.pow(-w[12]) * ((stability + 1).pow(w[13]) - 1) *
            exp((1 - retrievability) * w[14])
        val shortTerm = stability / exp(w[17] * w[18])
        return min(longTerm, shortTerm)
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: FsrsRating,
    ): Double {
        val hardPenalty = if (rating == FsrsRating.Hard) w[15] else 1.0
        val easyBonus = if (rating == FsrsRating.Easy) w[16] else 1.0
        return stability * (
            1 + exp(w[8]) * (11 - difficulty) * stability.pow(-w[9]) *
                (exp((1 - retrievability) * w[10]) - 1) * hardPenalty * easyBonus
            )
    }

    private fun fuzzedInterval(interval: Duration, random: Random): Duration {
        val days = interval.toDays()
        if (days < 2.5) return interval

        var delta = 1.0
        for ((start, end, fuzzFactor) in FUZZ_RANGES) {
            delta += fuzzFactor * max(min(days.toDouble(), end) - start, 0.0)
        }
        var minIvl = round(days - delta).toInt()
        val maxIvl = min(round(days + delta).toInt(), parameters.maximumInterval)
        minIvl = min(max(2, minIvl), maxIvl)

        val fuzzed = random.nextDouble() * (maxIvl - minIvl + 1) + minIvl
        return Duration.ofDays(min(round(fuzzed).toInt(), parameters.maximumInterval).toLong())
    }

    private fun clampStability(stability: Double) = max(stability, FsrsParameters.STABILITY_MIN)

    private fun clampDifficulty(difficulty: Double) = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private companion object {
        const val MIN_DIFFICULTY = 1.0
        const val MAX_DIFFICULTY = 10.0

        val FUZZ_RANGES = listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )

        /** Whole days between two instants, rounded down like Python's `timedelta.days`. */
        fun floorDays(from: Instant, to: Instant): Long =
            Math.floorDiv(Duration.between(from, to).toMillis(), Duration.ofDays(1).toMillis())
    }
}
