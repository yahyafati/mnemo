package com.yahyafati.mnemo.core.sync

/**
 * The remote layout (ADR 0013):
 *
 * ```
 * sync.json                              format version, collection id, created at, encryption parameters
 * devices/<deviceId>/device.json         one device's description and what it has applied
 * devices/<deviceId>/changes/<seq>.mnc   a batch of changes, written once
 * snapshots/<deviceId>-<clock>.mns       the whole collection as of a clock value
 * media/<sha256>                         a media file, written once
 * ```
 *
 * Paths use `/`, are made of letters, digits, `.`, `-` and `_`, start with one of the names above, and have no `__` in them: a store that has no
 * folders (the folder backend, Drive) keeps the path in one file name with `/` written as `__`.
 */
object SyncPaths {
    const val MANIFEST = "sync.json"
    const val DEVICES = "devices/"
    const val SNAPSHOTS = "snapshots/"
    const val MEDIA = "media/"

    private const val DEVICE_FILE = "device.json"
    private const val CHANGES = "changes/"
    private const val CHANGE_EXTENSION = ".mnc"
    private const val SNAPSHOT_EXTENSION = ".mns"

    private const val SEGMENT = "[A-Za-z0-9][A-Za-z0-9._-]*"
    private val segment = Regex(SEGMENT)
    private val valid = Regex("$SEGMENT(/$SEGMENT)*")
    private val sha256 = Regex("[0-9a-f]{64}")

    /** A plain name under one of the layout's roots: nothing else is ever written, so nothing else is listed. */
    fun isValid(path: String): Boolean =
        valid.matches(path) && "__" !in path && ".." !in path &&
            (path == MANIFEST || path.startsWith(DEVICES) || path.startsWith(SNAPSHOTS) || path.startsWith(MEDIA))

    fun requireValid(path: String) = require(isValid(path)) { "Not a sync path: $path" }

    /** [deviceId] is a UUID; anything else could climb out of its folder. */
    private fun requireId(deviceId: String) = require(segment.matches(deviceId) && "__" !in deviceId && ".." !in deviceId) { "Not a device id: $deviceId" }

    fun deviceInfo(deviceId: String): String = "$DEVICES${id(deviceId)}/$DEVICE_FILE"

    fun changesPrefix(deviceId: String): String = "$DEVICES${id(deviceId)}/$CHANGES"

    fun changeFile(deviceId: String, seq: Long): String {
        require(seq >= 0) { "seq must not be negative" }
        return "${changesPrefix(deviceId)}$seq$CHANGE_EXTENSION"
    }

    fun snapshot(deviceId: String, clock: Long): String {
        require(clock >= 0) { "clock must not be negative" }
        return "$SNAPSHOTS${id(deviceId)}-$clock$SNAPSHOT_EXTENSION"
    }

    fun media(sha256: String): String {
        require(this.sha256.matches(sha256)) { "Not a SHA-256: $sha256" }
        return "$MEDIA$sha256"
    }

    /** The device whose folder holds [path] (`devices/<id>/…`), or null. */
    fun deviceOf(path: String): String? {
        val parts = path.split('/')
        if (parts.size < 3 || parts[0] != "devices") return null
        return parts[1].takeIf { it.isNotEmpty() }
    }

    /** `devices/<deviceId>/changes/<seq>.mnc` → `<seq>`, or null for any other path. */
    fun seqOf(path: String): Long? {
        val parts = path.split('/')
        if (parts.size != 4 || parts[0] != "devices" || parts[2] != "changes") return null
        val name = parts[3]
        if (!name.endsWith(CHANGE_EXTENSION)) return null
        return name.removeSuffix(CHANGE_EXTENSION).takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
    }

    /** `snapshots/<deviceId>-<clock>.mns` → (deviceId, clock), or null. */
    fun snapshotOf(path: String): Pair<String, Long>? {
        val name = path.removePrefix(SNAPSHOTS).takeIf { it != path && '/' !in it } ?: return null
        if (!name.endsWith(SNAPSHOT_EXTENSION)) return null
        val stem = name.removeSuffix(SNAPSHOT_EXTENSION)
        val clock = stem.substringAfterLast('-', "").takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull() ?: return null
        val device = stem.substringBeforeLast('-').takeIf { it.isNotEmpty() } ?: return null
        return device to clock
    }

    /** `media/<sha256>` → `<sha256>`, or null. */
    fun mediaOf(path: String): String? =
        path.removePrefix(MEDIA).takeIf { it != path && sha256.matches(it) }

    private fun id(deviceId: String): String {
        requireId(deviceId)
        return deviceId
    }
}
