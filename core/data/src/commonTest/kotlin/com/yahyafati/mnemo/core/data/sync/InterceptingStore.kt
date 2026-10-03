package com.yahyafati.mnemo.core.data.sync

import com.yahyafati.mnemo.core.sync.SyncOfflineException
import com.yahyafati.mnemo.core.sync.SyncStore

/** A store that misbehaves on request: hides files (they haven't arrived yet), fails after a write, runs code on a listing. */
internal class InterceptingStore(private val delegate: SyncStore) : SyncStore {
    val hidden = HashSet<String>()

    /** The next write of a change file succeeds and is then reported as failed, like a crash right after it. */
    var failAfterNextChangeWrite = false

    /** Runs when the devices are listed, which happens between sending and receiving. */
    var onListDevices: (() -> Unit)? = null

    override fun list(prefix: String): List<String> {
        if (prefix == "devices/") onListDevices?.let { hook ->
            onListDevices = null
            hook()
        }
        return delegate.list(prefix).filter { it !in hidden }
    }

    override fun read(path: String): ByteArray = delegate.read(path)

    override fun write(path: String, bytes: ByteArray) {
        delegate.write(path, bytes)
        if (failAfterNextChangeWrite && "changes" in path) {
            failAfterNextChangeWrite = false
            throw SyncOfflineException("the connection dropped after $path")
        }
    }

    override fun overwrite(path: String, bytes: ByteArray) = delegate.overwrite(path, bytes)

    override fun delete(path: String) = delegate.delete(path)
}
