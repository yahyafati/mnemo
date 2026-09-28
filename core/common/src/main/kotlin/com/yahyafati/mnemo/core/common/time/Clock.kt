package com.yahyafati.mnemo.core.common.time

import java.time.Instant
import java.time.ZoneId

/**
 * Source of "now". Scheduling and statistics read time only through this, so tests can use a
 * fixed clock (`TestClock` in `:core:testing`).
 */
interface Clock {
    fun now(): Instant

    fun zone(): ZoneId
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
