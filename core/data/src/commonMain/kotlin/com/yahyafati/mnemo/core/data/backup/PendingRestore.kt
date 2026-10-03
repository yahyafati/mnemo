package com.yahyafati.mnemo.core.data.backup

import androidx.sqlite.SQLiteDriver
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import java.io.File

/**
 * Applies a restore staged by [BackupManager.stage]. It must run before anything opens the
 * database or the preferences, so the app calls [applyIfPresent] first thing, and the Settings
 * screen restarts the app once a restore is staged.
 *
 * The restored database keeps this device's sync identity (its `sync_state.deviceId`) and has sync
 * turned off, because the backup may come from another device: restored as it is, a second device
 * would pose as the first in the sync location. What to do with the sync data is then the user's call
 * (docs/sync/ROADMAP.md S4).
 */
object PendingRestore {
    internal const val READY = "READY"

    /** Replaces the database, preferences and media with the staged backup. Returns whether it did. */
    fun applyIfPresent(directories: AppDirectories, driver: SQLiteDriver = platformSqliteDriver()): Boolean {
        val dir = directories.restoreStaging
        if (!File(dir, READY).exists()) {
            // A restore that never finished staging.
            if (dir.exists()) dir.deleteRecursively()
            return false
        }
        val staged = File(dir, BackupManager.DATABASE).listFiles().orEmpty()
        if (staged.none { it.name == MnemoDatabase.NAME }) {
            dir.deleteRecursively()
            return false
        }
        val databaseFile = directories.databaseFile(MnemoDatabase.NAME)
        val deviceId = readDeviceId(databaseFile, driver)
        DatabaseSnapshot.files(directories).forEach { it.delete() }
        val databaseDir = checkNotNull(databaseFile.parentFile).apply { mkdirs() }
        staged.forEach { it.copyTo(File(databaseDir, it.name), overwrite = true) }
        keepDeviceIdentity(databaseFile, deviceId, driver)

        val prefs = directories.dataStoreFile(DataStoreFiles.USER_PREFERENCES)
        File(dir, BackupManager.PREFERENCES).listFiles().orEmpty().firstOrNull()?.let { file ->
            prefs.parentFile?.mkdirs()
            file.copyTo(prefs, overwrite = true)
        }

        val media = directories.media
        media.deleteRecursively()
        val stagedMedia = File(dir, BackupManager.MEDIA)
        if (stagedMedia.exists() && !stagedMedia.renameTo(media)) stagedMedia.copyRecursively(media, overwrite = true)

        dir.deleteRecursively()
        return true
    }

    /** The device id in the database [file], or null if there is none (no database yet, or one from before schema v6). */
    private fun readDeviceId(file: File, driver: SQLiteDriver): String? {
        if (!file.exists()) return null
        return try {
            driver.open(file.path).use { connection ->
                connection.prepare("SELECT deviceId FROM sync_state WHERE id = 1").use { if (it.step()) it.getText(0) else null }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Gives the restored database [file] this device's [deviceId] and turns sync off. Without an id
     * the restored row is removed, so the next open makes a new one. A backup from before schema v6
     * has no sync table yet; the migration gives it a new identity.
     */
    private fun keepDeviceIdentity(file: File, deviceId: String?, driver: SQLiteDriver) {
        try {
            driver.open(file.path).use { connection ->
                val hasTable = connection.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'sync_state'")
                    .use { it.step() }
                if (!hasTable) return
                if (deviceId == null) {
                    connection.prepare("DELETE FROM sync_state").use { it.step() }
                    return
                }
                connection.prepare("UPDATE sync_state SET deviceId = ?, enabled = 0, applying = 0").use {
                    it.bindText(1, deviceId)
                    it.step()
                }
                connection.prepare("INSERT OR IGNORE INTO sync_state (id, deviceId, clock, enabled, applying) VALUES (1, ?, 0, 0, 0)")
                    .use {
                        it.bindText(1, deviceId)
                        it.step()
                    }
            }
        } catch (_: Exception) {
            // The files are in place; failing now would repeat the restore on every start. A file Room
            // can't open is reported when it is opened.
        }
    }
}
