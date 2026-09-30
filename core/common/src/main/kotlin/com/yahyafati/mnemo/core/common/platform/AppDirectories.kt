package com.yahyafati.mnemo.core.common.platform

import java.io.File

/**
 * Where the app keeps its files. Shared code asks for a location here instead of reading
 * `Context.filesDir`, so the same code runs where there is no `Context` (the desktop app).
 *
 * The layout is part of the collection format: backups and restores move [media],
 * [databaseFile] and [dataStoreFile] by name, and [secrets] must stay outside all of them so that
 * no backup or export can contain it.
 */
interface AppDirectories {
    /** The app's private files directory. */
    val files: File

    /** Scratch space the system may clear; import, export and backup work in it. */
    val cache: File

    /** Media, one file per content hash (ARCHITECTURE §6). */
    val media: File

    /** Encrypted API keys. Never backed up, and never read by a backup or an export. */
    val secrets: File

    /** Where a restore is unpacked before the next start applies it (`PendingRestore`). */
    val restoreStaging: File

    /** The Room database file called [name]. Its `-wal` and `-shm` files sit next to it. */
    fun databaseFile(name: String): File

    /** The preferences DataStore file called [name]. */
    fun dataStoreFile(name: String): File
}
