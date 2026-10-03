package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.sync.SyncCorruptException
import com.yahyafati.mnemo.core.sync.SyncRemote
import com.yahyafati.mnemo.core.sync.format.DeviceInfo
import com.yahyafati.mnemo.core.sync.format.SnapshotMeta
import java.time.Duration

/** The numbers behind snapshots and clean-up (docs/sync/ROADMAP.md S4); tests shrink them. */
internal data class SyncPolicy(
    /** A device writes a snapshot once this many change files are newer than the newest one. */
    val snapshotAfterFiles: Int = 50,
    /** Snapshots and clean-up are considered at most this often (they list and read more than a round does). */
    val maintenanceEvery: Duration = Duration.ofHours(1),
    /** A device whose `device.json` is older is not waited for: it has to join again if files it needs are gone. */
    val inactiveAfter: Duration = Duration.ofDays(90),
    /** A media file that nothing references is deleted after this long. */
    val mediaGrace: Duration = Duration.ofDays(30),
    /** Older snapshots are deleted, but this many of the newest stay. */
    val keepSnapshots: Int = 2,
)

internal class MaintenanceOutcome(
    val snapshotWritten: Boolean = false,
    val changeFilesDeleted: Int = 0,
    val snapshotsDeleted: Int = 0,
    val mediaDeleted: Int = 0,
)

/**
 * What keeps a sync location small (docs/sync/ROADMAP.md S4, ADR 0013): snapshots, deleting the change files they
 * make redundant, and deleting media nobody uses. Everything here only *removes* what is provably not needed any
 * more, and anything it can't be sure of it leaves:
 *
 * - A change file goes only when the newest snapshot has it (by its writer's `device.json`, which says what the
 *   snapshot covers), and every device seen in the last [SyncPolicy.inactiveAfter] has applied it, as its own
 *   `device.json` says, late files (gaps) included. A device that has no readable `device.json` is unknown, and one
 *   unknown device stops the clean-up: it must never count as "has applied everything".
 * - A media file goes only after it has been unreferenced in two snapshots at least [SyncPolicy.mediaGrace] apart.
 *   The snapshots hold the first time it was noticed, since a store can't say how old a file is.
 *
 * All calls block on the location: run them on an IO dispatcher.
 */
internal class SyncMaintenance(
    private val database: MnemoDatabase,
    private val snapshots: SyncSnapshots,
    private val clock: Clock,
    private val policy: SyncPolicy = SyncPolicy(),
) {
    /** Whether [lastMaintenanceAt] is long enough ago to look again. */
    fun isDue(lastMaintenanceAt: Long?): Boolean =
        lastMaintenanceAt == null || clock.now().toEpochMilli() - lastMaintenanceAt >= policy.maintenanceEvery.toMillis()

    /** Whether a device whose last round was at [lastSyncAt] counts as inactive: the others don't wait for it. */
    fun isInactive(lastSyncAt: Long): Boolean = clock.now().toEpochMilli() - lastSyncAt > policy.inactiveAfter.toMillis()

    /** Writes a snapshot if enough change files piled up, then deletes what that makes redundant. Run right after a sync round. */
    suspend fun run(remote: SyncRemote, me: String): MaintenanceOutcome {
        val infos = readDevices(remote).toMutableMap()
        var newest = newestSnapshot(infos.values)
        var written = false
        var snapshotsDeleted = 0
        var mediaDeleted = 0

        if (filesSince(remote, infos.keys, newest) >= policy.snapshotAfterFiles) {
            val own = infos[me]
            if (own != null) {
                val result = writeSnapshot(remote, me, own)
                newest = result.first
                infos[me] = own.copy(snapshot = newest)
                mediaDeleted = result.second
                snapshotsDeleted = pruneSnapshots(remote)
                written = true
            }
        }
        return MaintenanceOutcome(written, compact(remote, infos, newest), snapshotsDeleted, mediaDeleted)
    }

    /**
     * Writes a snapshot of this device now, announces it in this device's `device.json` and returns what it covers.
     * Used when a location is created, so that the first device to join has something to load.
     */
    suspend fun writeFirstSnapshot(remote: SyncRemote, me: String): SnapshotMeta {
        val own = remote.readDevice(me) ?: error("The device description must exist before the first snapshot")
        return writeSnapshot(remote, me, own).first
    }

    private suspend fun writeSnapshot(remote: SyncRemote, me: String, own: DeviceInfo): Pair<SnapshotMeta, Int> {
        val now = clock.now().toEpochMilli()
        val previous = try {
            remote.listSnapshots().lastOrNull()?.let { snapshots.head(remote.readSnapshot(it)).unreferenced }.orEmpty()
        } catch (_: SyncCorruptException) {
            emptyMap() // The clock for the media clean-up starts again: nothing is deleted early because of it.
        }
        val live = database.syncMergeDao().getLiveMediaIds().toSet()
        val unreferenced = (remote.listMedia().toSet() - live).associateWith { previous[it] ?: now }

        val built = snapshots.build(me, unreferenced)
        remote.writeSnapshot(me, built.head.clock, built.bytes)
        val meta = built.head.meta()
        remote.writeDevice(own.copy(snapshot = meta, updatedAt = now))

        val expired = unreferenced.filterValues { now - it >= policy.mediaGrace.toMillis() }.keys
        expired.forEach(remote::deleteMedia)
        return meta to expired.size
    }

    private fun pruneSnapshots(remote: SyncRemote): Int {
        val old = remote.listSnapshots().dropLast(policy.keepSnapshots.coerceAtLeast(1))
        old.forEach(remote::deleteSnapshot)
        return old.size
    }

    /** The change files newer than what [newest] covers, over all devices. */
    private fun filesSince(remote: SyncRemote, devices: Collection<String>, newest: SnapshotMeta?): Int = devices.sumOf { device ->
        remote.listChangeSeqs(device).count { seq -> !covered(newest, device, seq) }
    }

    private fun covered(snapshot: SnapshotMeta?, device: String, seq: Long): Boolean =
        snapshot != null && seq <= (snapshot.applied[device] ?: 0) && seq !in snapshot.gaps[device].orEmpty()

    /** Deletes the change files that [newest] covers and every recent device has applied. Returns how many. */
    private fun compact(remote: SyncRemote, infos: Map<String, DeviceInfo?>, newest: SnapshotMeta?): Int {
        if (newest == null) return 0
        if (infos.values.any { it == null }) return 0 // An unknown device may need anything.
        if (remote.listSnapshots().none { it.deviceId == newest.deviceId && it.clock == newest.clock }) return 0

        val now = clock.now().toEpochMilli()
        val recent = infos.values.filterNotNull().filter { now - it.updatedAt <= policy.inactiveAfter.toMillis() }
        var deleted = 0
        for (device in infos.keys) {
            for (seq in remote.listChangeSeqs(device)) {
                if (!covered(newest, device, seq)) continue
                val needed = recent.any { other ->
                    other.deviceId != device && ((other.applied[device] ?: 0) < seq || seq in other.gaps[device].orEmpty())
                }
                if (needed) continue
                remote.deleteChanges(device, seq)
                deleted++
            }
        }
        return deleted
    }

    /**
     * Whether change files that a device with these positions ([applied] and [gaps], by device) hasn't applied are
     * gone from the location: the newest snapshot covers them and they are not listed. Only a device that was away
     * long enough to count as inactive can be in that position, so the caller asks only then; a file that is merely
     * late to arrive in a folder can't be mistaken for it.
     */
    fun lostFiles(remote: SyncRemote, me: String, applied: Map<String, Long>, gaps: Map<String, List<Long>>): Boolean {
        val newest = newestSnapshot(readDevices(remote).values) ?: return false
        for ((device, covers) in newest.applied) {
            if (device == me) continue
            val listed = remote.listChangeSeqs(device).toSet()
            val skipped = newest.gaps[device].orEmpty().toSet()
            val have = applied[device] ?: 0
            if ((have + 1..covers).any { it !in listed && it !in skipped }) return true
            if (gaps[device].orEmpty().any { it <= covers && it !in listed && it !in skipped }) return true
        }
        return false
    }

    /** Every device's description; null for one that has none or can't be read. */
    private fun readDevices(remote: SyncRemote): Map<String, DeviceInfo?> = remote.listDevices().associateWith { id ->
        try {
            remote.readDevice(id)
        } catch (_: SyncCorruptException) {
            null
        }
    }

    private fun newestSnapshot(infos: Collection<DeviceInfo?>): SnapshotMeta? =
        infos.mapNotNull { it?.snapshot }.maxWithOrNull(compareBy({ it.clock }, { it.deviceId }))
}
