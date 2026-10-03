package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.data.backup.PendingRestore
import com.yahyafati.mnemo.core.data.desktop.DesktopAppDirectories
import com.yahyafati.mnemo.core.data.desktop.ProcessAppRestarter
import com.yahyafati.mnemo.core.data.desktop.SingleInstanceLock
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.sync.SyncAutomation
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/** A started collection: holds the single-instance lock and the running Koin graph. */
class CollectionSession internal constructor(
    val koin: Koin,
    private val lock: SingleInstanceLock,
) : AutoCloseable {
    override fun close() {
        org.koin.core.context.stopKoin()
        lock.close()
    }
}

/**
 * Opens the collection in [directories], in the order the data layer needs (ADR 0010, ROADMAP D4):
 *
 * 1. Take the single-instance lock: the database and preferences files must not be opened by two
 *    processes. A restart waits [restartWaitMillis] for the old process to let go.
 * 2. Apply a staged restore ([PendingRestore]) before anything opens the database or DataStore.
 * 3. Start the Koin graph and the periodic maintenance.
 *
 * Returns null if another Mnemo already has the collection open.
 */
fun openCollection(
    directories: DesktopAppDirectories = DesktopAppDirectories.default(),
    modules: List<Module> = desktopModules,
    restarted: Boolean = System.getenv(ProcessAppRestarter.RESTARTED_VARIABLE) != null,
    restartWaitMillis: Long = RESTART_WAIT_MILLIS,
): CollectionSession? {
    val lock = SingleInstanceLock.tryAcquire(directories.files, waitMillis = if (restarted) restartWaitMillis else 0) ?: return null
    PendingRestore.applyIfPresent(directories)
    val koin = startKoin { modules(modules) }.koin
    runBlocking { koin.get<DataTransferRepository>().scheduleMaintenance() }
    koin.get<SyncAutomation>().start()
    return CollectionSession(koin, lock)
}

private const val RESTART_WAIT_MILLIS = 10_000L
