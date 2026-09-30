package com.yahyafati.mnemo.core.data.desktop

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * One running Mnemo per collection: the database and preferences files must not be opened by two
 * processes. The lock is an OS file lock in the data directory, released when the process ends, so
 * a crash never leaves the collection locked.
 */
class SingleInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : Closeable {
    override fun close() {
        runCatching { lock.release() }
        runCatching { channel.close() }
    }

    companion object {
        const val FILE_NAME = "mnemo.lock"

        /**
         * Takes the lock in [directory], or returns null if another process holds it. A restart
         * starts the new process before the old one has exited, so this keeps trying for
         * [waitMillis] first.
         */
        fun tryAcquire(directory: File, waitMillis: Long = 0): SingleInstanceLock? {
            directory.mkdirs()
            val deadline = System.nanoTime() + waitMillis * 1_000_000
            while (true) {
                acquireOnce(File(directory, FILE_NAME))?.let { return it }
                if (System.nanoTime() >= deadline) return null
                Thread.sleep(POLL_MILLIS)
            }
        }

        private fun acquireOnce(file: File): SingleInstanceLock? {
            val channel = try {
                RandomAccessFile(file, "rw").channel
            } catch (e: IOException) {
                return null
            }
            val lock = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null // held by this very process
            } catch (e: IOException) {
                null
            }
            if (lock == null) {
                runCatching { channel.close() }
                return null
            }
            return SingleInstanceLock(channel, lock)
        }

        private const val POLL_MILLIS = 100L
    }
}
