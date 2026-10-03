package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.RoomTransactionRunner
import com.yahyafati.mnemo.core.testing.PlatformTest
import com.yahyafati.mnemo.core.testing.TestClock
import com.yahyafati.mnemo.core.testing.fileDatabase
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

class SyncClockTest : PlatformTest() {
    private val wall = TestClock(Instant.parse("2026-03-01T10:00:00Z"))
    private val file by lazy { tmp.newFolder().resolve("clock.db") }
    private var db: MnemoDatabase? = null

    @After
    fun tearDown() {
        db?.close()
    }

    private fun clock(): SyncClock {
        val database = fileDatabase(file).also { db = it }
        return SyncClock(database.syncDao(), RoomTransactionRunner(database), wall)
    }

    private fun reopen(): SyncClock {
        db?.close()
        return clock()
    }

    @Test
    fun followsTheWallClockAndNeverRepeatsAValue() = runTest {
        val clock = clock()
        val first = clock.now()
        assertEquals(wall.now().toEpochMilli(), first)

        // Several calls in the same millisecond still get increasing values.
        val burst = List(5) { clock.now() }
        assertEquals(listOf(first + 1, first + 2, first + 3, first + 4, first + 5), burst)

        wall.advanceBy(Duration.ofSeconds(10))
        assertEquals(wall.now().toEpochMilli(), clock.now())
    }

    @Test
    fun aWallClockThatGoesBackwardsDoesNotMoveTheClockBack() = runTest {
        val clock = clock()
        val before = clock.now()
        wall.advanceBy(Duration.ofHours(-5))
        assertEquals(before + 1, clock.now())
    }

    /** ADR 0013's example: A is a day ahead, B syncs, then B edits; B's change must order after A's. */
    @Test
    fun aDeviceWithASlowClockOrdersAfterWhatItHasSeen() = runTest {
        val clock = clock()
        val dayAhead = wall.now().plus(Duration.ofDays(1)).toEpochMilli()
        clock.observe(dayAhead)
        assertTrue(clock.now() > dayAhead)
    }

    @Test
    fun observingAnOlderValueChangesNothing() = runTest {
        val clock = clock()
        val now = clock.now()
        clock.observe(now - 10_000)
        assertEquals(now + 1, clock.now())
    }

    @Test
    fun theClockSurvivesARestart() = runTest {
        val before = clock().now()
        wall.advanceBy(Duration.ofHours(-1))
        assertEquals(before + 1, reopen().now())
    }
}
