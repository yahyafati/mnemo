package com.yahyafati.mnemo.core.sync.store

import com.yahyafati.mnemo.core.sync.SyncAlreadyExistsException
import com.yahyafati.mnemo.core.sync.SyncException
import com.yahyafati.mnemo.core.sync.SyncNotFoundException
import com.yahyafati.mnemo.core.sync.SyncPaths
import com.yahyafati.mnemo.core.sync.SyncStore

/**
 * A [SyncStore] in memory, for tests and previews: two devices in one test share one instance and sync through
 * it, like two machines through one folder. It lives here and not in `:core:testing` so that the tests of every
 * module that syncs can use it without that module's dependencies.
 */
class InMemorySyncStore : SyncStore {
    private val files = java.util.TreeMap<String, ByteArray>()

    /** When set, every call throws it: the network is down, the token is revoked, the Drive is full. */
    @Volatile
    var failure: SyncException? = null

    /** The paths now in the store, in order. */
    val paths: List<String> get() = synchronized(files) { files.keys.toList() }

    fun contains(path: String): Boolean = synchronized(files) { path in files }

    /** Cuts [path] to its first [length] bytes: what a write that a crash or a sync tool interrupted leaves. */
    fun truncate(path: String, length: Int) = synchronized(files) {
        files[path] = files.getValue(path).copyOf(length)
    }

    /** Flips a byte of [path]: damage that keeps the length. */
    fun damage(path: String, at: Int) = synchronized(files) {
        files.getValue(path)[at] = (files.getValue(path)[at].toInt() xor 0x55).toByte()
    }

    override fun list(prefix: String): List<String> = synchronized(files) {
        fail()
        files.keys.filter { it.startsWith(prefix) }
    }

    override fun read(path: String): ByteArray = synchronized(files) {
        SyncPaths.requireValid(path)
        fail()
        files[path]?.copyOf() ?: throw SyncNotFoundException("$path isn't there")
    }

    override fun write(path: String, bytes: ByteArray) = synchronized(files) {
        SyncPaths.requireValid(path)
        fail()
        if (path in files) throw SyncAlreadyExistsException("$path exists")
        files[path] = bytes.copyOf()
    }

    override fun overwrite(path: String, bytes: ByteArray) = synchronized(files) {
        SyncPaths.requireValid(path)
        fail()
        files[path] = bytes.copyOf()
    }

    override fun delete(path: String) {
        SyncPaths.requireValid(path)
        synchronized(files) {
            fail()
            files.remove(path)
        }
    }

    private fun fail() {
        failure?.let { throw it }
    }
}
