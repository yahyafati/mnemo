package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.data.job.toTransferError
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What WorkManager does for one kind of long job on Android, with coroutines: jobs run one after
 * another in [scope], and [state] is the newest job's (or the running one's), as
 * `workManager.workState` reports it. Jobs live as long as the app.
 */
internal class TransferQueue<R>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val lock = Any()
    private var unfinished = 0
    private val _state = MutableStateFlow<TransferState<R>>(TransferState.Idle)

    val state: StateFlow<TransferState<R>> = _state

    /** Queues [work] behind the jobs already there. [report] gets 0..1. */
    fun enqueue(work: suspend (report: (Float) -> Unit) -> R) {
        synchronized(lock) {
            unfinished++
            if (_state.value !is TransferState.Running) _state.value = TransferState.Running(null)
        }
        scope.launch { run(work) }
    }

    /** Queues [work] unless a job is running or waiting: for work that one click may start twice. */
    fun enqueueIfIdle(work: suspend (report: (Float) -> Unit) -> R) {
        synchronized(lock) {
            if (unfinished > 0) return
            unfinished++
            _state.value = TransferState.Running(null)
        }
        scope.launch { run(work) }
    }

    /** Forgets a finished job's result, so it stops showing. A running job stays. */
    fun clearFinished() {
        synchronized(lock) {
            if (unfinished == 0) _state.value = TransferState.Idle
        }
    }

    private suspend fun run(work: suspend (report: (Float) -> Unit) -> R) {
        mutex.withLock {
            val outcome: TransferState<R> = try {
                TransferState.Succeeded(work { progress -> reportProgress(progress) })
            } catch (e: CancellationException) {
                finish(TransferState.Idle)
                throw e
            } catch (e: Exception) {
                TransferState.Failed(e.toTransferError())
            }
            finish(outcome)
        }
    }

    private fun reportProgress(progress: Float) {
        synchronized(lock) {
            _state.value = TransferState.Running(progress.coerceIn(0f, 1f))
        }
    }

    private fun finish(outcome: TransferState<R>) {
        synchronized(lock) {
            unfinished--
            _state.value = if (unfinished > 0) TransferState.Running(null) else outcome
        }
    }
}
