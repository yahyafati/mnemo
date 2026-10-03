package com.yahyafati.mnemo.core.sync.format

import kotlinx.serialization.Serializable

/**
 * `devices/<deviceId>/device.json`: what a device says about itself, and the one file it overwrites.
 *
 * [lastSeq] is its newest change file. [applied] says, per other device, the newest change file this device has
 * applied: compaction (S4) deletes a change file only once every recent device has applied it. [updatedAt] is
 * this device's wall clock in epoch milliseconds, used only to tell a device that has been away for months from
 * one that hasn't (never to order changes). An unreadable `device.json` means "unknown", which must never be
 * read as "has applied everything".
 *
 * [applied] is only the newest number, so [gaps] adds, per device, the older numbers this device still waits for (a
 * sync tool may deliver files late); compaction keeps a file whose number is in somebody's gaps. [snapshot] says what
 * the newest snapshot this device wrote covers, which tells the others (without downloading it) which change files it
 * made redundant. Both are new in S4 and optional, so older files still read.
 */
@Serializable
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val platform: String,
    val appVersion: String,
    val lastSeq: Long = 0,
    val applied: Map<String, Long> = emptyMap(),
    val updatedAt: Long,
    val gaps: Map<String, List<Long>> = emptyMap(),
    val snapshot: SnapshotMeta? = null,
)

/**
 * What a snapshot covers: the change files its writer had applied when it wrote it, per device ([applied] is the newest
 * number, [gaps] the older ones that were still missing), as of the logical clock value [clock]. A snapshot written
 * by [deviceId] is `snapshots/<deviceId>-<clock>.mns`.
 */
@Serializable
data class SnapshotMeta(
    val deviceId: String,
    val clock: Long,
    val applied: Map<String, Long>,
    val gaps: Map<String, List<Long>> = emptyMap(),
)
