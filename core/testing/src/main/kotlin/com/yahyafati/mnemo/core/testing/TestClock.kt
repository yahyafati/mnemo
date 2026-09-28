package com.yahyafati.mnemo.core.testing

import com.yahyafati.mnemo.core.common.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] that only moves when the test says so. */
class TestClock(
    private var now: Instant = Instant.parse("2026-01-01T09:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock {
    override fun now(): Instant = now

    override fun zone(): ZoneId = zone

    fun set(instant: Instant) {
        now = instant
    }

    fun advanceBy(duration: Duration) {
        now += duration
    }
}
