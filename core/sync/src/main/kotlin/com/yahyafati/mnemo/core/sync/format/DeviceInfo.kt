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
)
