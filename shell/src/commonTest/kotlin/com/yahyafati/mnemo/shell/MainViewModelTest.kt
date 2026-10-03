package com.yahyafati.mnemo.shell

import com.yahyafati.mnemo.core.common.result.MnemoError
import com.yahyafati.mnemo.core.common.result.MnemoResult
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.testing.MainDispatcherRule
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncResult
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.testing.repository.FakeDataTransferRepository
import com.yahyafati.mnemo.core.testing.repository.FakeDeckRepository
import com.yahyafati.mnemo.core.testing.repository.FakeReminderRepository
import com.yahyafati.mnemo.core.testing.repository.FakeSyncRepository
import com.yahyafati.mnemo.core.testing.repository.FakeUserSettingsRepository
import com.yahyafati.mnemo.feature.settings.RestoreStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the desktop's menu bar and drop target ask of the shell (desktop ROADMAP D7). */
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val transfers = FakeDataTransferRepository()
    private val settings = FakeUserSettingsRepository()
    private val sync = FakeSyncRepository()
    private val viewModel by lazy {
        MainViewModel(settings, FakeDeckRepository(), FakeReminderRepository(settings), transfers, sync)
    }

    @Test
    fun filesAreOpenedByWhatTheyAre() = runTest {
        // An Anki package is imported (and the shell shows the Decks tab).
        assertTrue(viewModel.openFile("/home/me/Biology.apkg"))
        assertTrue(viewModel.openFile("/home/me/everything.COLPKG"))
        assertEquals(listOf("/home/me/Biology.apkg", "/home/me/everything.COLPKG"), transfers.imports)

        // A zip is a backup to restore: it asks first, and imports nothing.
        transfers.restoreResult = MnemoResult.Success(Instant.parse("2026-09-01T10:00:00Z"))
        assertFalse(viewModel.openFile("/home/me/mnemo-backup.zip"))
        assertEquals(RestoreStep.Confirm(Instant.parse("2026-09-01T10:00:00Z")), viewModel.restore.value)
        assertEquals(2, transfers.imports.size)

        // Anything else is said to be unsupported.
        assertFalse(viewModel.openFile("/home/me/photo.png"))
        assertEquals(ShellMessage.UnsupportedFile, viewModel.messages.first())
    }

    @Test
    fun syncNowRunsARoundAndSaysSo() = runTest {
        sync.statusState.value = SyncStatus.Idle(SyncBackend.Folder("/sync"), null)
        viewModel.syncNow()
        assertEquals(ShellMessage.SyncDone, viewModel.messages.first())
        assertEquals(1, sync.syncs)
        assertNull(viewModel.destination.value)

        sync.syncResult = SyncResult.Failed(SyncProblem.Offline)
        viewModel.syncNow()
        assertEquals(ShellMessage.SyncFailed, viewModel.messages.first())
    }

    @Test
    fun syncNowWithSyncOffOpensTheScreenThatSetsItUp() = runTest {
        viewModel.syncNow()
        assertEquals(AppDestination.SyncSettings, viewModel.destination.value)
    }

    @Test
    fun syncNowDuringARoundSaysNothing() = runTest {
        sync.syncResult = SyncResult.Busy
        viewModel.syncNow()
        assertNull(viewModel.destination.value)
        assertEquals(1, sync.syncs)
    }

    @Test
    fun restoringIsConfirmedBeforeItRestarts() = runTest {
        transfers.restoreResult = MnemoResult.Success(Instant.EPOCH)
        viewModel.stageRestore("/home/me/mnemo-backup.zip")
        assertFalse(transfers.restarted)
        viewModel.confirmRestore()
        assertTrue(transfers.restarted)

        // A file that is no backup says so, and confirming it does nothing.
        transfers.restarted = false
        transfers.restoreResult = MnemoResult.Failure(MnemoError.Parse("no manifest"))
        viewModel.stageRestore("/home/me/other.zip")
        assertEquals(RestoreStep.Failed(TransferError.UnsupportedFile), viewModel.restore.value)
        viewModel.confirmRestore()
        assertFalse(transfers.restarted)
        viewModel.dismissRestore()
        assertNull(viewModel.restore.value)
    }

    @Test
    fun aBackupOrExportStartedFromTheMenuSaysHowItEnded() = runTest {
        viewModel.backUpTo("/home/me/backup.zip")
        assertEquals(listOf("/home/me/backup.zip"), transfers.backups)
        transfers.backupState.value = TransferState.Succeeded(Unit)
        assertEquals(ShellMessage.BackupDone, viewModel.messages.first())

        viewModel.exportTo("/home/me/cards.json", ExportFormat.Json)
        assertEquals(ExportFormat.Json, transfers.exports.single().second)
        transfers.exportState.value = TransferState.Failed(TransferError.Storage)
        assertEquals(ShellMessage.TransferFailed, viewModel.messages.first())
    }

    @Test
    fun droppedFilesBecomeCommands() {
        assertEquals(FileKind.AnkiPackage, FileKind.of("deck.APKG"))
        assertEquals(FileKind.Backup, FileKind.of("/tmp/mnemo-backup-2026-10-01.zip"))
        assertEquals(FileKind.Epub, FileKind.of("/Books/Dune.EPUB"))
        assertEquals(FileKind.Unknown, FileKind.of("/tmp/notes"))

        // Every package is imported; with none, the first backup is offered.
        assertEquals(
            listOf(AppCommand.OpenFile("a.apkg"), AppCommand.OpenFile("c.colpkg")),
            droppedFileCommands(listOf("a.apkg", "b.zip", "c.colpkg", "d.txt")),
        )
        assertEquals(listOf(AppCommand.OpenFile("b.zip")), droppedFileCommands(listOf("d.txt", "b.zip", "e.zip")))
        // A book comes before a backup, and after the packages.
        assertEquals(listOf(AppCommand.OpenFile("a.epub")), droppedFileCommands(listOf("b.zip", "a.epub", "c.epub")))
        assertEquals(listOf(AppCommand.OpenFile("a.apkg")), droppedFileCommands(listOf("a.epub", "a.apkg")))
        assertTrue(droppedFileCommands(listOf("d.txt")).isEmpty())
    }
}
