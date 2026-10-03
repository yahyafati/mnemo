package com.yahyafati.mnemo.core.sync

/**
 * Where a sync location's files are kept: a folder, Google Drive, a WebDAV server (ADR 0013). It is a flat set of
 * files named by [SyncPaths]; everything above it (format, encryption, merging) is the same for every backend.
 *
 * The contract that lets a backend be this simple:
 *
 * - A path is written **once**, by [write], and never changes. Only a device's own `device.json` is replaced, with
 *   [overwrite]. So no two devices ever write the same file, and a backend needs no locks.
 * - Calls block. Callers run them on an IO dispatcher.
 * - Every failure is a [SyncException]: [SyncOfflineException], [SyncAuthException], [SyncQuotaException],
 *   [SyncNotFoundException], [SyncAlreadyExistsException] or [SyncIoException]. A path that isn't a valid
 *   [SyncPaths] path is an [IllegalArgumentException].
 * - A store never follows redirects and takes its base URL or folder as a constructor parameter.
 */
interface SyncStore {
    /** Every file whose path starts with [prefix], in path order; `""` lists everything. Foreign files are left out. */
    fun list(prefix: String): List<String>

    /** The whole file, or [SyncNotFoundException]. */
    fun read(path: String): ByteArray

    /** Creates [path]; [SyncAlreadyExistsException] if it exists. A write cut off halfway may leave a partial file. */
    fun write(path: String, bytes: ByteArray)

    /** Creates or replaces [path]. Only for a device's own `device.json`. */
    fun overwrite(path: String, bytes: ByteArray)

    /** Deletes [path]. A file that isn't there is not an error. */
    fun delete(path: String)
}
