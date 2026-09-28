package com.yahyafati.mnemo.core.scheduler

import java.time.Duration

/**
 * Everything that shapes a schedule: the FSRS-6 model weights plus the user's scheduling options.
 *
 * Defaults match the reference implementation (py-fsrs 6): 90% desired retention, learning steps
 * of 1m and 10m, one 10m relearning step, a 100-year maximum interval, fuzzing on.
 */
data class FsrsParameters(
    val weights: List<Double> = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumInterval: Int = 36_500,
    val enableFuzzing: Boolean = true,
) {
    init {
        require(weights.size == LOWER_BOUNDS.size) {
            "Expected ${LOWER_BOUNDS.size} weights, got ${weights.size}"
        }
        weights.forEachIndexed { i, w ->
            require(w in LOWER_BOUNDS[i]..UPPER_BOUNDS[i]) {
                "weights[$i] = $w is out of bounds (${LOWER_BOUNDS[i]}, ${UPPER_BOUNDS[i]})"
            }
        }
        require(desiredRetention > 0.0 && desiredRetention < 1.0) { "desiredRetention must be in (0, 1)" }
        require(maximumInterval >= 1) { "maximumInterval must be at least 1 day" }
    }

    companion object {
        const val DEFAULT_DECAY = 0.1542

        /** FSRS-6 default weights (w0…w20). */
        val DEFAULT_WEIGHTS: List<Double> = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, DEFAULT_DECAY,
        )

        internal const val STABILITY_MIN = 0.001
        private const val INITIAL_STABILITY_MAX = 100.0

        private val LOWER_BOUNDS = listOf(
            STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, STABILITY_MIN, 1.0, 0.001, 0.001, 0.001,
            0.0, 0.0, 0.001, 0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
        )
        private val UPPER_BOUNDS = listOf(
            INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX, INITIAL_STABILITY_MAX,
            10.0, 4.0, 4.0, 0.75, 4.5, 0.8, 3.5, 5.0, 0.25, 0.9, 4.0, 1.0, 6.0, 2.0, 2.0, 0.8, 0.8,
        )
    }
}
