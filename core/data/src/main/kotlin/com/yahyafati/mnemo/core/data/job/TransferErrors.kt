package com.yahyafati.mnemo.core.data.job

import com.yahyafati.mnemo.core.anki.AnkiFormatException
import com.yahyafati.mnemo.core.data.backup.BackupFormatException
import com.yahyafati.mnemo.core.model.TransferError
import java.io.IOException
import java.util.zip.ZipException

/**
 * What went wrong, for the user. Programmer errors are reported as unknown rather than crashing a
 * background job.
 */
internal fun Throwable.toTransferError(): TransferError = when (this) {
    is AnkiFormatException -> TransferError.UnsupportedFile
    is BackupFormatException -> TransferError.UnsupportedFile
    is ZipException -> TransferError.Corrupt
    is IOException, is SecurityException -> TransferError.Storage
    else -> TransferError.Unknown
}
