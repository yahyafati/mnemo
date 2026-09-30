package com.yahyafati.mnemo.core.database

import androidx.room.withTransaction
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import java.io.File

/**
 * Consistent copies of the open database, for backups (ARCHITECTURE §5.4), without closing Room.
 *
 * The WAL is checkpointed into the main file first, then the main file (and whatever WAL a
 * concurrent write left behind) is copied while holding a write transaction, so no commit can
 * land halfway through the copy. SQLite replays a copied WAL when the file is opened again.
 */
class DatabaseSnapshot(
    private val directories: AppDirectories,
    private val database: MnemoDatabase,
) {
    /** The schema version, recorded in backups so a restore can refuse a newer one. */
    val version: Int get() = database.openHelper.readableDatabase.version

    /** Copies the database into [dir] and returns the files written (the database, maybe its WAL). */
    suspend fun copyTo(dir: File): List<File> {
        val name = checkNotNull(database.openHelper.databaseName) { "An in-memory database has no file to copy" }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        return database.withTransaction {
            val main = directories.databaseFile(name)
            listOf(main, File(main.path + WAL_SUFFIX))
                .filter { it.exists() && it.length() > 0 }
                .map { it.copyTo(File(dir, it.name), overwrite = true) }
        }
    }

    companion object {
        const val WAL_SUFFIX = "-wal"

        /** Every file SQLite may keep for the database [name]. */
        fun files(directories: AppDirectories, name: String = MnemoDatabase.NAME): List<File> {
            val main = directories.databaseFile(name)
            return listOf(main) + listOf(WAL_SUFFIX, "-shm", "-journal").map { File(main.path + it) }
        }
    }
}
