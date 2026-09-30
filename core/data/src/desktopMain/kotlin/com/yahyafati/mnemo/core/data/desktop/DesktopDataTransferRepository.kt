package com.yahyafati.mnemo.core.data.desktop

import com.yahyafati.mnemo.core.common.platform.AppDirectories
import com.yahyafati.mnemo.core.common.platform.DocumentAccess
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.backup.BackupFormatException
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.job.BackupJob
import com.yahyafati.mnemo.core.data.job.ExportJob
import com.yahyafati.mnemo.core.data.job.ImportJob
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipException

/**
 * [DataTransferRepository] on coroutines: the jobs run in [scope] while the app is open, one at a
 * time per kind, and their state is observed here. The periodic work WorkManager does on Android
 * (media cleanup every week, an automatic backup every day) is checked when the app starts and
 * then every hour, so a computer that is left on still gets it.
 */
internal class DesktopDataTransferRepository(
    private val scope: CoroutineScope,
    private val documents: DocumentAccess,
    private val directories: AppDirectories,
    private val backups: BackupManager,
    private val importJob: ImportJob,
    private val exportJob: ExportJob,
    private val backupJob: BackupJob,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
    private val restarter: AppRestarter,
) : DataTransferRepository {
    private val imports = TransferQueue<ImportSummary>(scope)
    private val exports = TransferQueue<Unit>(scope)
    private val backupQueue = TransferQueue<Unit>(scope)
    private var maintenance: Job? = null

    override val importState: Flow<TransferState<ImportSummary>> = imports.state

    override val exportState: Flow<TransferState<Unit>> = exports.state

    override val backupState: Flow<TransferState<Unit>> = backupQueue.state

    override fun startImport(uri: String) {
        imports.enqueue { report -> importJob.run(uri, newId()) { report(it) } }
    }

    override fun startExport(uri: String, format: ExportFormat, deckId: String?) {
        exports.enqueue { report -> exportJob.run(uri, format, deckId, newId()) { report(it) } }
    }

    override fun startBackup(uri: String) {
        backupQueue.enqueue { report -> backupJob.run(uri, newId()) { report(it) }; Unit }
    }

    override fun clearFinished() {
        imports.clearFinished()
        exports.clearFinished()
        backupQueue.clearFinished()
    }

    override suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) {
        val previous = settingsRepository.settings.first().backup.folderUri
        if (folderUri != null && folderUri != previous) {
            documents.keepAccess(folderUri)
            previous?.let(documents::releaseAccess)
        }
        settingsRepository.setAutoBackup(enabled, folderUri)
        if (enabled && folderUri != null) scope.launch { runAutomaticBackupIfDue() }
    }

    override suspend fun stageRestore(uri: String): MnemoResult<Instant> = try {
        MnemoResult.Success(documents.openInput(uri).use { backups.stage(it) }.createdAt)
    } catch (e: BackupFormatException) {
        MnemoResult.Failure(MnemoError.Parse(e.message.orEmpty(), e))
    } catch (e: ZipException) {
        MnemoResult.Failure(MnemoError.Parse("Not a zip file", e))
    } catch (e: IOException) {
        MnemoResult.Failure(MnemoError.Storage(e))
    }

    override fun restartToRestore() = restarter.restart()

    override suspend fun scheduleMaintenance() {
        if (maintenance?.isActive == true) return
        maintenance = scope.launch {
            while (true) {
                runMaintenanceIfDue()
                delay(CHECK_INTERVAL.toMillis())
            }
        }
    }

    /** Media cleanup weekly (the time of the last one is the stamp file's), a backup daily. */
    private suspend fun runMaintenanceIfDue() {
        val stamp = File(directories.files, CLEANUP_STAMP)
        val now = clock.now()
        if (!stamp.exists() || now.toEpochMilli() - stamp.lastModified() >= CLEANUP_EVERY.toMillis()) {
            try {
                mediaRepository.collectGarbage()
                stamp.parentFile?.mkdirs()
                stamp.writeText(now.toString())
                stamp.setLastModified(now.toEpochMilli())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Tried again in an hour.
            }
        }
        runAutomaticBackupIfDue()
    }

    private suspend fun runAutomaticBackupIfDue() {
        val backup = settingsRepository.settings.first().backup
        if (!backup.autoBackupEnabled || backup.folderUri == null) return
        val last = backup.lastBackupAt
        if (last != null && Duration.between(last, clock.now()) < BACKUP_EVERY) return
        try {
            backupJob.run(null, newId())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The folder may be on a drive that isn't connected: tried again in an hour.
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        val CHECK_INTERVAL: Duration = Duration.ofHours(1)
        val CLEANUP_EVERY: Duration = Duration.ofDays(7)
        val BACKUP_EVERY: Duration = Duration.ofDays(1)
        const val CLEANUP_STAMP = "media-cleanup.stamp"
    }
}
