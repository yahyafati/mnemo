package com.yahyafati.mnemo.core.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yahyafati.mnemo.core.data.R
import com.yahyafati.mnemo.core.data.job.BackupJob
import com.yahyafati.mnemo.core.data.job.ExportJob
import com.yahyafati.mnemo.core.data.job.ImportJob
import com.yahyafati.mnemo.core.data.job.toTransferError
import com.yahyafati.mnemo.core.data.repository.MediaRepository
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferError
import kotlinx.coroutines.CancellationException
import java.io.IOException

// Background work for the data transfers (ARCHITECTURE §5.4), on Android. The workers only adapt
// the jobs in `job/` to WorkManager: input and output data, the foreground notification, retries.
// Results and errors go back through the output data, which `WorkManagerDataTransferRepository`
// turns into `TransferState`s.

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

internal class ImportWorker(
    context: Context,
    params: WorkerParameters,
    private val job: ImportJob,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI) ?: return failure(TransferError.UnsupportedFile)
        tryForeground(NOTIFICATION_ID, R.string.core_data_importing, null)
        return try {
            val summary = job.run(uri, id.toString()) { progress ->
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
        }
    }

    companion object {
        const val UNIQUE_NAME = "anki-import"
        private const val NOTIFICATION_ID = 1001
    }
}

internal class ExportWorker(
    context: Context,
    params: WorkerParameters,
    private val job: ExportJob,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI) ?: return failure(TransferError.Storage)
        val format = ExportFormat.entries.firstOrNull { it.name == inputData.getString(WorkKeys.FORMAT) } ?: ExportFormat.Apkg
        tryForeground(NOTIFICATION_ID, R.string.core_data_exporting, null)
        return try {
            job.run(uri, format, inputData.getString(WorkKeys.DECK_ID), id.toString()) { setProgress(workDataOf(WorkKeys.PROGRESS to it)) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e.toTransferError())
        }
    }

    companion object {
        const val UNIQUE_NAME = "export"
        private const val NOTIFICATION_ID = 1002
    }
}

/** A manual backup to [WorkKeys.URI], or, without a URI, an automatic one into the backup folder. */
internal class BackupWorker(
    context: Context,
    params: WorkerParameters,
    private val job: BackupJob,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(WorkKeys.URI)
        return try {
            if (uri != null) tryForeground(NOTIFICATION_ID, R.string.core_data_backing_up, null)
            job.run(uri, id.toString()) { setProgress(workDataOf(WorkKeys.PROGRESS to it)) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An automatic backup tries again later; a manual one reports the error.
            if (uri == null && runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure(e.toTransferError())
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
internal class MediaCleanupWorker(
    context: Context,
    params: WorkerParameters,
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
