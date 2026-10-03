package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.database.dao.SyncDao

/**
 * The hybrid logical clock that orders changes between devices (ADR 0013). A value is milliseconds
 * that never go backwards: [now] is `max(wall clock, last + 1)`, and [observe] moves the clock past a
 * value seen on another device, so a device with a slow clock still orders after everything it has
 * already seen. The last value is kept in `sync_state`, so it survives a restart.
 *
 * Repositories keep writing `updatedAt` from [Clock]; a change is stamped with [now] when it is packed
 * (S3), not when it is made.
 */
internal class SyncClock(
    private val syncDao: SyncDao,
    private val transaction: TransactionRunner,
    private val clock: Clock,
) {
    /** A new value, greater than every value issued or observed so far. */
    suspend fun now(): Long = transaction {
        syncDao.advanceClock(clock.now().toEpochMilli())
        checkNotNull(syncDao.getClock()) { "The sync_state row is missing" }
    }

    /** Moves the clock past [remote], a value from another device's change. */
    suspend fun observe(remote: Long) = syncDao.raiseClock(remote)
}
