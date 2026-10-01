package com.yahyafati.mnemo.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.designsystem.component.MnemoLogo
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.ProjectLinks
import com.yahyafati.mnemo.core.ui.platform.LocalAppVersion
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_about
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_app_name
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_license
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_license_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_licenses
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_licenses_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ok
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_ai
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_ai_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_keys
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_keys_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_local
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_local_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_online
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_permissions
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_permissions_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_telemetry
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_privacy_telemetry_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_releases
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_releases_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_source
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_version
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › About: the version, the license, where the source is (the GPL asks for that), the
 * releases page (a link, no network call from the app), the open-source licenses list, and the
 * privacy policy, readable offline.
 */
@Composable
internal fun AboutSection(onOpenLicenses: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    // No browser installed: nothing to open.
    val openLink: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val version = LocalAppVersion.current
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    Section(stringResource(Res.string.feature_settings_about), MnemoIcons.Info) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = MnemoTheme.spacing.xs)) {
            MnemoLogo(size = 40.dp)
            Text(
                stringResource(Res.string.feature_settings_app_name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = MnemoTheme.spacing.md),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.feature_settings_version), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(version, style = MnemoTheme.typography.metricLg, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AboutLink(
            title = stringResource(Res.string.feature_settings_license),
            summary = stringResource(Res.string.feature_settings_license_summary, ProjectLinks.LICENSE_NAME),
            icon = MnemoIcons.OpenInNew,
            onClick = { openLink(ProjectLinks.LICENSE_URL) },
        )
        AboutLink(
            title = stringResource(Res.string.feature_settings_source),
            summary = ProjectLinks.SOURCE.removePrefix("https://"),
            icon = MnemoIcons.OpenInNew,
            onClick = { openLink(ProjectLinks.SOURCE) },
        )
        AboutLink(
            title = stringResource(Res.string.feature_settings_releases),
            summary = stringResource(Res.string.feature_settings_releases_summary),
            icon = MnemoIcons.OpenInNew,
            onClick = { openLink(ProjectLinks.RELEASES) },
        )
        AboutLink(
            title = stringResource(Res.string.feature_settings_licenses),
            summary = stringResource(Res.string.feature_settings_licenses_summary),
            icon = MnemoIcons.ArrowForward,
            onClick = onOpenLicenses,
        )
        AboutLink(
            title = stringResource(Res.string.feature_settings_privacy),
            summary = stringResource(Res.string.feature_settings_privacy_summary),
            icon = MnemoIcons.ArrowForward,
            onClick = { showPolicy = true },
        )
    }
    if (showPolicy) PrivacyPolicyDialog(onDismiss = { showPolicy = false }, onReadOnline = { openLink(ProjectLinks.PRIVACY_POLICY) })
}

@Composable
private fun AboutLink(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Hint(summary)
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The privacy policy (docs/release/privacy-policy.md), in short. */
@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit, onReadOnline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(MnemoIcons.Privacy, contentDescription = null) },
        title = { Text(stringResource(Res.string.feature_settings_privacy)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                listOf(
                    Res.string.feature_settings_privacy_local_title to Res.string.feature_settings_privacy_local,
                    Res.string.feature_settings_privacy_telemetry_title to Res.string.feature_settings_privacy_telemetry,
                    Res.string.feature_settings_privacy_ai_title to Res.string.feature_settings_privacy_ai,
                    Res.string.feature_settings_privacy_keys_title to Res.string.feature_settings_privacy_keys,
                    Res.string.feature_settings_privacy_permissions_title to Res.string.feature_settings_privacy_permissions,
                ).forEach { (title, body) ->
                    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_settings_ok)) } },
        dismissButton = { TextButton(onClick = onReadOnline) { Text(stringResource(Res.string.feature_settings_privacy_online)) } },
    )
}
