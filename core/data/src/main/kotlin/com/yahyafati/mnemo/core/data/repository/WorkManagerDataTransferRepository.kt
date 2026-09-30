package com.yahyafati.mnemo.core.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.backup.BackupFormatException
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.backup.PendingRestore
import com.yahyafati.mnemo.core.data.work.BackupWorker
import com.yahyafati.mnemo.core.data.work.ExportWorker
import com.yahyafati.mnemo.core.data.work.ImportWorker
import com.yahyafati.mnemo.core.data.work.MediaCleanupWorker
import com.yahyafati.mnemo.core.data.work.WorkKeys
import com.yahyafati.mnemo.core.data.work.workState
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.zip.ZipException

internal class WorkManagerDataTransferRepository(
    private val context: Context,
    private val workManager: WorkManager,
    private val backups: BackupManager,
    private val settingsRepository: UserSettingsRepository,
) : DataTransferRepository {
    override val importState: Flow<TransferState<ImportSummary>> = workManager.workState(ImportWorker.UNIQUE_NAME, WorkKeys::summary)

    override val exportState: Flow<TransferState<Unit>> = workManager.workState(ExportWorker.UNIQUE_NAME) { }

    override val backupState: Flow<TransferState<Unit>> = workManager.workState(BackupWorker.UNIQUE_NAME) { }

    override fun startImport(uri: String) {
        // Several imports queue up rather than replacing each other.
        workManager.enqueueUniqueWork(ImportWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request<ImportWorker>(workDataOf(WorkKeys.URI to uri)))
    }

    override fun startExport(uri: String, format: ExportFormat, deckId: String?) {
        val input = workDataOf(WorkKeys.URI to uri, WorkKeys.FORMAT to format.name, WorkKeys.DECK_ID to deckId)
        workManager.enqueueUniqueWork(ExportWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request<ExportWorker>(input))
    }

    override fun startBackup(uri: String) {
        workManager.enqueueUniqueWork(BackupWorker.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request<BackupWorker>(workDataOf(WorkKeys.URI to uri)))
    }

    override fun clearFinished() {
        workManager.pruneWork()
    }

    override suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) {
        val previous = settingsRepository.settings.first().backup.folderUri
        if (folderUri != null && folderUri != previous) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(folderUri.toUri(), flags) }
            previous?.let { old ->
                runCatching { context.contentResolver.releasePersistableUriPermission(old.toUri(), flags) }
            }
        }
        settingsRepository.setAutoBackup(enabled, folderUri)
        scheduleAutoBackup(enabled && folderUri != null)
    }

    override suspend fun stageRestore(uri: String): MnemoResult<Instant> = try {
        val input = context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("Can't open $uri")
        MnemoResult.Success(input.use { backups.stage(it) }.createdAt)
    } catch (e: BackupFormatException) {
        MnemoResult.Failure(MnemoError.Parse(e.message.orEmpty(), e))
    } catch (e: ZipException) {
        MnemoResult.Failure(MnemoError.Parse("Not a zip file", e))
    } catch (e: IOException) {
        MnemoResult.Failure(MnemoError.Storage(e))
    } catch (e: SecurityException) {
        MnemoResult.Failure(MnemoError.Storage(e))
    }

    override fun restartToRestore() = PendingRestore.restartApp(context)

    override suspend fun scheduleMaintenance() {
        workManager.enqueueUniquePeriodicWork(
            MediaCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MediaCleanupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).setRequiresBatteryNotLow(true).build())
                .build(),
        )
        val backup = settingsRepository.settings.first().backup
        scheduleAutoBackup(backup.autoBackupEnabled && backup.folderUri != null)
    }

    private fun scheduleAutoBackup(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(BackupWorker.AUTO_UNIQUE_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            BackupWorker.AUTO_UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    private inline fun <reified W : androidx.work.ListenableWorker> request(input: Data) =
        OneTimeWorkRequestBuilder<W>().setInputData(input).build()
}
