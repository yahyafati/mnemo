package com.yahyafati.mnemo.core.data.android

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.model.MediaRef
import java.io.File

/**
 * [AppDirectories] on Android. Nothing here is cached: the paths are read from the [Context] each
 * time, so a test that swaps the files directory sees the new one.
 */
class AndroidAppDirectories(context: Context) : AppDirectories {
    private val context = context.applicationContext

    override val files: File get() = context.filesDir

    override val cache: File get() = context.cacheDir

    override val media: File get() = File(files, MediaRef.DIRECTORY)

    /** In `noBackupFilesDir`, which Android's Auto Backup never copies. */
    override val secrets: File get() = File(context.noBackupFilesDir, "secrets")

    override val restoreStaging: File get() = File(files, "restore-pending")

    override fun databaseFile(name: String): File = context.getDatabasePath(name)

    override fun dataStoreFile(name: String): File = context.preferencesDataStoreFile(name)
}
