package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.database.TransactionRunner
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.format.DeviceInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What this device says about itself in the location (`device.json`). */
data class DeviceDescription(val name: String, val platform: String, val appVersion: String)

/**
 * What one run did. [blocked] are change files that couldn't be read yet (`<device>/<seq>`): probably still being
 * copied, so the run stopped at them for that device and the next one tries again. [missingMedia] counts media
 * rows whose file hasn't reached this device (or the location) yet.
 */
data class SyncReport(
    val sentChanges: Int = 0,
    val sentFiles: Int = 0,
    val receivedChanges: Int = 0,
    val receivedFiles: Int = 0,
    val replayedCards: Int = 0,
    val blocked: List<String> = emptyList(),
    val missingMedia: Int = 0,
) {
    val isQuiet: Boolean get() = sentChanges == 0 && receivedChanges == 0 && replayedCards == 0
}

/**
 * One round of syncing with a location (docs/sync/ROADMAP.md S3, ADR 0013): send what changed here, apply what the
 * other devices sent, send the result of that (a deck merge or a replayed schedule is a change like any other), say
 * who this device is, and fetch media files that are missing. Nothing in it decides *when* to run or *which* location:
 * that is `SyncRepository`'s (S4), which also makes sure two rounds never overlap.
 *
 * Failures of the location (`SyncException`) end the run and leave this device's state as it was; the outbox is only
 * cleared after the change file is written, and a received file only counts as applied with the transaction that
 * applied it, so the next run picks up where this one stopped.
 */
internal class SyncEngine(
    private val database: MnemoDatabase,
    transaction: TransactionRunner,
    syncClock: SyncClock,
    private val clock: Clock,
    private val media: SyncMediaFiles,
    userSettings: UserSettingsRepository,
    replayer: ScheduleReplayer,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val dao = database.syncDao()
    private val settings = SchedulingSettings(userSettings)
    private val packer = SyncPacker(database, transaction, syncClock, media, settings, ioDispatcher)
    private val applier = SyncApplier(database, transaction, syncClock, clock, settings, replayer)

    /**
     * Syncs once with [remote]. Sync must be on ([com.yahyafati.mnemo.core.database.dao.SyncDao.setEnabled]), or local
     * changes aren't recorded. [announceSettings] is for the device that creates the location: it sends its scheduling
     * settings even though they haven't changed, which is how the others get them.
     */
    suspend fun sync(remote: SyncRemote, device: DeviceDescription, announceSettings: Boolean = false): SyncReport {
        val state = checkNotNull(dao.getState()) { "The sync_state row is missing" }
        check(state.enabled) { "Sync is off" }
        val me = state.deviceId

        val sentFirst = packer.pack(remote, me, announceSettings)
        val (files, blocked) = read(remote, me)
        val applied = applier.apply(files)
        // The merge may have changed rows (a deck merge, a replayed schedule): send them now rather than next time.
        val sentAfter = packer.pack(remote, me, announceSettings = false)
        writeDevice(remote, me, device)
        val missing = fetchMedia(remote)

        return SyncReport(
            sentChanges = sentFirst.changes + sentAfter.changes,
            sentFiles = sentFirst.files + sentAfter.files,
            receivedChanges = applied.changes,
            receivedFiles = applied.files,
            replayedCards = applied.replayedCards,
            blocked = blocked,
            missingMedia = missing,
        )
    }

    /**
     * The change files of the other devices that this device hasn't applied, in the order to apply them: by the clock
     * of their first change, then device, then number. A device's runs are stamped in increasing order, so its own
     * files stay in order, and a change that was made after seeing another is applied after it (the first change
     * of an edit's file is later than the file that made the row).
     */
    private suspend fun read(remote: SyncRemote, me: String): Pair<ReceivedFiles, List<String>> = withContext(ioDispatcher) {
        val seen = dao.getSeqs().associateBy { it.deviceId }
        val files = ArrayList<Received>()
        val present = HashMap<String, Set<Long>>()
        val blocked = ArrayList<String>()
        for (device in remote.listDevices()) {
            if (device == me) continue
            val position = seen[device]
            val late = position?.gaps?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty().toSet()
            val listed = remote.listChangeSeqs(device)
            present[device] = listed.toSet()
            for (seq in listed.filter { it > (position?.seq ?: 0) || it in late }) {
                val batch = try {
                    remote.readChanges(device, seq)
                } catch (_: SyncNotFoundException) {
                    continue // Taken away while we looked: compaction (S4) has put it in a snapshot.
                } catch (_: SyncCorruptException) {
                    // Probably still being written. Stop at it for this device: its later files may rely on it.
                    blocked += "$device/$seq"
                    break
                }
                files += Received(device, batch)
            }
        }
        files.sortWith(compareBy({ it.batch.clockFrom }, { it.deviceId }, { it.batch.seq }))
        ReceivedFiles(files, present) to blocked
    }

    private suspend fun writeDevice(remote: SyncRemote, me: String, device: DeviceDescription) {
        val seqs = dao.getSeqs().associate { it.deviceId to it.seq }
        val info = DeviceInfo(
            deviceId = me,
            name = device.name,
            platform = device.platform,
            appVersion = device.appVersion,
            lastSeq = seqs[me] ?: 0,
            applied = seqs - me,
            updatedAt = clock.now().toEpochMilli(),
        )
        withContext(ioDispatcher) { remote.writeDevice(info) }
    }

    /** Downloads the media files of live rows that this device doesn't have. Returns how many are still missing. */
    private suspend fun fetchMedia(remote: SyncRemote): Int {
        val missing = database.syncMergeDao().getLiveMediaIds().filter { !media.exists(it) }
        if (missing.isEmpty()) return 0
        return withContext(ioDispatcher) {
            var stillMissing = 0
            for (hash in missing) {
                try {
                    media.write(hash, remote.readMedia(hash))
                } catch (_: SyncNotFoundException) {
                    stillMissing++ // The other device hasn't uploaded it yet.
                } catch (_: SyncCorruptException) {
                    stillMissing++
                }
            }
            stillMissing
        }
    }
}
