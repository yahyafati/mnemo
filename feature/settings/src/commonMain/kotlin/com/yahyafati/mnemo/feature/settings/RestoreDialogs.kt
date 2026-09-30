package com.yahyafati.mnemo.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_cancel
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_error_corrupt
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_error_storage
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_error_unknown
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_error_unsupported
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ok
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_restore_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_restore_failed
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_restore_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_restore_title
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The two dialogs of a restore: confirm replacing everything with the backup that was read, or say why
 * it couldn't be used. Nothing shows while [step] is null. Settings › Data and the app shell (File ›
 * Restore…, a backup dropped on the window) use the same ones.
 */
@Composable
fun RestoreDialogs(step: RestoreStep?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    when (step) {
        null -> Unit
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(MnemoIcons.Restore, null) },
            title = { Text(stringResource(Res.string.feature_settings_restore_title)) },
            text = { Text(stringResource(Res.string.feature_settings_restore_message, formatted(step.createdAt))) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(Res.string.feature_settings_restore_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_settings_cancel)) } },
        )
        is RestoreStep.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.feature_settings_restore_failed)) },
            text = { Text(errorText(step.error)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_settings_ok)) } },
        )
    }
}

@Composable
internal fun errorText(error: TransferError): String = stringResource(
    when (error) {
        TransferError.UnsupportedFile -> Res.string.feature_settings_error_unsupported
        TransferError.Corrupt -> Res.string.feature_settings_error_corrupt
        TransferError.Storage -> Res.string.feature_settings_error_storage
        TransferError.Unknown -> Res.string.feature_settings_error_unknown
    },
)

private val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")

/** The name suggested for a backup made at [now]: `mnemo-backup-2026-10-01-0930.zip`. */
fun backupFileName(now: Instant) = "mnemo-backup-${FILE_TIME.format(now.atZone(ZoneId.systemDefault()))}.zip"

internal fun formatted(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(instant.atZone(ZoneId.systemDefault()))
