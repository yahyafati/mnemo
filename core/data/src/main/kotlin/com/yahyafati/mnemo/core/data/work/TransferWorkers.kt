package com.yahyafati.mnemo.core.data.work

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yahyafati.mnemo.core.anki.AnkiFormatException
import com.yahyafati.mnemo.core.common.time.Clock
import com.yahyafati.mnemo.core.data.R
import com.yahyafati.mnemo.core.data.backup.BackupManager
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.data.repository.UserSettingsRepository
import com.yahyafati.mnemo.core.data.transfer.AnkiExporter
import com.yahyafati.mnemo.core.data.transfer.AnkiImporter
import com.yahyafati.mnemo.core.data.transfer.JsonExporter
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.util.zip.ZipException

// Background work for the data transfers (ARCHITECTURE §5.4). Workers read and write the user's
// files through the content URIs the system pickers returned; results and errors go back through
// the output data, which `WorkManagerDataTransferRepository` turns into `TransferState`s.

internal object WorkKeys {
    const val URI = "uri"
    const val FORMAT = "format"
    const val DECK_ID = "deckId"
    const val PROGRESS = "progress"
    const val ERROR = "error"
    const val DECKS = "decks"
    const val NOTES = "notes"
    const val CARDS = "cards"
    const val REVIEWS = "reviews"
    const val MEDIA = "media"
    const val DUPLICATES = "duplicates"
    const val SKIPPED = "skipped"

    fun summary(data: Data) = ImportSummary(
        decks = data.getInt(DECKS, 0),
        notes = data.getInt(NOTES, 0),
        cards = data.getInt(CARDS, 0),
        reviews = data.getInt(REVIEWS, 0),
        media = data.getInt(MEDIA, 0),
        duplicateNotes = data.getInt(DUPLICATES, 0),
        skippedCards = data.getInt(SKIPPED, 0),
    )

    fun error(data: Data): TransferError =
        TransferError.entries.firstOrNull { it.name == data.getString(ERROR) } ?: TransferError.Unknown
}

/** A failure result carrying [error]. */
private fun failure(error: TransferError) = androidx.work.ListenableWorker.Result.failure(workDataOf(WorkKeys.ERROR to error.name))

/** What went wrong, for the user. Programmer errors are reported as unknown rather than crashing a background job. */
private fun Throwable.toTransferError(): TransferError = when (this) {
    is AnkiFormatException -> TransferError.UnsupportedFile
    is com.yahyafati.mnemo.core.data.backup.BackupFormatException -> TransferError.UnsupportedFile
    is ZipException -> TransferError.Corrupt
    is IOException, is SecurityException -> TransferError.Storage
    else -> TransferError.Unknown
}

@HiltWorker
internal class ImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: AnkiImporter,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI)?.let(Uri::parse) ?: return failure(TransferError.UnsupportedFile)
        tryForeground(NOTIFICATION_ID, R.string.core_data_importing, null)
        val work = File(applicationContext.cacheDir, "import-$id")
        return try {
            work.mkdirs()
            // A local copy: the package is read with random access, and the URI grant is short-lived.
            val copy = File(work, "package.apkg")
            val input = applicationContext.contentResolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
            input.use { source -> copy.outputStream().use { source.copyTo(it) } }
            val summary = importer.import(copy, File(work, "unpacked")) { progress ->
                setProgress(workDataOf(WorkKeys.PROGRESS to progress))
                tryForeground(NOTIFICATION_ID, R.string.core_data_importing, progress)
            }
            Result.success(
                workDataOf(
                    WorkKeys.DECKS to summary.decks,
                    WorkKeys.NOTES to summary.notes,
                    WorkKeys.CARDS to summary.cards,
                    WorkKeys.REVIEWS to summary.reviews,
                    WorkKeys.MEDIA to summary.media,
                    WorkKeys.DUPLICATES to summary.duplicateNotes,
                    WorkKeys.SKIPPED to summary.skippedCards,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e.toTransferError())
        } finally {
            work.deleteRecursively()
        }
    }

    companion object {
        const val UNIQUE_NAME = "anki-import"
        private const val NOTIFICATION_ID = 1001
    }
}

@HiltWorker
internal class ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ankiExporter: AnkiExporter,
    private val jsonExporter: JsonExporter,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI)?.let(Uri::parse) ?: return failure(TransferError.Storage)
        val format = ExportFormat.entries.firstOrNull { it.name == inputData.getString(WorkKeys.FORMAT) } ?: ExportFormat.Apkg
        tryForeground(NOTIFICATION_ID, R.string.core_data_exporting, null)
        val work = File(applicationContext.cacheDir, "export-$id")
        return try {
            val output = applicationContext.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
            output.use { out ->
                val onProgress: suspend (Float) -> Unit = { setProgress(workDataOf(WorkKeys.PROGRESS to it)) }
                when (format) {
                    ExportFormat.Apkg -> ankiExporter.export(inputData.getString(WorkKeys.DECK_ID), out, work, onProgress)
                    ExportFormat.Json -> jsonExporter.export(out, onProgress)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Don't leave a half-written file behind.
            runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, uri) }
            failure(e.toTransferError())
        } finally {
            work.deleteRecursively()
        }
    }

    companion object {
        const val UNIQUE_NAME = "export"
        private const val NOTIFICATION_ID = 1002
    }
}

/** A manual backup to [WorkKeys.URI], or, without a URI, an automatic one into the backup folder. */
@HiltWorker
internal class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
    private val settingsRepository: UserSettingsRepository,
    private val clock: Clock,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI)?.let(Uri::parse)
        val work = File(applicationContext.cacheDir, "backup-$id")
        return try {
            if (uri != null) {
                tryForeground(NOTIFICATION_ID, R.string.core_data_backing_up, null)
                val output = applicationContext.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Can't write $uri")
                output.use { out -> backups.write(out, work) { setProgress(workDataOf(WorkKeys.PROGRESS to it)) } }
            } else {
                val settings = settingsRepository.settings.first().backup
                val folder = settings.folderUri
                if (!settings.autoBackupEnabled || folder == null) return Result.success()
                backups.writeToFolder(folder, settings.keepCount, work)
            }
            settingsRepository.setLastBackupAt(clock.now())
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (uri != null) runCatching { DocumentsContract.deleteDocument(applicationContext.contentResolver, uri) }
            // An automatic backup tries again later; a manual one reports the error.
            if (uri == null && runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure(e.toTransferError())
        } finally {
            work.deleteRecursively()
        }
    }

    companion object {
        const val UNIQUE_NAME = "backup"
        const val AUTO_UNIQUE_NAME = "auto-backup"
        private const val NOTIFICATION_ID = 1003
        private const val MAX_ATTEMPTS = 3
    }
}

/** Deletes media no note uses any more (ARCHITECTURE §6). */
@HiltWorker
internal class MediaCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mediaRepository: MediaRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        mediaRepository.collectGarbage()
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "media-cleanup"
    }
}
