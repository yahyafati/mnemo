package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.security.SecretIds
import com.yahyafati.mnemo.core.security.SecretStore
import com.yahyafati.mnemo.core.security.StoredSecret
import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncPassphraseException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.SyncStore
import com.yahyafati.mnemo.core.sync.format.DeviceInfo
import com.yahyafati.mnemo.core.sync.format.SyncKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A reason to stop that isn't a failure of the location itself (also what the fakes throw to stand for any problem). */
class SyncProblemException(val problem: SyncProblem, message: String) : Exception(message)

/**
 * [SyncRepository] on the merge engine (docs/sync/ROADMAP.md S4). One [Mutex] serializes everything: a round that is
 * running makes [syncNow] return [SyncResult.Busy] and makes the lifecycle calls wait.
 *
 * State lives in three places that are only ever changed together under the mutex: `sync_state.enabled` (the database:
 * whether changes are recorded), [SyncConfigStore] (which location) and the key in [SecretStore]. A config without
 * `enabled` is a location this device remembers but doesn't sync with ([SyncStatus.Restored]), which is what a restore
 * leaves; `enabled` without a config is repaired to off.
 */
internal class DefaultSyncRepository(
    private val database: MnemoDatabase,
    private val transaction: TransactionRunner,
    private val engine: SyncEngine,
    private val snapshots: SyncSnapshots,
    private val maintenance: SyncMaintenance,
    private val configs: SyncConfigStore,
    private val secrets: SecretStore,
    private val stores: SyncStores,
    private val backups: BackupManager,
    private val directories: AppDirectories,
    private val media: SyncMediaFiles,
    private val describer: DeviceDescriber,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) : SyncRepository {
    private val dao = database.syncDao()
    private val mutex = Mutex()
    private val state = MutableStateFlow<SyncStatus?>(null)

    override val status: Flow<SyncStatus> = flow {
        // Read-only: an operation that is running has set (or will set) the state itself.
        if (state.value == null) state.compareAndSet(null, resting(configs.read(), dao.getState()?.enabled == true))
        emitAll(state.filterNotNull())
    }

    override val pendingChanges: Flow<Int> = dao.observeChangeCount()

    // --- reading ------------------------------------------------------------------------------------------

    override suspend fun inspect(backend: SyncBackend): SyncLocation = withContext(ioDispatcher) {
        val store = stores.open(backend)
        val paths = store.list("")
        when {
            paths.isEmpty() -> SyncLocation.Empty
            SyncPaths.MANIFEST in paths -> SyncLocation.SyncData(encrypted = SyncRemote.readManifest(store).encryption != null)
            else -> SyncLocation.Leftovers
        }
    }

    override val googleDriveAvailable: Boolean get() = stores.googleDriveAvailable

    override suspend fun signInToGoogleDrive() = stores.signInToGoogleDrive()

    override suspend fun isEncrypted(): Boolean = configs.read()?.encrypted == true

    override suspend fun devices(): List<SyncDeviceSummary> {
        val config = configs.read() ?: throw IllegalStateException("There is no sync location")
        val me = deviceId()
        val remote = openRemote(config)
        return withContext(ioDispatcher) {
            remote.listDevices().mapNotNull { id ->
                val info = try {
                    remote.readDevice(id)
                } catch (_: SyncCorruptException) {
                    null
                }
                // `updatedAt = 0` is how a device that left says so.
                info?.takeIf { it.updatedAt > 0 }?.let {
                    SyncDeviceSummary(it.deviceId, it.name, it.platform, it.appVersion, Instant.ofEpochMilli(it.updatedAt), isThisDevice = it.deviceId == me)
                }
            }.sortedByDescending { it.lastSeenAt }
        }
    }

    // --- rounds -------------------------------------------------------------------------------------------

    override suspend fun syncNow(): SyncResult {
        if (!mutex.tryLock()) return SyncResult.Busy
        try {
            return runRound()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun runRound(): SyncResult {
        val config = configs.read()
        val enabled = dao.getState()?.enabled == true
        if (config == null || !enabled) {
            if (enabled) dao.setEnabled(false)
            state.value = resting(config, enabled = false)
            return SyncResult.NotSyncing
        }
        val last = config.lastSyncAt?.let(Instant::ofEpochMilli)
        state.value = SyncStatus.Syncing(config.backend, last)
        return try {
            val me = deviceId()
            val remote = openRemote(config)
            guardAgainstLostFiles(config, remote, me)
            val report = engine.sync(remote, describer.describe())
            val now = clock.now().toEpochMilli()
            var updated = config.copy(lastSyncAt = now)
            if (maintenance.isDue(config.lastMaintenanceAt)) {
                try {
                    withContext(ioDispatcher) { maintenance.run(remote, me) }
                    updated = updated.copy(lastMaintenanceAt = now)
                } catch (_: SyncException) {
                    // Clean-up is not worth failing a round for: it is tried again at the next one.
                }
            }
            configs.write(updated)
            state.value = SyncStatus.Idle(config.backend, Instant.ofEpochMilli(now))
            SyncResult.Done(report)
        } catch (e: CancellationException) {
            state.value = SyncStatus.Idle(config.backend, last)
            throw e
        } catch (e: Exception) {
            val problem = e.syncProblem()
            state.value = if (problem == SyncProblem.Offline) {
                SyncStatus.WaitingForNetwork(config.backend, last)
            } else {
                SyncStatus.Error(config.backend, problem, last, e.message)
            }
            SyncResult.Failed(problem)
        }
    }

    /** Opens the location this device syncs with, and refuses one whose sync data was started again by someone. */
    private suspend fun openRemote(config: SyncConfig): SyncRemote = withContext(ioDispatcher) {
        val store = stores.open(config.backend)
        if (SyncRemote.readManifest(store).collectionId != config.collectionId) {
            throw SyncProblemException(SyncProblem.Replaced, "The sync data was started again")
        }
        SyncRemote.openWithKey(store, if (config.encrypted) storedKey() else null)
    }

    /**
     * A device that has been away longer than the others wait for may find the change files it needs gone. Only then
     * is that worth checking: a file that is merely late in a folder never looks like it.
     */
    private suspend fun guardAgainstLostFiles(config: SyncConfig, remote: SyncRemote, me: String) {
        val lastSync = config.lastSyncAt ?: return
        if (!maintenance.isInactive(lastSync)) return
        val seqs = dao.getSeqs()
        val lost = withContext(ioDispatcher) {
            maintenance.lostFiles(
                remote,
                me,
                applied = seqs.associate { it.deviceId to it.seq },
                gaps = seqs.filter { it.gaps.isNotBlank() }.associate { it.deviceId to SnapshotHead.parseGaps(it.gaps) },
            )
        }
        if (lost) throw SyncProblemException(SyncProblem.MustRejoin, "Change files this device needs were cleaned up")
    }

    // --- create -------------------------------------------------------------------------------------------

    override suspend fun create(backend: SyncBackend, passphrase: CharArray?) = mutex.withLock {
        requireOff()
        createLocked(backend, passphrase)
    }

    private suspend fun createLocked(backend: SyncBackend, passphrase: CharArray?) {
        val store = stores.open(backend)
        var created = false
        try {
            val remote = withContext(ioDispatcher) {
                if (store.list("").isNotEmpty()) throw SyncAlreadyExistsException("The location already holds sync data")
                SyncRemote.create(store, UUID.randomUUID().toString(), clock.now().toEpochMilli(), passphrase)
            }
            created = true
            stores.retain(backend)
            val me = deviceId()
            withContext(ioDispatcher) { uploadMedia(remote) }
            clearBookkeeping()
            saveKey(remote)
            val config = SyncConfig(backend, remote.manifest.collectionId, remote.isEncrypted)
            configs.write(config)
            dao.setEnabled(true)
            engine.sync(remote, describer.describe(), announceSettings = true)
            withContext(ioDispatcher) { maintenance.writeFirstSnapshot(remote, me) }
            val now = clock.now().toEpochMilli()
            configs.write(config.copy(lastSyncAt = now, lastMaintenanceAt = now))
            state.value = SyncStatus.Idle(backend, Instant.ofEpochMilli(now))
        } catch (e: Throwable) {
            withContext(NonCancellable) {
                runCatching { dao.setEnabled(false) }
                runCatching { configs.clear() }
                runCatching { secrets.remove(SecretIds.SYNC_KEY) }
                if (created) {
                    runCatching { stores.release(backend) }
                    runCatching { withContext(ioDispatcher) { deleteEverything(store) } }
                }
                state.value = resting(configs.read(), dao.getState()?.enabled == true)
            }
            throw e
        }
    }

    /** A new location has none of this collection's media files; the change files only carry the rows. */
    private suspend fun uploadMedia(remote: SyncRemote) {
        val have = remote.listMedia().toSet()
        for (hash in database.syncMergeDao().getLiveMediaIds()) {
            if (hash in have) continue
            media.read(hash)?.let { remote.writeMedia(hash, it) }
        }
    }

    // --- join ---------------------------------------------------------------------------------------------

    override suspend fun join(backend: SyncBackend, passphrase: CharArray?, replaceLocalData: Boolean): JoinResult = mutex.withLock {
        requireOff()
        joinLocked(backend, replaceLocalData) { store -> SyncRemote.open(store, passphrase) }
    }

    override suspend fun rejoin(passphrase: CharArray?): JoinResult = mutex.withLock {
        val config = configs.read() ?: throw IllegalStateException("There is no sync location to join again")
        joinLocked(config.backend, replaceLocalData = true) { store ->
            if (passphrase != null) SyncRemote.open(store, passphrase) else SyncRemote.openWithKey(store, if (config.encrypted) storedKey() else null)
        }
    }

    private suspend fun joinLocked(backend: SyncBackend, replaceLocalData: Boolean, open: suspend (SyncStore) -> SyncRemote): JoinResult {
        val previous = configs.read()
        val store = stores.open(backend)
        val (remote, bytes) = withContext(ioDispatcher) {
            val remote = open(store)
            val newest = remote.listSnapshots().lastOrNull() ?: throw SyncNotFoundException("The location has no snapshot to join from")
            remote to remote.readSnapshot(newest)
        }
        val head = snapshots.head(bytes)
        val hasData = database.syncMergeDao().countLiveDecksAndNotes() > 0
        if (hasData && !replaceLocalData) throw SyncReplacesLocalDataException()
        val backup = if (hasData) saveSafetyBackup() else null

        // Say this device is there before it changes, so no clean-up decides it needs nothing: it will have exactly
        // what the snapshot covers.
        val me = deviceId()
        val now = clock.now().toEpochMilli()
        withContext(ioDispatcher) {
            val old = try {
                remote.readDevice(me)
            } catch (_: SyncCorruptException) {
                null
            }
            val description = describer.describe()
            remote.writeDevice(
                DeviceInfo(
                    deviceId = me,
                    name = description.name,
                    platform = description.platform,
                    appVersion = description.appVersion,
                    lastSeq = head.seqs.firstOrNull { it.deviceId == me }?.seq ?: 0,
                    applied = head.seqs.filter { it.deviceId != me }.associate { it.deviceId to it.seq },
                    updatedAt = now,
                    gaps = head.seqs.filter { it.deviceId != me && it.gaps.isNotBlank() }
                        .associate { it.deviceId to SnapshotHead.parseGaps(it.gaps) },
                    snapshot = old?.snapshot,
                ),
            )
        }

        val wasEnabled = dao.getState()?.enabled == true
        dao.setEnabled(false)
        try {
            snapshots.load(bytes)
        } catch (e: Throwable) {
            withContext(NonCancellable) { dao.setEnabled(wasEnabled) }
            throw e
        }
        stores.retain(backend)
        previous?.takeIf { it.backend != backend }?.let { stores.release(it.backend) }
        saveKey(remote)
        val config = SyncConfig(backend, remote.manifest.collectionId, remote.isEncrypted, lastSyncAt = now, lastMaintenanceAt = now)
        configs.write(config)
        dao.setEnabled(true)
        state.value = SyncStatus.Idle(backend, Instant.ofEpochMilli(now))

        // The collection is replaced; a first round that can't reach the location doesn't undo that.
        try {
            engine.sync(remote, describer.describe(), announceSettings = false, includeOwn = true)
        } catch (e: SyncException) {
            val problem = e.syncProblem()
            state.value = if (problem == SyncProblem.Offline) {
                SyncStatus.WaitingForNetwork(backend, Instant.ofEpochMilli(now))
            } else {
                SyncStatus.Error(backend, problem, Instant.ofEpochMilli(now), e.message)
            }
        }
        return JoinResult(backup)
    }

    /** A copy of the collection a join replaces, in the app's own files (the newest few are kept). */
    private suspend fun saveSafetyBackup(): String = withContext(ioDispatcher) {
        val folder = File(directories.files, BACKUP_FOLDER).apply { mkdirs() }
        val stamp = BACKUP_NAME.format(clock.now().atZone(ZoneId.systemDefault()))
        val file = File(folder, "before-sync-$stamp.zip")
        val work = File(directories.cache, "sync-backup-${UUID.randomUUID()}")
        try {
            file.outputStream().use { backups.write(it, work) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        folder.listFiles().orEmpty().sortedByDescending { it.name }.drop(KEEP_BACKUPS).forEach { it.delete() }
        file.toURI().toString()
    }

    // --- restore, unlock, leave -----------------------------------------------------------------------------

    override suspend fun uploadAsNew(passphrase: CharArray?) = mutex.withLock {
        val config = configs.read() ?: throw IllegalStateException("There is no sync location to start again")
        withContext(ioDispatcher) { deleteEverything(stores.open(config.backend)) }
        createLocked(config.backend, passphrase)
    }

    override suspend fun unlock(passphrase: CharArray) = mutex.withLock {
        val config = configs.read() ?: throw IllegalStateException("There is no sync location")
        check(config.encrypted) { "The location isn't encrypted" }
        val remote = withContext(ioDispatcher) { SyncRemote.open(stores.open(config.backend), passphrase) }
        if (remote.manifest.collectionId != config.collectionId) throw SyncProblemException(SyncProblem.Replaced, "The sync data was started again")
        saveKey(remote)
        val enabled = dao.getState()?.enabled == true
        state.value = resting(config, enabled)
    }

    override suspend fun leave() = mutex.withLock {
        val config = configs.read()
        if (config != null && dao.getState()?.enabled == true) announceLeaving(config)
        leaveLocked(config)
    }

    override suspend fun deleteSyncData() = mutex.withLock {
        val config = configs.read() ?: throw IllegalStateException("There is no sync location")
        withContext(ioDispatcher) { deleteEverything(stores.open(config.backend)) }
        leaveLocked(config)
    }

    private suspend fun leaveLocked(config: SyncConfig?) {
        dao.setEnabled(false)
        clearBookkeeping()
        configs.clear()
        secrets.remove(SecretIds.SYNC_KEY)
        config?.let { stores.release(it.backend) }
        state.value = SyncStatus.Off
    }

    /** Marks this device's `device.json` as old, so the others stop waiting for it. Best effort: it may be offline. */
    private suspend fun announceLeaving(config: SyncConfig) {
        try {
            val me = deviceId()
            withContext(ioDispatcher) {
                val remote = SyncRemote.openWithKey(stores.open(config.backend), if (config.encrypted) storedKey() else null)
                remote.readDevice(me)?.let { remote.writeDevice(it.copy(updatedAt = 0)) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The others wait for this device for at most the inactivity period.
        }
    }

    // --- plumbing -----------------------------------------------------------------------------------------

    private suspend fun requireOff() {
        check(!(configs.read() != null && dao.getState()?.enabled == true)) { "Sync is on: leave before setting it up again" }
    }

    private suspend fun clearBookkeeping() = transaction {
        dao.clearChanges()
        dao.clearFieldClocks()
        dao.clearSeqs()
    }

    private suspend fun deviceId(): String = checkNotNull(dao.getState()) { "The sync_state row is missing" }.deviceId

    private suspend fun saveKey(remote: SyncRemote) {
        val key = remote.key
        if (key == null) secrets.remove(SecretIds.SYNC_KEY) else secrets.put(SecretIds.SYNC_KEY, key.toBytes().toHexString())
    }

    private suspend fun storedKey(): SyncKey = when (val stored = secrets.get(SecretIds.SYNC_KEY)) {
        is StoredSecret.Present -> try {
            SyncKey.fromBytes(stored.value.hexToByteArray())
        } catch (_: IllegalArgumentException) {
            throw SyncPassphraseException(required = true)
        }
        else -> throw SyncPassphraseException(required = true)
    }

    private fun resting(config: SyncConfig?, enabled: Boolean): SyncStatus = when {
        config == null -> SyncStatus.Off
        !enabled -> SyncStatus.Restored(config.backend)
        else -> SyncStatus.Idle(config.backend, config.lastSyncAt?.let(Instant::ofEpochMilli))
    }

    /** Every file of the location, the manifest last: a half-done delete is still found and finished by another try. */
    private fun deleteEverything(store: SyncStore) {
        val paths = store.list("")
        paths.filter { it != SyncPaths.MANIFEST }.forEach(store::delete)
        if (SyncPaths.MANIFEST in paths) store.delete(SyncPaths.MANIFEST)
    }

    private companion object {
        const val BACKUP_FOLDER = "sync-backups"
        const val KEEP_BACKUPS = 3
        val BACKUP_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    }
}
