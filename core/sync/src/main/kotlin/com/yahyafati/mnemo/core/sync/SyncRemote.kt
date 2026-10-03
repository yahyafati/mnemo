package com.yahyafati.mnemo.core.sync

import com.yahyafati.mnemo.core.sync.format.Change
import com.yahyafati.mnemo.core.sync.format.ChangeBatch
import com.yahyafati.mnemo.core.sync.format.DeviceInfo
import com.yahyafati.mnemo.core.sync.format.EncryptionParams
import com.yahyafati.mnemo.core.sync.format.FileCodec
import com.yahyafati.mnemo.core.sync.format.SyncFormat
import com.yahyafati.mnemo.core.sync.format.SyncKey
import com.yahyafati.mnemo.core.sync.format.SyncManifest
import kotlinx.serialization.SerializationException
import java.security.MessageDigest

/** A snapshot file in the location, by the device that wrote it and the clock value it is as of. */
data class SnapshotRef(val deviceId: String, val clock: Long) {
    val path: String get() = SyncPaths.snapshot(deviceId, clock)
}

/**
 * One sync location, as the merge engine sees it: typed files on top of a [SyncStore], with the envelope
 * (checksum, compression, encryption) handled here. It knows the layout and the formats of `sync.json`,
 * `device.json` and the change files, and treats snapshots and media as opaque bytes (their contents are S4's).
 *
 * What the callers must know about failure:
 *
 * - Reading a file that can't be trusted throws [SyncCorruptException]. Such a file is **ignored**: a change
 *   file that is [SyncCorruptException.maybeIncomplete] is probably still being written, so stop at it for that
 *   device and try again at the next sync, rather than skipping to the files after it.
 * - A format newer than this version is [SyncUnsupportedVersionException]; nothing is rewritten.
 * - Methods block; call them from an IO dispatcher.
 */
class SyncRemote private constructor(
    private val store: SyncStore,
    val manifest: SyncManifest,
    /** The key to keep in `SecretStore`; null if the location isn't encrypted. */
    val key: SyncKey?,
) {
    private val codec = FileCodec(key)

    val isEncrypted: Boolean get() = key != null

    // --- change files -------------------------------------------------------------------------------------

    /**
     * Writes [changes] as change files for [deviceId], numbered from [firstSeq]; a big list becomes several files of
     * at most about [maxFileBytes] each. Returns the numbers used, in order, empty for no changes. A file that
     * already exists and is whole is [SyncAlreadyExistsException] (pick the next number); one that a crash left
     * half-written is replaced, because only this device writes its own names.
     */
    fun writeChanges(
        deviceId: String,
        firstSeq: Long,
        changes: List<Change>,
        maxFileBytes: Int = SyncFormat.MAX_CHANGE_FILE_BYTES,
    ): List<Long> {
        val seqs = ArrayList<Long>()
        for ((i, chunk) in ChangeSplitter.split(changes, maxFileBytes).withIndex()) {
            val seq = firstSeq + i
            val path = SyncPaths.changeFile(deviceId, seq)
            val payload = ChangeSplitter.encode(ChangeBatch.of(deviceId, seq, chunk))
            writeHealing(path, codec.seal(path, payload, compress = true))
            seqs += seq
        }
        return seqs
    }

    /** The numbers of [deviceId]'s change files after [after], ascending. Files that aren't change files are left out. */
    fun listChangeSeqs(deviceId: String, after: Long = 0): List<Long> =
        store.list(SyncPaths.changesPrefix(deviceId)).mapNotNull(SyncPaths::seqOf).filter { it > after }.sorted()

    fun readChanges(deviceId: String, seq: Long): ChangeBatch {
        val path = SyncPaths.changeFile(deviceId, seq)
        val batch = try {
            ChangeSplitter.decode(codec.open(path, store.read(path)))
        } catch (e: SerializationException) {
            throw SyncCorruptException("$path isn't readable: ${e.message}")
        }
        if (batch.deviceId != deviceId || batch.seq != seq) throw SyncCorruptException("$path holds another file's changes")
        return batch
    }

    fun deleteChanges(deviceId: String, seq: Long) = store.delete(SyncPaths.changeFile(deviceId, seq))

    // --- devices ------------------------------------------------------------------------------------------

    /** The ids of every device that has a folder here, including this one. */
    fun listDevices(): List<String> = store.list(SyncPaths.DEVICES).mapNotNull(SyncPaths::deviceOf).distinct().sorted()

    /** A device's description, or null if it has none yet. Unreadable ones throw [SyncCorruptException]: treat as unknown. */
    fun readDevice(deviceId: String): DeviceInfo? {
        val path = SyncPaths.deviceInfo(deviceId)
        val bytes = try {
            store.read(path)
        } catch (_: SyncNotFoundException) {
            return null
        }
        val info = try {
            SyncFormat.json.decodeFromString(DeviceInfo.serializer(), codec.open(path, bytes).decodeToString())
        } catch (e: SerializationException) {
            throw SyncCorruptException("$path isn't readable: ${e.message}")
        }
        if (info.deviceId != deviceId) throw SyncCorruptException("$path describes another device")
        return info
    }

    /** Replaces this device's own `device.json`. */
    fun writeDevice(info: DeviceInfo) {
        val path = SyncPaths.deviceInfo(info.deviceId)
        val payload = SyncFormat.json.encodeToString(DeviceInfo.serializer(), info).toByteArray()
        store.overwrite(path, codec.seal(path, payload, compress = false))
    }

    // --- snapshots ----------------------------------------------------------------------------------------

    fun writeSnapshot(deviceId: String, clock: Long, payload: ByteArray): SnapshotRef {
        val ref = SnapshotRef(deviceId, clock)
        writeHealing(ref.path, codec.seal(ref.path, payload, compress = true))
        return ref
    }

    fun readSnapshot(ref: SnapshotRef): ByteArray = codec.open(ref.path, store.read(ref.path))

    /** Every snapshot, oldest clock first. */
    fun listSnapshots(): List<SnapshotRef> = store.list(SyncPaths.SNAPSHOTS)
        .mapNotNull(SyncPaths::snapshotOf)
        .map { (device, clock) -> SnapshotRef(device, clock) }
        .sortedWith(compareBy({ it.clock }, { it.deviceId }))

    fun deleteSnapshot(ref: SnapshotRef) = store.delete(ref.path)

    // --- media --------------------------------------------------------------------------------------------

    /**
     * Uploads a media file under its SHA-256 [sha256] (lowercase hex). A file that is already there is left alone:
     * the name is the content. [bytes] must hash to [sha256].
     */
    fun writeMedia(sha256: String, bytes: ByteArray) {
        val path = SyncPaths.media(sha256)
        require(sha256Of(bytes) == sha256) { "The bytes don't hash to $sha256" }
        try {
            writeHealing(path, codec.seal(path, bytes, compress = false))
        } catch (_: SyncAlreadyExistsException) {
            // Whole and the same, by its name.
        }
    }

    /** The media file, checked against its name. */
    fun readMedia(sha256: String): ByteArray {
        val path = SyncPaths.media(sha256)
        val bytes = codec.open(path, store.read(path))
        if (sha256Of(bytes) != sha256) throw SyncCorruptException("$path doesn't match its name")
        return bytes
    }

    /** The hashes of every media file in the location. */
    fun listMedia(): List<String> = store.list(SyncPaths.MEDIA).mapNotNull(SyncPaths::mediaOf).sorted()

    fun deleteMedia(sha256: String) = store.delete(SyncPaths.media(sha256))

    // --- plumbing -----------------------------------------------------------------------------------------

    /**
     * Writes a new file. If the name is taken by a file that is cut off (a crash during an earlier write of the
     * same file), the remnant is replaced; a whole file stays and the exists-error goes to the caller.
     */
    private fun writeHealing(path: String, sealed: ByteArray) {
        try {
            store.write(path, sealed)
        } catch (exists: SyncAlreadyExistsException) {
            try {
                codec.open(path, store.read(path))
            } catch (_: SyncCorruptException) {
                store.delete(path)
                store.write(path, sealed)
                return
            } catch (_: SyncNotFoundException) {
                store.write(path, sealed)
                return
            }
            throw exists
        }
    }

    companion object {
        /** `sync.json`, or [SyncNotFoundException] when the location is empty. */
        fun readManifest(store: SyncStore): SyncManifest = SyncManifest.decode(store.read(SyncPaths.MANIFEST))

        /**
         * Starts a new location: writes `sync.json`. [SyncAlreadyExistsException] if the location has one (that
         * is Join). With a [passphrase] the location is encrypted ([iterations] is PBKDF2's work factor).
         */
        fun create(
            store: SyncStore,
            collectionId: String,
            createdAt: Long,
            passphrase: CharArray? = null,
            iterations: Int = EncryptionParams.DEFAULT_ITERATIONS,
        ): SyncRemote {
            val keyed = passphrase?.let { EncryptionParams.create(it, iterations) }
            val manifest = SyncManifest(collectionId = collectionId, createdAt = createdAt, encryption = keyed?.first)
            try {
                store.write(SyncPaths.MANIFEST, manifest.encode())
            } catch (e: SyncAlreadyExistsException) {
                throw e
            } catch (e: SyncException) {
                removeBrokenManifest(store)
                throw e
            }
            return SyncRemote(store, manifest, keyed?.second)
        }

        /** Opens an existing location with its [passphrase] (null for an unencrypted one). */
        fun open(store: SyncStore, passphrase: CharArray? = null): SyncRemote {
            val manifest = readManifest(store)
            val params = manifest.encryption ?: return SyncRemote(store, manifest, null)
            val key = params.unlock(passphrase ?: throw SyncPassphraseException(required = true))
            return SyncRemote(store, manifest, key)
        }

        /** Opens an existing location with the key kept from an earlier session (`SecretStore`). */
        fun openWithKey(store: SyncStore, key: SyncKey?): SyncRemote {
            val manifest = readManifest(store)
            val params = manifest.encryption ?: return SyncRemote(store, manifest, null)
            if (key == null) throw SyncPassphraseException(required = true)
            if (!params.verify(key)) throw SyncPassphraseException(required = false)
            return SyncRemote(store, manifest, key)
        }

        /**
         * A create that failed halfway may have left a cut-off `sync.json`, which would make the location look
         * taken. Remove it, but only if it is unreadable: a whole manifest may be another device's, and the failure
         * may have been before anything was written.
         */
        private fun removeBrokenManifest(store: SyncStore) {
            val broken = try {
                SyncManifest.decode(store.read(SyncPaths.MANIFEST))
                false
            } catch (_: SyncCorruptException) {
                true
            } catch (_: SyncException) {
                false
            }
            if (broken) runCatching { store.delete(SyncPaths.MANIFEST) }
        }

        private fun sha256Of(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
