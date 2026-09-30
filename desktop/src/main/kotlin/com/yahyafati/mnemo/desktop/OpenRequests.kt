package com.yahyafati.mnemo.desktop

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Files that a second Mnemo was asked to open, handed to the first (desktop ROADMAP D8). Windows and Linux
 * start the program with the path as an argument when a `.apkg` is opened from the file manager; the
 * collection admits one process ([com.yahyafati.mnemo.core.data.desktop.SingleInstanceLock]), so the newcomer
 * leaves the paths in a folder of the data directory and exits, and the running app picks them up.
 *
 * Each request is one small file, written under another name and renamed, so the reader never sees half of
 * one. A request older than [MAX_AGE_MILLIS] is dropped: it was left for an app that has since closed, and
 * opening it at some later start would surprise.
 */
internal object OpenRequests {
    const val DIRECTORY = "open-requests"
    const val MAX_AGE_MILLIS = 60_000L
    private const val EXTENSION = "request"

    /** Leaves [paths] for the running app. False if they could not be written. */
    fun send(dataDirectory: File, paths: List<String>, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (paths.isEmpty()) return true
        val directory = File(dataDirectory, DIRECTORY)
        return try {
            directory.mkdirs()
            val name = "%020d".format(nowMillis) + "-" + UUID.randomUUID()
            val partial = File(directory, "$name.tmp")
            partial.writeText(paths.joinToString("\n"))
            partial.renameTo(File(directory, "$name.$EXTENSION"))
        } catch (e: IOException) {
            false
        }
    }

    /** The paths of every waiting request, oldest first, and removes them. */
    fun take(dataDirectory: File, nowMillis: Long = System.currentTimeMillis()): List<String> {
        val requests = File(dataDirectory, DIRECTORY).listFiles { file -> file.extension == EXTENSION }?.sortedBy { it.name } ?: return emptyList()
        return requests.flatMap { request ->
            val fresh = nowMillis - request.lastModified() <= MAX_AGE_MILLIS
            val lines = if (fresh) runCatching { request.readLines() }.getOrDefault(emptyList()) else emptyList()
            request.delete()
            lines.filter { it.isNotBlank() }
        }
    }

    /** The files among command-line [arguments] that exist, as absolute paths (the newcomer's working directory is not the running app's). */
    fun existingFiles(arguments: Array<String>): List<String> =
        arguments.map { File(it).absoluteFile }.filter { it.isFile }.map { it.path }
}
