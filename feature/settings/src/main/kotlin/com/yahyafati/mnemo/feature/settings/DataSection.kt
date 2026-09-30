package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.BackupSettings
import com.yahyafati.mnemo.core.model.ExportFormat
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.core.ui.files.rememberFilePicker
import com.yahyafati.mnemo.core.ui.files.rememberFileSaver
import com.yahyafati.mnemo.core.ui.files.rememberFolderPicker
import java.net.URLDecoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the Data section can ask for; the system file pickers are opened by the screen. */
internal class DataCallbacks(
    val onBackUp: (uri: String) -> Unit,
    val onReadBackup: (uri: String) -> Unit,
    val onConfirmRestore: () -> Unit,
    val onDismissRestore: () -> Unit,
    val onAutoBackup: (enabled: Boolean, folderUri: String?) -> Unit,
    val onExport: (uri: String, format: ExportFormat) -> Unit,
    val onDismissTransfers: () -> Unit,
)

/** Settings › Data: backups, restore and full exports (ROADMAP Phase 2). */
@Composable
internal fun DataSection(backup: BackupSettings, state: DataUiState, callbacks: DataCallbacks) {
    val backupPicker = rememberFileSaver(ZIP_MIME) { callbacks.onBackUp(it) }
    val restorePicker = rememberFilePicker(listOf(ZIP_MIME, "application/octet-stream")) { callbacks.onReadBackup(it) }
    val folderPicker = rememberFolderPicker { callbacks.onAutoBackup(true, it) }
    var exportFormat by rememberSaveable { mutableStateOf(ExportFormat.Apkg) }
    val exportPicker = rememberFileSaver("*/*") { callbacks.onExport(it, exportFormat) }

    Section(stringResource(R.string.feature_settings_data), MnemoIcons.Data) {
        ActionRow(
            icon = MnemoIcons.Backup,
            title = stringResource(R.string.feature_settings_backup_now),
            summary = stringResource(R.string.feature_settings_backup_summary),
            onClick = { backupPicker.launch(backupFileName(Instant.now())) },
        )
        TransferStatus(state.backupState, R.string.feature_settings_backing_up, R.string.feature_settings_backup_done, callbacks.onDismissTransfers)
        ActionRow(
            icon = MnemoIcons.Restore,
            title = stringResource(R.string.feature_settings_restore),
            summary = stringResource(R.string.feature_settings_restore_summary),
            onClick = { restorePicker.launch() },
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_settings_auto_backup), style = MaterialTheme.typography.titleSmall)
                val folder = backup.folderUri
                Hint(
                    if (folder == null) {
                        stringResource(R.string.feature_settings_auto_backup_summary)
                    } else {
                        stringResource(R.string.feature_settings_auto_backup_folder, folderName(folder))
                    },
                )
                backup.lastBackupAt?.let {
                    Hint(stringResource(R.string.feature_settings_last_backup, formatted(it)))
                }
            }
            Switch(
                checked = backup.autoBackupEnabled && backup.folderUri != null,
                onCheckedChange = { on ->
                    when {
                        !on -> callbacks.onAutoBackup(false, backup.folderUri)
                        backup.folderUri == null -> folderPicker.launch()
                        else -> callbacks.onAutoBackup(true, backup.folderUri)
                    }
                },
            )
        }
        if (backup.folderUri != null) {
            MnemoButton(
                text = stringResource(R.string.feature_settings_change_folder),
                onClick = { folderPicker.launch() },
                style = MnemoButtonStyle.Text,
                leadingIcon = MnemoIcons.Folder,
            )
        }

        ActionRow(
            icon = MnemoIcons.FileDownload,
            title = stringResource(R.string.feature_settings_export_apkg),
            summary = stringResource(R.string.feature_settings_export_apkg_summary),
            onClick = {
                exportFormat = ExportFormat.Apkg
                exportPicker.launch("mnemo-collection.apkg")
            },
        )
        ActionRow(
            icon = MnemoIcons.Json,
            title = stringResource(R.string.feature_settings_export_json),
            summary = stringResource(R.string.feature_settings_export_json_summary),
            onClick = {
                exportFormat = ExportFormat.Json
                exportPicker.launch("mnemo-collection.json")
            },
        )
        TransferStatus(state.exportState, R.string.feature_settings_exporting, R.string.feature_settings_export_done, callbacks.onDismissTransfers)
    }

    when (val restore = state.restore) {
        null -> Unit
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = callbacks.onDismissRestore,
            icon = { Icon(MnemoIcons.Restore, null) },
            title = { Text(stringResource(R.string.feature_settings_restore_title)) },
            text = { Text(stringResource(R.string.feature_settings_restore_message, formatted(restore.createdAt))) },
            confirmButton = {
                TextButton(onClick = callbacks.onConfirmRestore) {
                    Text(stringResource(R.string.feature_settings_restore_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = callbacks.onDismissRestore) { Text(stringResource(R.string.feature_settings_cancel)) } },
        )
        is RestoreStep.Failed -> AlertDialog(
            onDismissRequest = callbacks.onDismissRestore,
            title = { Text(stringResource(R.string.feature_settings_restore_failed)) },
            text = { Text(errorText(restore.error)) },
            confirmButton = { TextButton(onClick = callbacks.onDismissRestore) { Text(stringResource(R.string.feature_settings_ok)) } },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Hint(summary)
        }
        MnemoButton(text = stringResource(R.string.feature_settings_go), onClick = onClick, style = MnemoButtonStyle.Secondary)
    }
}

@Composable
private fun TransferStatus(state: TransferState<Unit>, running: Int, done: Int, onDismiss: () -> Unit) {
    when (state) {
        TransferState.Idle -> Unit
        is TransferState.Running -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
            Hint(stringResource(running))
            val progress = state.progress
            if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
        }
        is TransferState.Succeeded -> StatusLine(stringResource(done), isError = false, onDismiss)
        is TransferState.Failed -> StatusLine(errorText(state.error), isError = true, onDismiss)
    }
}

@Composable
private fun StatusLine(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .weight(1f)
                .padding(end = MnemoTheme.spacing.sm),
        )
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_ok)) }
    }
}

@Composable
private fun errorText(error: TransferError): String = stringResource(
    when (error) {
        TransferError.UnsupportedFile -> R.string.feature_settings_error_unsupported
        TransferError.Corrupt -> R.string.feature_settings_error_corrupt
        TransferError.Storage -> R.string.feature_settings_error_storage
        TransferError.Unknown -> R.string.feature_settings_error_unknown
    },
)

private const val ZIP_MIME = "application/zip"

private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

private fun backupFileName(now: Instant) = "mnemo-backup-${FILE_TIME.format(now.atZone(ZoneId.systemDefault()))}.zip"

private fun formatted(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))

/** A readable name for a SAF folder: "primary:Documents/Mnemo" → "Documents/Mnemo". */
private fun folderName(uri: String): String =
    URLDecoder.decode(uri.substringAfterLast('/'), "UTF-8").substringAfter(':').ifBlank { uri }
