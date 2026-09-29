package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme

/** Settings › About: the version, and the privacy policy, readable offline. */
@Composable
internal fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(R.string.feature_settings_about), MnemoIcons.Info) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.feature_settings_version), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(version, style = MnemoTheme.typography.metricLg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { showPolicy = true },
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.feature_settings_privacy), style = MaterialTheme.typography.titleSmall)
                Hint(stringResource(R.string.feature_settings_privacy_summary))
            }
            Icon(MnemoIcons.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showPolicy) PrivacyPolicyDialog(onDismiss = { showPolicy = false })
}

/** The privacy policy (docs/release/privacy-policy.md), in short. */
@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MnemoIcons.Privacy, contentDescription = null) },
        title = { Text(stringResource(R.string.feature_settings_privacy)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                listOf(
                    R.string.feature_settings_privacy_local_title to R.string.feature_settings_privacy_local,
                    R.string.feature_settings_privacy_telemetry_title to R.string.feature_settings_privacy_telemetry,
                    R.string.feature_settings_privacy_ai_title to R.string.feature_settings_privacy_ai,
                    R.string.feature_settings_privacy_keys_title to R.string.feature_settings_privacy_keys,
                    R.string.feature_settings_privacy_permissions_title to R.string.feature_settings_privacy_permissions,
                ).forEach { (title, body) ->
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_settings_ok)) } },
    )
}
