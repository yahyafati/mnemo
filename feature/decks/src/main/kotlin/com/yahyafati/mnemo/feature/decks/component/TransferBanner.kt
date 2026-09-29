package com.yahyafati.mnemo.feature.decks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ImportSummary
import com.yahyafati.mnemo.core.model.TransferError
import com.yahyafati.mnemo.core.model.TransferState
import com.yahyafati.mnemo.feature.decks.R

/**
 * Progress and results of an Anki import or deck export, shown above the library. Nothing shows
 * while idle. Finished results stay until dismissed, so they aren't missed after leaving the app.
 */
@Composable
internal fun TransferBanner(
    importState: TransferState<ImportSummary>,
    exportState: TransferState<Unit>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
        when (importState) {
            TransferState.Idle -> Unit
            is TransferState.Running -> Progress(stringResource(R.string.feature_decks_importing), importState.progress)
            is TransferState.Succeeded -> Result(
                icon = MnemoIcons.CheckCircle,
                title = stringResource(R.string.feature_decks_import_done),
                message = importSummary(importState.result),
                onDismiss = onDismiss,
            )
            is TransferState.Failed -> Result(
                icon = MnemoIcons.Close,
                title = stringResource(R.string.feature_decks_import_failed),
                message = errorMessage(importState.error),
                onDismiss = onDismiss,
                isError = true,
            )
        }
        when (exportState) {
            TransferState.Idle -> Unit
            is TransferState.Running -> Progress(stringResource(R.string.feature_decks_exporting), exportState.progress)
            is TransferState.Succeeded -> Result(MnemoIcons.CheckCircle, stringResource(R.string.feature_decks_export_done), null, onDismiss)
            is TransferState.Failed -> Result(
                MnemoIcons.Close, stringResource(R.string.feature_decks_export_failed), errorMessage(exportState.error), onDismiss, isError = true,
            )
        }
    }
}

@Composable
private fun Progress(title: String, progress: Float?) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Result(icon: ImageVector, title: String, message: String?, onDismiss: () -> Unit, isError: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (isError) colors.errorContainer else colors.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = MnemoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isError) colors.error else colors.secondary, modifier = Modifier.size(20.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = MnemoTheme.spacing.sm, vertical = MnemoTheme.spacing.sm),
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (message != null) Text(message, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(MnemoIcons.Close, stringResource(R.string.feature_decks_dismiss)) }
        }
    }
}

@Composable
private fun importSummary(summary: ImportSummary): String {
    val parts = mutableListOf(
        pluralStringResource(R.plurals.feature_decks_import_notes, summary.notes, summary.notes),
        pluralStringResource(R.plurals.feature_decks_import_cards, summary.cards, summary.cards),
    )
    if (summary.reviews > 0) parts += pluralStringResource(R.plurals.feature_decks_import_reviews, summary.reviews, summary.reviews)
    if (summary.media > 0) parts += pluralStringResource(R.plurals.feature_decks_import_media, summary.media, summary.media)
    if (summary.duplicateNotes > 0) {
        parts += pluralStringResource(R.plurals.feature_decks_import_duplicates, summary.duplicateNotes, summary.duplicateNotes)
    }
    if (summary.skippedCards > 0) parts += pluralStringResource(R.plurals.feature_decks_import_skipped, summary.skippedCards, summary.skippedCards)
    return parts.joinToString(" · ")
}

@Composable
internal fun errorMessage(error: TransferError): String = stringResource(
    when (error) {
        TransferError.UnsupportedFile -> R.string.feature_decks_error_unsupported
        TransferError.Corrupt -> R.string.feature_decks_error_corrupt
        TransferError.Storage -> R.string.feature_decks_error_storage
        TransferError.Unknown -> R.string.feature_decks_error_unknown
    },
)

@Preview(showBackground = true)
@Composable
private fun TransferBannerPreview() {
    MnemoTheme {
        TransferBanner(
            importState = TransferState.Succeeded(ImportSummary(2, 120, 240, 900, 12, 3, 4)),
            exportState = TransferState.Running(0.4f),
            onDismiss = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
