package com.yahyafati.mnemo.feature.decks.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.feature.decks.resources.Res
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_auth
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_gone
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_open
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_passphrase
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_quota
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_rejoin
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_restored
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_stalled
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_title
import com.yahyafati.mnemo.feature.decks.resources.feature_decks_sync_update
import org.jetbrains.compose.resources.stringResource

/**
 * Says that sync needs the user, with a way to Settings › Sync (docs/sync/ROADMAP.md S5). The Decks screen shows it only
 * for a status that [com.yahyafati.mnemo.core.data.sync.attentionFrom] says deserves it: nothing shows while sync works.
 */
@Composable
internal fun SyncBanner(status: SyncStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val message = stringResource(
        when (status) {
            is SyncStatus.Restored -> Res.string.feature_decks_sync_restored
            is SyncStatus.Error -> when (status.problem) {
                SyncProblem.PassphraseRequired, SyncProblem.PassphraseWrong -> Res.string.feature_decks_sync_passphrase
                SyncProblem.Replaced, SyncProblem.MustRejoin -> Res.string.feature_decks_sync_rejoin
                SyncProblem.UpdateRequired -> Res.string.feature_decks_sync_update
                SyncProblem.LocationGone -> Res.string.feature_decks_sync_gone
                SyncProblem.Auth -> Res.string.feature_decks_sync_auth
                SyncProblem.Quota -> Res.string.feature_decks_sync_quota
                SyncProblem.Offline, SyncProblem.LocationNotEmpty, SyncProblem.Other -> Res.string.feature_decks_sync_stalled
            }
            else -> Res.string.feature_decks_sync_stalled
        },
    )
    Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = MnemoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.SyncProblem, null, tint = colors.error, modifier = Modifier.size(20.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = MnemoTheme.spacing.sm, vertical = MnemoTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(Res.string.feature_decks_sync_title), style = MaterialTheme.typography.titleSmall, color = colors.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.onErrorContainer)
            }
            TextButton(onClick = onOpen) { Text(stringResource(Res.string.feature_decks_sync_open)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SyncBannerPreview() {
    MnemoTheme {
        SyncBanner(
            status = SyncStatus.Error(SyncBackend.Folder("/sync"), SyncProblem.PassphraseRequired, null),
            onOpen = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
