package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.zip.ZipException
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TransferQueueTest {
    @Test
    fun aJobGoesFromIdleThroughRunningToItsResult() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<String>(this)
        val release = CompletableDeferred<Unit>()
        assertEquals(TransferState.Idle, queue.state.value)

        queue.enqueue { report ->
            report(0.5f)
            release.await()
            "done"
        }
        assertEquals(TransferState.Running(0.5f), queue.state.value)

        release.complete(Unit)
        assertEquals(TransferState.Succeeded("done"), queue.state.value)

        // A finished result stays until it is cleared.
        queue.clearFinished()
        assertEquals(TransferState.Idle, queue.state.value)
    }

    @Test
    fun progressStaysBetweenZeroAndOne() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<Unit>(this)
        val release = CompletableDeferred<Unit>()

        queue.enqueue { report ->
            report(1.7f)
            release.await()
        }

        assertEquals(TransferState.Running(1f), queue.state.value)
        release.complete(Unit)
    }

    @Test
    fun failuresBecomeErrorsForTheUser() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<Unit>(this)

        queue.enqueue { throw ZipException("bad") }
        assertEquals(TransferState.Failed(TransferError.Corrupt), queue.state.value)

        queue.enqueue { throw IOException("disk full") }
        assertEquals(TransferState.Failed(TransferError.Storage), queue.state.value)

        queue.enqueue { throw IllegalStateException("a bug") }
        assertEquals(TransferState.Failed(TransferError.Unknown), queue.state.value)
    }

    @Test
    fun jobsQueueUpAndTheLastOneReports() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<Int>(this)
        val first = CompletableDeferred<Unit>()
        val order = mutableListOf<Int>()

        queue.enqueue { first.await(); order += 1; 1 }
        queue.enqueue { order += 2; 2 }
        // The second waits behind the first: still running, nothing finished.
        assertEquals(listOf<Int>(), order)
        assertEquals(TransferState.Running(null), queue.state.value)

        first.complete(Unit)

        assertEquals(listOf(1, 2), order)
        assertEquals(TransferState.Succeeded(2), queue.state.value)
    }

    @Test
    fun enqueueIfIdleIgnoresAClickWhileSomethingRuns() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<Int>(this)
        val release = CompletableDeferred<Unit>()
        var started = 0

        queue.enqueueIfIdle { started++; release.await(); 1 }
        queue.enqueueIfIdle { started++; 2 }
        release.complete(Unit)

        assertEquals(1, started)
        assertEquals(TransferState.Succeeded(1), queue.state.value)
        // Once it is done, a new one runs.
        queue.enqueueIfIdle { started++; 3 }
        assertEquals(TransferState.Succeeded(3), queue.state.value)
    }

    @Test
    fun aRunningJobIsNotClearedAway() = runTest(UnconfinedTestDispatcher()) {
        val queue = TransferQueue<Unit>(this)
        val release = CompletableDeferred<Unit>()
        queue.enqueue { release.await() }

        queue.clearFinished()

        assertEquals(TransferState.Running(null), queue.state.value)
        release.complete(Unit)
    }
}
