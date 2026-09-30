package com.yahyafati.mnemo.core.data.backup

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import java.io.File

/**
 * Applies a restore staged by [BackupManager.stage]. It must run before anything opens the
 * database or the preferences, so the app calls [applyIfPresent] first thing, and the Settings
 * screen restarts the app once a restore is staged.
 */
object PendingRestore {
    internal const val READY = "READY"

    /** Replaces the database, preferences and media with the staged backup. Returns whether it did. */
    fun applyIfPresent(directories: AppDirectories): Boolean {
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
        DatabaseSnapshot.files(directories).forEach { it.delete() }
        val databaseDir = checkNotNull(directories.databaseFile(MnemoDatabase.NAME).parentFile).apply { mkdirs() }
        staged.forEach { it.copyTo(File(databaseDir, it.name), overwrite = true) }

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
}
