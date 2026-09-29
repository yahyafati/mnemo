package com.yahyafati.mnemo.core.scheduler

import com.yahyafati.mnemo.core.scheduler.FsrsRating.Again
import com.yahyafati.mnemo.core.scheduler.FsrsRating.Easy
import com.yahyafati.mnemo.core.scheduler.FsrsRating.Good
import com.yahyafati.mnemo.core.scheduler.FsrsRating.Hard
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reference vectors from py-fsrs 6 (`tests/test_basic.py`). Test names match the Python ones so
 * the two can be compared when the reference changes.
 */
class FsrsTest {
    private val start = Instant.parse("2022-11-29T12:30:00Z")
    private val noFuzz = Fsrs(FsrsParameters(enableFuzzing = false))

    private fun days(from: Instant, to: Instant) = Math.floorDiv(Duration.between(from, to).toMillis(), 86_400_000L)

    @Test
    fun test_review_card() {
        val ratings = listOf(Good, Good, Good, Good, Good, Good, Again, Again, Good, Good, Good, Good, Good)
        var card = FsrsCard(due = start)
        var reviewAt = start
        val intervals = ratings.map { rating ->
            card = noFuzz.review(card, rating, reviewAt)
            reviewAt = card.due
            days(card.lastReview!!, card.due)
        }
        assertEquals(listOf(0L, 2, 11, 46, 163, 498, 0, 0, 2, 4, 7, 12, 21), intervals)
    }

    @Test
    fun test_repeated_correct_reviews() {
        var card = FsrsCard(due = start)
        repeat(10) { i ->
            card = noFuzz.review(card, Easy, start.plusNanos(i * 1_000L))
        }
        assertEquals(1.0, card.difficulty)
    }

    @Test
    fun test_memo_state() {
        val fsrs = Fsrs() // fuzzing does not change stability or difficulty
        val ratings = listOf(Again, Good, Good, Good, Good, Good)
        val gaps = listOf(0L, 0, 1, 3, 8, 21)
        var card = FsrsCard(due = start)
        var reviewAt = start
        ratings.zip(gaps).forEach { (rating, gap) ->
            reviewAt = reviewAt.plus(Duration.ofDays(gap))
            card = fsrs.review(card, rating, reviewAt)
        }
        assertEquals(53.62691, card.stability!!, 1e-4)
        assertEquals(6.3574867, card.difficulty!!, 1e-4)
    }

    @Test
    fun test_good_learning_steps() {
        var card = FsrsCard(due = start)
        card = noFuzz.review(card, Good, card.due)
        assertEquals(FsrsState.Learning, card.state)
        assertEquals(1, card.step)
        assertEquals(Duration.ofMinutes(10), Duration.between(start, card.due))

        card = noFuzz.review(card, Good, card.due)
        assertEquals(FsrsState.Review, card.state)
        assertNull(card.step)
        assertTrue(Duration.between(start, card.due) >= Duration.ofDays(1))
    }

    @Test
    fun test_again_learning_steps() {
        val card = noFuzz.review(FsrsCard(due = start), Again, start)
        assertEquals(FsrsState.Learning, card.state)
        assertEquals(0, card.step)
        assertEquals(Duration.ofMinutes(1), Duration.between(start, card.due))
    }

    @Test
    fun test_hard_learning_steps() {
        val card = noFuzz.review(FsrsCard(due = start), Hard, start)
        assertEquals(FsrsState.Learning, card.state)
        assertEquals(0, card.step)
        assertEquals(Duration.ofSeconds(330), Duration.between(start, card.due))
    }

    @Test
    fun test_easy_learning_steps() {
        val card = noFuzz.review(FsrsCard(due = start), Easy, start)
        assertEquals(FsrsState.Review, card.state)
        assertNull(card.step)
        assertTrue(Duration.between(start, card.due) >= Duration.ofDays(1))
    }

    @Test
    fun test_review_state() {
        var card = FsrsCard(due = start)
        card = noFuzz.review(card, Good, card.due)
        card = noFuzz.review(card, Good, card.due)
        assertEquals(FsrsState.Review, card.state)

        var prevDue = card.due
        card = noFuzz.review(card, Good, card.due)
        assertEquals(FsrsState.Review, card.state)
        assertTrue(Duration.between(prevDue, card.due) >= Duration.ofDays(1))

        prevDue = card.due
        card = noFuzz.review(card, Again, card.due)
        assertEquals(FsrsState.Relearning, card.state)
        assertEquals(Duration.ofMinutes(10), Duration.between(prevDue, card.due))
    }

    @Test
    fun test_relearning() {
        var card = FsrsCard(due = start)
        repeat(3) { card = noFuzz.review(card, Good, card.due) }

        repeat(2) {
            val prevDue = card.due
            card = noFuzz.review(card, Again, card.due)
            assertEquals(FsrsState.Relearning, card.state)
            assertEquals(0, card.step)
            assertEquals(Duration.ofMinutes(10), Duration.between(prevDue, card.due))
        }

        val prevDue = card.due
        card = noFuzz.review(card, Good, card.due)
        assertEquals(FsrsState.Review, card.state)
        assertNull(card.step)
        assertTrue(Duration.between(prevDue, card.due) >= Duration.ofDays(1))
    }

    @Test
    fun test_no_learning_steps() {
        val fsrs = Fsrs(FsrsParameters(learningSteps = emptyList()))
        val card = fsrs.review(FsrsCard(due = start), Again, start)
        assertEquals(FsrsState.Review, card.state)
        assertTrue(days(start, card.due) >= 1)
    }

    @Test
    fun test_no_relearning_steps() {
        val fsrs = Fsrs(FsrsParameters(relearningSteps = emptyList()))
        var card = fsrs.review(FsrsCard(due = start), Good, start)
        assertEquals(FsrsState.Learning, card.state)
        card = fsrs.review(card, Good, card.due)
        assertEquals(FsrsState.Review, card.state)
        card = fsrs.review(card, Again, card.due)
        assertEquals(FsrsState.Review, card.state)
        assertTrue(days(card.lastReview!!, card.due) >= 1)
    }

    @Test
    fun test_maximum_interval() {
        val fsrs = Fsrs(FsrsParameters(maximumInterval = 100))
        var card = FsrsCard(due = start)
        listOf(Easy, Good, Easy, Good).forEach { rating ->
            card = fsrs.review(card, rating, card.due)
            assertTrue(days(card.lastReview!!, card.due) <= 100)
        }
    }

    @Test
    fun test_retrievability() {
        var card = FsrsCard(due = start)
        assertEquals(0.0, noFuzz.retrievability(card, start))
        card = noFuzz.review(card, Good, start)
        card = noFuzz.review(card, Good, card.due)
        assertEquals(1.0, noFuzz.retrievability(card, card.lastReview!!))
        val later = noFuzz.retrievability(card, card.due)
        assertTrue(later in 0.0..1.0 && later < 1.0)
    }

    @Test
    fun test_stability_lower_bound() {
        var card = FsrsCard(due = start)
        repeat(1000) {
            card = Fsrs().review(card, Again, card.due.plus(Duration.ofDays(1)))
            assertTrue(card.stability!! >= FsrsParameters.STABILITY_MIN)
        }
    }

    @Test
    fun test_same_day_hard_review_does_not_decrease_stability() {
        val fsrs = Fsrs(FsrsParameters(learningSteps = emptyList()))
        val at = Instant.parse("2026-01-01T00:00:00Z")
        val card = fsrs.review(FsrsCard(due = at), Good, at)
        val next = fsrs.review(card, Hard, at.plus(Duration.ofMinutes(1)))
        assertEquals(card.stability, next.stability)
    }

    @Test
    fun test_scheduler_parameter_validation() {
        assertFailsWith<IllegalArgumentException> { FsrsParameters(weights = FsrsParameters.DEFAULT_WEIGHTS.drop(1)) }
        assertFailsWith<IllegalArgumentException> {
            FsrsParameters(weights = FsrsParameters.DEFAULT_WEIGHTS.toMutableList().also { it[4] = 11.0 })
        }
    }

    // Python's random() stream can't be reproduced here, so instead of the exact fuzzed days this
    // checks the range and that a seeded Random makes fuzz reproducible.
    @Test
    fun fuzzStaysInRangeAndIsReproducible() {
        var card = FsrsCard(due = start)
        card = noFuzz.review(card, Good, card.due)
        card = noFuzz.review(card, Good, card.due)
        val unfuzzed = days(card.due, noFuzz.review(card, Good, card.due).due)

        val fsrs = Fsrs()
        repeat(50) { seed ->
            val a = fsrs.review(card, Good, card.due, Random(seed))
            val b = fsrs.review(card, Good, card.due, Random(seed))
            assertEquals(a, b)
            val fuzzed = days(card.due, a.due)
            // 11 days fuzzes to 9..13; like the reference, rounding the random draw can reach 14.
            assertTrue(fuzzed in (unfuzzed - 2)..(unfuzzed + 3), "fuzzed $fuzzed vs $unfuzzed")
        }
    }

    @Test
    fun previewCoversEveryRating() {
        val info = noFuzz.preview(FsrsCard(due = start), start)
        assertEquals(Duration.ofMinutes(1), info.interval(Again))
        assertEquals(Duration.ofSeconds(330), info.interval(Hard))
        assertEquals(Duration.ofMinutes(10), info.interval(Good))
        assertTrue(info.interval(Easy) >= Duration.ofDays(1))
    }

    @Test
    fun forgettingCurveIsNinetyPercentAtOneStability() {
        val fsrs = Fsrs()
        assertEquals(1.0, fsrs.retrievability(0.0, 4.0), 1e-12)
        assertEquals(0.9, fsrs.retrievability(4.0, 4.0), 1e-12)
        assertEquals(fsrs.retrievability(2.0, 1.0), fsrs.retrievability(20.0, 10.0), 1e-12)
        assertTrue(fsrs.retrievability(40.0, 4.0) < 0.9)
    }
}
