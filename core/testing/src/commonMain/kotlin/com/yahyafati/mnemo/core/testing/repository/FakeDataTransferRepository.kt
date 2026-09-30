package com.yahyafati.mnemo.core.testing.repository

import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.data.repository.DataTransferRepository
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** Records what was started; tests drive the states directly. */
class FakeDataTransferRepository : DataTransferRepository {
    override val importState = MutableStateFlow<TransferState<ImportSummary>>(TransferState.Idle)
    override val exportState = MutableStateFlow<TransferState<Unit>>(TransferState.Idle)
    override val backupState = MutableStateFlow<TransferState<Unit>>(TransferState.Idle)

    val imports = mutableListOf<String>()
    val exports = mutableListOf<Triple<String, ExportFormat, String?>>()
    val backups = mutableListOf<String>()
    var autoBackup: Pair<Boolean, String?>? = null
    var restoreResult: MnemoResult<Instant> = MnemoResult.Success(Instant.EPOCH)
    var restarted = false

    override fun startImport(uri: String) {
        imports += uri
        importState.value = TransferState.Running(null)
    }

    override fun startExport(uri: String, format: ExportFormat, deckId: String?) {
        exports += Triple(uri, format, deckId)
        exportState.value = TransferState.Running(null)
    }

    override fun startBackup(uri: String) {
        backups += uri
        backupState.value = TransferState.Running(null)
    }

    override fun clearFinished() {
        if (importState.value.finished) importState.value = TransferState.Idle
        if (exportState.value.finished) exportState.value = TransferState.Idle
        if (backupState.value.finished) backupState.value = TransferState.Idle
    }

    private val TransferState<*>.finished: Boolean
        get() = this is TransferState.Succeeded || this is TransferState.Failed

    override suspend fun setAutoBackup(enabled: Boolean, folderUri: String?) {
        autoBackup = enabled to folderUri
    }

    override suspend fun stageRestore(uri: String): MnemoResult<Instant> = restoreResult

    override fun restartToRestore() {
        restarted = true
    }

    override suspend fun scheduleMaintenance() = Unit
}
