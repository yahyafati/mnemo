package com.yahyafati.mnemo.core.data.desktop

import java.io.IOException
import kotlin.system.exitProcess

/** Starts Mnemo again and ends this process, so a staged restore is applied before anything opens the collection. */
fun interface AppRestarter {
    fun restart()
}

/**
 * Starts the same command again (the packaged app's launcher, or `java` with the same arguments
 * under `gradlew :desktop:run`), then exits. The new process waits for this one to release the
 * collection (see [SingleInstanceLock.tryAcquire]). If the command can't be found, it only exits,
 * and the user opens Mnemo again.
 */
object ProcessAppRestarter : AppRestarter {
    /** Set in the new process's environment: it should wait for this one to release the collection. */
    const val RESTARTED_VARIABLE = "MNEMO_RESTARTED"

    override fun restart() {
        val info = ProcessHandle.current().info()
        val command = info.command().orElse(null)
        if (command != null) {
            val arguments = info.arguments().orElse(emptyArray())
            try {
                ProcessBuilder(listOf(command) + arguments)
                    .inheritIO()
                    .apply { environment()[RESTARTED_VARIABLE] = "1" }
                    .start()
            } catch (e: IOException) {
                // Nothing to do but exit.
            }
        }
        exitProcess(0)
    }
}
