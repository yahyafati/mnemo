package com.yahyafati.mnemo.core.scheduler

import org.junit.Test
import kotlin.test.assertEquals

/** SM-2 → FSRS memory state (ADR 0001), against values computed from the fsrs-rs formula. */
class Sm2ConversionTest {
    private val fsrs = Fsrs()

    @Test
    fun stabilityIsTheIntervalAtNinetyPercent() {
        val state = fsrs.memoryStateFromSm2(easeFactor = 2.5, intervalDays = 10.0)
        assertEquals(10.0, state.stability, 1e-9)
        assertEquals(6.9140552357715555, state.difficulty, 1e-9)

        val other = fsrs.memoryStateFromSm2(easeFactor = 2.3, intervalDays = 30.0)
        assertEquals(30.0, other.stability, 1e-9)
        assertEquals(6.747611004245584, other.difficulty, 1e-9)
    }

    @Test
    fun otherRetentionAndClamping() {
        val lower = fsrs.memoryStateFromSm2(easeFactor = 2.5, intervalDays = 10.0, sm2Retention = 0.85)
        assertEquals(5.245416975052666, lower.stability, 1e-9)
        assertEquals(8.602653403207992, lower.difficulty, 1e-9)
        // A lapsed card at minimum ease is as hard as FSRS allows.
        assertEquals(10.0, fsrs.memoryStateFromSm2(easeFactor = 1.3, intervalDays = 1.0).difficulty)
    }

    @Test
    fun easeFactorInvertsTheConversion() {
        val state = fsrs.memoryStateFromSm2(easeFactor = 2.5, intervalDays = 10.0)
        assertEquals(2.5, fsrs.sm2EaseFactor(state), 1e-9)
        assertEquals(1.3, fsrs.sm2EaseFactor(FsrsMemoryState(stability = 1_000_000.0, difficulty = 10.0)))
    }
}
