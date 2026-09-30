package com.yahyafati.mnemo.core.data.repository

import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Imports, exports, backups and restores. The long ones run in the background and survive the
 * user leaving the screen; their state is observed here. URIs are content URIs from the system
 * file pickers.
 */
interface DataTransferRepository {
    val importState: Flow<TransferState<ImportSummary>>
    val exportState: Flow<TransferState<Unit>>
    val backupState: Flow<TransferState<Unit>>

    /** Imports the Anki package (`.apkg` / `.colpkg`) at [uri]. */
    fun startImport(uri: String)

    /** Exports [deckId] and its subdecks, or the whole collection when null, to [uri]. */
    fun startExport(uri: String, format: ExportFormat, deckId: String? = null)

    /** Writes a backup to [uri]. */
    fun startBackup(uri: String)

    /** Forgets finished transfers, so their results stop showing. */
    fun clearFinished()

    /**
     * Turns daily automatic backups into the folder [folderUri] (a SAF tree) on or off, keeping
     * access to the folder across restarts.
     */
    suspend fun setAutoBackup(enabled: Boolean, folderUri: String?)

    /**
     * Reads the backup at [uri] and stages it; [restartToRestore] then applies it. Returns when the
     * backup was made.
     */
    suspend fun stageRestore(uri: String): MnemoResult<Instant>

    /** Restarts the app so a staged restore replaces the collection. */
    fun restartToRestore()

    /** Schedules the periodic jobs (media cleanup, automatic backups). Safe to call on every start. */
    suspend fun scheduleMaintenance()
}
