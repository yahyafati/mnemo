package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.model.Rating
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The model and scheduler modules, unchanged, give sensible answers on the desktop JVM. */
class SampleSchedulingTest {
    private val now = Instant.parse("2026-09-30T08:00:00Z")

    @Test
    fun `ratings order the intervals of a review card`() {
        val review = sampleCards(now).last().intervals

        assertTrue(review.getValue(Rating.Again) < review.getValue(Rating.Hard))
        assertTrue(review.getValue(Rating.Hard) < review.getValue(Rating.Good))
        assertTrue(review.getValue(Rating.Good) < review.getValue(Rating.Easy))
    }

    @Test
    fun `a new card starts with learning steps`() {
        val fresh = sampleCards(now).first().intervals

        assertEquals("1m", formatInterval(fresh.getValue(Rating.Again)))
        assertEquals("10m", formatInterval(fresh.getValue(Rating.Good)))
        assertTrue(fresh.getValue(Rating.Easy).toDays() >= 1)
    }

    @Test
    fun `the same day gives the same labels`() {
        assertEquals(sampleCards(now).map { it.intervals }, sampleCards(now).map { it.intervals })
    }
}
