package com.yahyafati.mnemo.core.data.job

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File

/** A manual backup to a file the user picked, or an automatic one into the backup folder. */
internal class BackupJob(
    private val backups: BackupManager,
    private val settingsRepository: UserSettingsRepository,
    private val documents: DocumentAccess,
    private val directories: AppDirectories,
    private val clock: Clock,
) {
    /**
     * Writes a backup to [uri], or, when it is null, an automatic one into the backup folder if
     * automatic backups are on. Returns whether a backup was written.
     */
    suspend fun run(uri: String?, id: String, onProgress: suspend (Float) -> Unit = {}): Boolean {
        val work = File(directories.cache, "backup-$id")
        try {
            if (uri != null) {
                documents.openOutput(uri).use { out -> backups.write(out, work, onProgress) }
            } else {
                val settings = settingsRepository.settings.first().backup
                val folder = settings.folderUri
                if (!settings.autoBackupEnabled || folder == null) return false
                backups.writeToFolder(folder, settings.keepCount, work)
            }
            settingsRepository.setLastBackupAt(clock.now())
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (uri != null) documents.delete(uri)
            throw e
        } finally {
            work.deleteRecursively()
        }
    }
}
