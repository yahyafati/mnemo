package com.yahyafati.mnemo.feature.settings

import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.testing.repository.FakeDataTransferRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelDataTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transfers = FakeDataTransferRepository()
    private val viewModel by lazy { SettingsViewModel(FakeUserSettingsRepository(), transfers) }

    private fun runWithState(block: suspend () -> Unit) = runTest {
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.dataState.collect {} }
        block()
    }

    @Test
    fun restoreAsksToConfirmThenRestarts() = runWithState {
        val made = Instant.parse("2026-09-01T10:00:00Z")
        transfers.restoreResult = MnemoResult.Success(made)
        viewModel.readBackup("content://backup.zip")
        assertEquals(RestoreStep.Confirm(made), viewModel.dataState.value.restore)
        assertFalse(transfers.restarted)
        viewModel.confirmRestore()
        assertTrue(transfers.restarted)
    }

    @Test
    fun unreadableBackupsExplainWhy() = runWithState {
        transfers.restoreResult = MnemoResult.Failure(MnemoError.Parse("no manifest"))
        viewModel.readBackup("content://photo.jpg")
        assertEquals(RestoreStep.Failed(TransferError.UnsupportedFile), viewModel.dataState.value.restore)
        // Confirming a failure does nothing; dismissing clears it.
        viewModel.confirmRestore()
        assertFalse(transfers.restarted)
        viewModel.dismissRestore()
        assertEquals(null, viewModel.dataState.value.restore)
    }

    @Test
    fun backupsAndExports() = runWithState {
        viewModel.backUpTo("content://b.zip")
        viewModel.exportTo("content://c.json", ExportFormat.Json)
        viewModel.setAutoBackup(true, "content://tree/backups")
        assertEquals(listOf("content://b.zip"), transfers.backups)
        assertEquals(Triple("content://c.json", ExportFormat.Json, null), transfers.exports.single())
        assertEquals(true to "content://tree/backups", transfers.autoBackup)
        assertTrue(viewModel.dataState.value.backupState is TransferState.Running)

        transfers.backupState.value = TransferState.Succeeded(Unit)
        viewModel.dismissTransfers()
        assertEquals(TransferState.Idle, viewModel.dataState.value.backupState)
    }
}
