package com.yahyafati.mnemo.core.data.backup

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.preferencesDataStoreFile
import com.yahyafati.mnemo.core.database.DatabaseSnapshot
import com.yahyafati.mnemo.core.database.MnemoDatabase
import com.yahyafati.mnemo.core.datastore.di.DataStoreFiles
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File

/**
 * Applies a restore staged by [BackupManager.stage]. It must run before anything opens the
 * database or the preferences, so `MnemoApplication` calls [applyIfPresent] first thing, and the
 * Settings screen restarts the app ([restartApp]) once a restore is staged.
 */
object PendingRestore {
    internal const val READY = "READY"
    private const val DIRECTORY = "restore-pending"

    fun directory(context: Context) = File(context.filesDir, DIRECTORY)

    /** Replaces the database, preferences and media with the staged backup. Returns whether it did. */
    fun applyIfPresent(context: Context): Boolean {
        val dir = directory(context)
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
        DatabaseSnapshot.files(context).forEach { it.delete() }
        val databaseDir = checkNotNull(context.getDatabasePath(MnemoDatabase.NAME).parentFile).apply { mkdirs() }
        staged.forEach { it.copyTo(File(databaseDir, it.name), overwrite = true) }

        val prefs = context.preferencesDataStoreFile(DataStoreFiles.USER_PREFERENCES)
        File(dir, BackupManager.PREFERENCES).listFiles().orEmpty().firstOrNull()?.let { file ->
            prefs.parentFile?.mkdirs()
            file.copyTo(prefs, overwrite = true)
        }

        val media = File(context.filesDir, MediaRef.DIRECTORY)
        media.deleteRecursively()
        val stagedMedia = File(dir, BackupManager.MEDIA)
        if (stagedMedia.exists() && !stagedMedia.renameTo(media)) stagedMedia.copyRecursively(media, overwrite = true)

        dir.deleteRecursively()
        return true
    }

    /** Restarts the app in a fresh process, so the staged restore is applied. */
    fun restartApp(context: Context) {
        val launch = checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}
