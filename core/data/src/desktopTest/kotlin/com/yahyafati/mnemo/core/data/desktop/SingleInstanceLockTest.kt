package com.yahyafati.mnemo.core.data.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The lock is an OS file lock: the second "Mnemo" here is a real second process. */
class SingleInstanceLockTest {
    private val directory: File = Files.createTempDirectory("mnemo-lock").toFile()
    private var holder: Process? = null

    @AfterTest
    fun cleanUp() {
        holder?.destroyForcibly()
        directory.deleteRecursively()
    }

    /** Starts a process that takes the lock and holds it until it is killed. */
    private fun startHolder(): Process {
        val java = File(System.getProperty("java.home"), "bin/java").path
        val process = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LockHolder::class.java.name, directory.path)
            .redirectErrorStream(true)
            .start()
        holder = process
        assertEquals("locked", process.inputStream.bufferedReader().readLine())
        return process
    }

    @Test
    fun aSecondProcessDoesNotGetTheCollection() {
        startHolder()

        assertNull(SingleInstanceLock.tryAcquire(directory))
    }

    @Test
    fun theLockIsFreeOnceTheHolderIsGone() {
        // Even a killed process (a crash) leaves nothing behind.
        startHolder().destroyForcibly().waitFor()

        SingleInstanceLock.tryAcquire(directory).let { assertNotNull(it); it.close() }
    }

    @Test
    fun aRestartWaitsForTheOldProcessToLetGo() {
        val old = startHolder()
        Thread {
            Thread.sleep(300)
            old.destroyForcibly()
        }.start()

        val lock = SingleInstanceLock.tryAcquire(directory, waitMillis = 5_000)

        assertNotNull(lock)
        lock.close()
    }

    @Test
    fun aRestartGivesUpIfTheOldProcessNeverLetsGo() {
        startHolder()

        assertNull(SingleInstanceLock.tryAcquire(directory, waitMillis = 300))
    }

    @Test
    fun theSameProcessCannotTakeItTwiceEither() {
        val first = assertNotNull(SingleInstanceLock.tryAcquire(directory))

        assertNull(SingleInstanceLock.tryAcquire(directory))
        first.close()
        SingleInstanceLock.tryAcquire(directory).let { assertNotNull(it); it.close() }
    }
}

/** The other "Mnemo": locks the directory in its first argument and waits. */
object LockHolder {
    @JvmStatic
    fun main(args: Array<String>) {
        SingleInstanceLock.tryAcquire(File(args[0])) ?: error("could not lock")
        println("locked")
        System.out.flush()
        Thread.sleep(Long.MAX_VALUE)
    }
}
