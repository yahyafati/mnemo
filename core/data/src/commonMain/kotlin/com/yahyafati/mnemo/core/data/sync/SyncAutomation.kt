package com.yahyafati.mnemo.core.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * What makes sync run by itself while the app is open (docs/sync/ROADMAP.md S4): a round when syncing turns on or the
 * app starts, and one a few seconds after the last edit, which is also right after a study session ends (every answer
 * is an edit, and the delay only passes once they stop). The rounds while the app is closed are [SyncBackgroundWork]'s
 * (WorkManager on Android; on the desktop an in-process timer), which this keeps scheduled exactly while sync is on.
 * Rounds never overlap: [SyncRepository.syncNow] returns `Busy` for the second.
 */
class SyncAutomation internal constructor(
    private val repository: SyncRepository,
    private val background: SyncBackgroundWork,
    private val scope: CoroutineScope,
    private val editDelay: Duration = 5.seconds,
) {
    private val started = AtomicBoolean(false)

    /** Starts watching. Does nothing the second time. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            repository.status.map { it.isOn }.distinctUntilChanged().collectLatest { on ->
                if (on) {
                    background.schedule()
                    attempt()
                } else {
                    background.cancel()
                }
            }
        }
        scope.launch {
            repository.pendingChanges.filter { it > 0 }.debounce(editDelay).collect { attempt() }
        }
    }

    private suspend fun attempt() {
        try {
            repository.syncNow()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The status already says what went wrong; the next trigger tries again.
        }
    }
}
