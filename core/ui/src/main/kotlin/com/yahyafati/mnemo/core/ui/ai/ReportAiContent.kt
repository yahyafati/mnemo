package com.yahyafati.mnemo.core.ui.ai

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.R

/**
 * A "Report" button for one piece of AI output ([compact]: just the flag icon). It asks first, then opens a prefilled
 * GitHub issue in the browser (see [AiReportIssue]); nothing is sent by Mnemo itself.
 */
@Composable
fun ReportAiButton(report: AiReport, modifier: Modifier = Modifier, compact: Boolean = false) {
    var asking by rememberSaveable { mutableStateOf(false) }
    if (compact) {
        IconButton(onClick = { asking = true }, modifier = modifier) {
            Icon(MnemoIcons.Flag, contentDescription = stringResource(R.string.core_ui_ai_report))
        }
    } else {
        TextButton(onClick = { asking = true }, modifier = modifier) {
            Icon(MnemoIcons.Flag, contentDescription = null)
            Text(stringResource(R.string.core_ui_ai_report), modifier = Modifier.padding(start = MnemoTheme.spacing.xs))
        }
    }
    if (asking) ReportAiDialog(report, onDismiss = { asking = false })
}

/** The confirmation before the draft opens: what it is, what is in it, and that it is public. */
@Composable
fun ReportAiDialog(report: AiReport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MnemoIcons.Flag, contentDescription = null) },
        title = { Text(stringResource(R.string.core_ui_ai_report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                Text(stringResource(R.string.core_ui_ai_report_explain), style = MaterialTheme.typography.bodyMedium)
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(
                        text = AiReportIssue.sentContent(report),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(MnemoTheme.spacing.sm),
                    )
                }
                Text(
                    stringResource(R.string.core_ui_ai_report_public),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AiReportIssue.url(report, appVersion)))
                    try {
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        // No browser installed: nothing to open, and nothing was sent.
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.core_ui_ai_report_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_ui_ai_disclosure_cancel)) } },
    )
}

@Preview
@Composable
private fun ReportAiDialogPreview() {
    MnemoTheme {
        ReportAiDialog(AiReport(AiReportKind.SmartExtractCard, "Front: What makes ATP?\nBack: Mitochondria", "gpt-4o"), onDismiss = {})
    }
}
