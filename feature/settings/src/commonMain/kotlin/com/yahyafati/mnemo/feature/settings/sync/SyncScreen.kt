package com.yahyafati.mnemo.feature.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncDeviceSummary
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.SyncStatus
import com.yahyafati.mnemo.core.data.sync.backend
import com.yahyafati.mnemo.core.data.sync.lastSyncAt
import com.yahyafati.mnemo.core.designsystem.component.MnemoButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoButtonStyle
import com.yahyafati.mnemo.core.designsystem.component.MnemoIconButton
import com.yahyafati.mnemo.core.designsystem.component.MnemoTopBar
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.files.rememberFolderPicker
import com.yahyafati.mnemo.core.ui.scroll.ScrollbarBox
import com.yahyafati.mnemo.feature.settings.Hint
import com.yahyafati.mnemo.feature.settings.Section
import com.yahyafati.mnemo.feature.settings.formatted
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_back
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_cancel
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ok
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_checking
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_device_detail
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_devices
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_devices_loading
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_devices_unavailable
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_encrypted
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_enter_passphrase
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_folder_hint_desktop
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_folder_hint_phone
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_intro
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_again
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_joined
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_joined_backup
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_last
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_last_never
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leave
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leave_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_location
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_not_encrypted
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_now
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_privacy
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_auth
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_gone
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_must_rejoin
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_not_empty
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_offline
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_other
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_passphrase_required
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_passphrase_wrong
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_quota
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_replaced
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_update
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_rejoin
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_rejoin_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_restored_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_restored_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_status_idle
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_status_pending
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_status_syncing
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_status_waiting
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_stop
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_this_device
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_use_folder
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_use_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_drive_hint
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_signing_in
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_sign_in_again
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_location_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_auth_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_quota_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_gone_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_problem_cancelled
import java.net.URLDecoder
import java.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Everything the Sync screen can ask for, so the stateless screen stays previewable. */
internal class SyncCallbacks(
    val onFolderPicked: (String) -> Unit = {},
    val onUseGoogleDrive: () -> Unit = {},
    val onCancelSignIn: () -> Unit = {},
    val onSignInAgain: () -> Unit = {},
    val onSyncNow: () -> Unit = {},
    val onAskToLeave: () -> Unit = {},
    val onAskToDelete: () -> Unit = {},
    val onAskForPassphrase: () -> Unit = {},
    val onAskToRejoin: () -> Unit = {},
    val onAskToUploadAsNew: () -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val dialogs: SyncDialogCallbacks = SyncDialogCallbacks(),
)

/** Settings › Sync. */
@Composable
fun SyncRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SyncViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SyncScreen(
        state = state,
        callbacks = SyncCallbacks(
            onFolderPicked = viewModel::onFolderPicked,
            onUseGoogleDrive = viewModel::useGoogleDrive,
            onCancelSignIn = viewModel::cancelSignIn,
            onSignInAgain = viewModel::signInAgain,
            onSyncNow = viewModel::syncNow,
            onAskToLeave = viewModel::askToLeave,
            onAskToDelete = viewModel::askToDelete,
            onAskForPassphrase = viewModel::askForPassphrase,
            onAskToRejoin = viewModel::askToRejoin,
            onAskToUploadAsNew = viewModel::askToUploadAsNew,
            onDismissNotice = viewModel::dismissNotice,
            dialogs = SyncDialogCallbacks(
                onCreate = viewModel::create,
                onJoin = viewModel::join,
                onConfirmReplace = viewModel::confirmReplace,
                onUnlock = viewModel::unlock,
                onUploadAsNew = viewModel::uploadAsNew,
                onConfirmRejoin = viewModel::confirmRejoin,
                onConfirmLeave = viewModel::confirmLeave,
                onConfirmDelete = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDialog,
            ),
        ),
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun SyncScreen(state: SyncUiState, callbacks: SyncCallbacks, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MnemoTopBar(
                title = stringResource(Res.string.feature_settings_sync),
                navigationIcon = {
                    MnemoIconButton(
                        icon = MnemoIcons.ArrowBack,
                        contentDescription = stringResource(Res.string.feature_settings_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .padding(horizontal = MnemoTheme.spacing.screenMargin, vertical = MnemoTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.lg),
                ) {
                    state.notice?.let { Notice(it, callbacks.onDismissNotice) }
                    when (val status = state.status) {
                        SyncStatus.Off -> SetUp(state, callbacks)
                        is SyncStatus.Restored -> Restored(status, state, callbacks)
                        else -> On(status, state, callbacks)
                    }
                    if (state.status != SyncStatus.Off) {
                        DevicesSection(state.devices)
                        LeaveSection(state, callbacks)
                    }
                }
            }
        }
    }
    SyncDialogs(state.dialog, state.working, state.status.backend, callbacks.dialogs)
}

// --- Off -------------------------------------------------------------------------------------------------------------

@Composable
private fun SetUp(state: SyncUiState, callbacks: SyncCallbacks) {
    val picker = rememberFolderPicker(callbacks.onFolderPicked)
    Section(stringResource(Res.string.feature_settings_sync), MnemoIcons.Sync) {
        Text(stringResource(Res.string.feature_settings_sync_intro), style = MaterialTheme.typography.bodyMedium)
        Hint(stringResource(Res.string.feature_settings_sync_privacy))
        MnemoButton(
            text = stringResource(Res.string.feature_settings_sync_use_folder),
            onClick = { picker.launch() },
            enabled = !state.working,
            leadingIcon = MnemoIcons.Folder,
        )
        Hint(
            stringResource(
                if (LocalPlatformCapabilities.current.syncAppFolders) {
                    Res.string.feature_settings_sync_folder_hint_desktop
                } else {
                    Res.string.feature_settings_sync_folder_hint_phone
                },
            ),
        )
        if (state.googleDriveAvailable) {
            MnemoButton(
                text = stringResource(Res.string.feature_settings_sync_use_drive),
                onClick = callbacks.onUseGoogleDrive,
                enabled = !state.working,
                style = MnemoButtonStyle.Secondary,
                leadingIcon = MnemoIcons.Cloud,
            )
            Hint(stringResource(Res.string.feature_settings_sync_drive_hint))
        }
        if (state.signingIn) {
            Working(stringResource(Res.string.feature_settings_sync_signing_in))
            TextButton(onClick = callbacks.onCancelSignIn) { Text(stringResource(Res.string.feature_settings_cancel)) }
        } else if (state.working) {
            Working(stringResource(Res.string.feature_settings_sync_checking))
        }
    }
}

// --- Restored ----------------------------------------------------------------------------------------------------------

@Composable
private fun Restored(status: SyncStatus.Restored, state: SyncUiState, callbacks: SyncCallbacks) {
    Section(stringResource(Res.string.feature_settings_sync), MnemoIcons.SyncProblem) {
        Text(stringResource(Res.string.feature_settings_sync_restored_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(Res.string.feature_settings_sync_restored_message), style = MaterialTheme.typography.bodyMedium)
        LocationHint(status.backend)
        ActionRow(
            summary = stringResource(Res.string.feature_settings_sync_upload_summary),
            actionLabel = stringResource(Res.string.feature_settings_sync_upload),
            enabled = !state.working,
            onClick = callbacks.onAskToUploadAsNew,
        )
        ActionRow(
            summary = stringResource(Res.string.feature_settings_sync_rejoin_summary),
            actionLabel = stringResource(Res.string.feature_settings_sync_rejoin),
            enabled = !state.working,
            onClick = callbacks.onAskToRejoin,
        )
    }
}

// --- On ----------------------------------------------------------------------------------------------------------------

@Composable
private fun On(status: SyncStatus, state: SyncUiState, callbacks: SyncCallbacks) {
    val backend = status.backend ?: return
    val syncing = status is SyncStatus.Syncing
    Section(stringResource(Res.string.feature_settings_sync), if (status is SyncStatus.Error) MnemoIcons.SyncProblem else MnemoIcons.Sync) {
        StatusLine(status, state.pendingChanges)
        status.lastSyncAt.let { last ->
            Hint(
                if (last == null) {
                    stringResource(Res.string.feature_settings_sync_last_never)
                } else {
                    stringResource(Res.string.feature_settings_sync_last, formatted(last))
                },
            )
        }
        if (status is SyncStatus.Error) Problem(status.problem, backend, callbacks)
        MnemoButton(
            text = stringResource(Res.string.feature_settings_sync_now),
            onClick = callbacks.onSyncNow,
            enabled = !syncing && !state.working,
            leadingIcon = MnemoIcons.Sync,
        )
        if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        LocationHint(backend)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
            Icon(MnemoIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Hint(
                stringResource(
                    if (state.encrypted) Res.string.feature_settings_sync_encrypted else Res.string.feature_settings_sync_not_encrypted,
                ),
            )
        }
    }
}

@Composable
private fun StatusLine(status: SyncStatus, pending: Int) {
    val text = when (status) {
        is SyncStatus.Syncing -> stringResource(Res.string.feature_settings_sync_status_syncing)
        is SyncStatus.WaitingForNetwork -> stringResource(Res.string.feature_settings_sync_status_waiting)
        is SyncStatus.Error -> return
        else -> if (pending > 0) {
            pluralStringResource(Res.plurals.feature_settings_sync_status_pending, pending, pending)
        } else {
            stringResource(Res.string.feature_settings_sync_status_idle)
        }
    }
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/** What went wrong and, where the user can do something about it, the button for that. */
@Composable
private fun Problem(problem: SyncProblem, backend: SyncBackend, callbacks: SyncCallbacks) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.small, color = colors.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(MnemoTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
            Text(syncProblemText(problem, backend), style = MaterialTheme.typography.bodyMedium, color = colors.onErrorContainer)
            when (problem) {
                SyncProblem.PassphraseRequired, SyncProblem.PassphraseWrong -> MnemoButton(
                    text = stringResource(Res.string.feature_settings_sync_enter_passphrase),
                    onClick = callbacks.onAskForPassphrase,
                    leadingIcon = MnemoIcons.Key,
                )
                SyncProblem.Auth -> if (backend == SyncBackend.GoogleDrive) {
                    MnemoButton(
                        text = stringResource(Res.string.feature_settings_sync_sign_in_again),
                        onClick = callbacks.onSignInAgain,
                        leadingIcon = MnemoIcons.Key,
                    )
                }
                SyncProblem.Replaced, SyncProblem.MustRejoin -> MnemoButton(
                    text = stringResource(Res.string.feature_settings_sync_join_again),
                    onClick = callbacks.onAskToRejoin,
                    leadingIcon = MnemoIcons.Sync,
                )
                else -> Unit
            }
        }
    }
}

// --- Devices, leaving -------------------------------------------------------------------------------------------------

@Composable
private fun DevicesSection(devices: SyncDevices) {
    Section(stringResource(Res.string.feature_settings_sync_devices), MnemoIcons.Phone) {
        when (devices) {
            SyncDevices.Loading -> Hint(stringResource(Res.string.feature_settings_sync_devices_loading))
            SyncDevices.Unavailable -> Hint(stringResource(Res.string.feature_settings_sync_devices_unavailable))
            is SyncDevices.Loaded -> devices.devices.forEach { Device(it) }
        }
    }
}

@Composable
private fun Device(device: SyncDeviceSummary) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.md)) {
        Icon(
            if (device.platform.contains("android", ignoreCase = true)) MnemoIcons.Phone else MnemoIcons.Local,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (device.isThisDevice) stringResource(Res.string.feature_settings_sync_this_device, device.name) else device.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Hint(stringResource(Res.string.feature_settings_sync_device_detail, device.platform, formatted(device.lastSeenAt)))
        }
    }
}

@Composable
private fun LeaveSection(state: SyncUiState, callbacks: SyncCallbacks) {
    Section(stringResource(Res.string.feature_settings_sync_stop), MnemoIcons.Close) {
        ActionRow(
            summary = stringResource(Res.string.feature_settings_sync_leave_summary),
            actionLabel = stringResource(Res.string.feature_settings_sync_leave),
            enabled = !state.working,
            onClick = callbacks.onAskToLeave,
        )
        ActionRow(
            summary = stringResource(Res.string.feature_settings_sync_delete_summary),
            actionLabel = stringResource(Res.string.feature_settings_sync_delete),
            enabled = !state.working,
            destructive = true,
            onClick = callbacks.onAskToDelete,
        )
    }
}

// --- Pieces ----------------------------------------------------------------------------------------------------------

@Composable
private fun ActionRow(summary: String, actionLabel: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Hint(summary)
        MnemoButton(
            text = actionLabel,
            onClick = onClick,
            enabled = enabled,
            style = if (destructive) MnemoButtonStyle.Text else MnemoButtonStyle.Secondary,
        )
    }
}

@Composable
private fun Notice(notice: SyncNotice, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = colors.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = MnemoTheme.spacing.md).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(MnemoIcons.CheckCircle, null, tint = colors.secondary)
            Column(Modifier.weight(1f).padding(horizontal = MnemoTheme.spacing.sm, vertical = MnemoTheme.spacing.sm)) {
                when (notice) {
                    is SyncNotice.Joined -> {
                        Text(stringResource(Res.string.feature_settings_sync_joined), style = MaterialTheme.typography.titleSmall)
                        notice.safetyBackup?.let {
                            Text(
                                stringResource(Res.string.feature_settings_sync_joined_backup, it),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_settings_ok)) }
        }
    }
}

@Composable
internal fun Working(text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.xs)) {
        Hint(text)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

/** The words for a problem, in the status card and in the dialogs. */
@Composable
internal fun syncProblemText(problem: SyncProblem, backend: SyncBackend? = null): String = stringResource(
    when (problem) {
        SyncProblem.Offline -> Res.string.feature_settings_sync_problem_offline
        SyncProblem.Auth -> if (backend == SyncBackend.GoogleDrive) Res.string.feature_settings_sync_problem_auth_drive else Res.string.feature_settings_sync_problem_auth
        SyncProblem.Quota -> if (backend == SyncBackend.GoogleDrive) Res.string.feature_settings_sync_problem_quota_drive else Res.string.feature_settings_sync_problem_quota
        SyncProblem.PassphraseRequired -> Res.string.feature_settings_sync_problem_passphrase_required
        SyncProblem.PassphraseWrong -> Res.string.feature_settings_sync_problem_passphrase_wrong
        SyncProblem.UpdateRequired -> Res.string.feature_settings_sync_problem_update
        SyncProblem.LocationGone -> if (backend == SyncBackend.GoogleDrive) Res.string.feature_settings_sync_problem_gone_drive else Res.string.feature_settings_sync_problem_gone
        SyncProblem.Replaced -> Res.string.feature_settings_sync_problem_replaced
        SyncProblem.MustRejoin -> Res.string.feature_settings_sync_problem_must_rejoin
        SyncProblem.LocationNotEmpty -> Res.string.feature_settings_sync_problem_not_empty
        SyncProblem.SignInCancelled -> Res.string.feature_settings_sync_problem_cancelled
        SyncProblem.Other -> Res.string.feature_settings_sync_problem_other
    },
)

/**
 * A readable name for a location: a Storage Access Framework tree (`content://…/tree/primary%3ADocuments%2FMnemo`) as
 * "Documents/Mnemo", a path on a computer as it is.
 */
internal fun folderLabel(location: String): String =
    if (location.startsWith("content:")) {
        URLDecoder.decode(location.substringAfterLast('/'), "UTF-8").substringAfter(':').ifBlank { location }
    } else {
        location
    }

/** Where the sync data is: "Folder: Documents/Mnemo" or Google Drive. */
@Composable
private fun LocationHint(backend: SyncBackend) {
    Hint(
        when (backend) {
            is SyncBackend.Folder -> stringResource(Res.string.feature_settings_sync_location, folderLabel(backend.location))
            SyncBackend.GoogleDrive -> stringResource(Res.string.feature_settings_sync_location_drive)
        },
    )
}

@Preview(heightDp = 900)
@Composable
private fun SyncScreenPreview() {
    MnemoTheme {
        SyncScreen(
            state = SyncUiState(
                status = SyncStatus.Idle(SyncBackend.Folder("/Users/me/Sync/Mnemo"), Instant.now()),
                encrypted = true,
                devices = SyncDevices.Loaded(listOf(SyncDeviceSummary("a", "MacBook", "Desktop (macOS)", "1.0.0", Instant.now(), true))),
            ),
            callbacks = SyncCallbacks(),
            onBack = {},
        )
    }
}
