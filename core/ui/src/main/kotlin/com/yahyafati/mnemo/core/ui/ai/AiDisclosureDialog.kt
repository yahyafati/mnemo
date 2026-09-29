package com.yahyafati.mnemo.core.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.R

/**
 * The notice before the first request to a provider (PROJECT_OVERVIEW §5.4): what leaves the
 * device, and exactly where it goes. [whatIsSent] names this request's content.
 */
@Composable
fun AiDisclosureDialog(
    providerName: String,
    host: String,
    whatIsSent: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MnemoIcons.Privacy, contentDescription = null) },
        title = { Text(stringResource(R.string.core_ui_ai_disclosure_title, providerName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                Text(stringResource(R.string.core_ui_ai_disclosure_sent, whatIsSent))
                Text(host, style = MnemoTheme.typography.metricSm, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.core_ui_ai_disclosure_policy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.core_ui_ai_disclosure_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_ai_disclosure_cancel)) } },
    )
}

@Preview
@Composable
private fun AiDisclosureDialogPreview() {
    MnemoTheme {
        AiDisclosureDialog("OpenAI", "api.openai.com", "your API key and a short test message", onAccept = {}, onDismiss = {})
    }
}
