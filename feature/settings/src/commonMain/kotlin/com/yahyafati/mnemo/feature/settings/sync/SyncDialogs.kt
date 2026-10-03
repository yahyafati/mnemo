package com.yahyafati.mnemo.feature.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yahyafati.mnemo.core.data.sync.SyncBackend
import com.yahyafati.mnemo.core.data.sync.SyncProblem
import com.yahyafati.mnemo.core.data.sync.WebDavAddress
import com.yahyafati.mnemo.core.model.AiEndpoint
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.feature.settings.SwitchRow
import com.yahyafati.mnemo.feature.settings.resources.Res
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_cancel
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_ok
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_title_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_message_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_title_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_message_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_folder_problem_title_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_encrypt_summary_drive
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete_others
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_delete_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_encrypt
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_encrypt_summary
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_folder_problem_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_hide
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_encrypted
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leave_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leave_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leave_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leftovers_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_leftovers_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_passphrase
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_passphrase_empty
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_passphrase_mismatch
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_passphrase_repeat
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_passphrase_short
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_rejoin_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_rejoin_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_replace_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_replace_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_replace_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_show
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_unlock_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_unlock_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_unlock_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_confirm
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_others
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_others_unknown
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_upload_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_working
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_title_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_create_message_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_title_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_join_message_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_folder_problem_title_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_encrypt_summary_webdav
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_reconnect_title
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_reconnect_message
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_url
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_url_hint
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_user
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_password
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_url_invalid
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_url_insecure
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_test
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_continue
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_test_found
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_test_create
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_test_failed
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_failure_auth
import com.yahyafati.mnemo.feature.settings.resources.feature_settings_sync_webdav_failure_gone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** What the dialogs of the Sync screen do. A passphrase is a string only for as long as the dialog is open. */
internal class SyncDialogCallbacks(
    val onWebDavTest: (url: String, username: String, password: String) -> Unit = { _, _, _ -> },
    val onWebDavConnect: (url: String, username: String, password: String) -> Unit = { _, _, _ -> },
    val onCreate: (passphrase: String?) -> Unit = {},
    val onJoin: (passphrase: String?) -> Unit = {},
    val onConfirmReplace: () -> Unit = {},
    val onUnlock: (passphrase: String) -> Unit = {},
    val onUploadAsNew: (passphrase: String?) -> Unit = {},
    val onConfirmRejoin: () -> Unit = {},
    val onConfirmLeave: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** The question or form that is open over the screen, if any. [working] greys its buttons while a call runs. */
@Composable
internal fun SyncDialogs(dialog: SyncDialog?, working: Boolean, backend: SyncBackend?, callbacks: SyncDialogCallbacks) {
    when (dialog) {
        null -> Unit
        is SyncDialog.Create -> NewSyncDataDialog(
            title = dialog.backend.pick(
                folder = Res.string.feature_settings_sync_create_title,
                drive = Res.string.feature_settings_sync_create_title_drive,
                webDav = Res.string.feature_settings_sync_create_title_webdav,
            ),
            message = stringResource(
                dialog.backend.pick(
                    folder = Res.string.feature_settings_sync_create_message,
                    drive = Res.string.feature_settings_sync_create_message_drive,
                    webDav = Res.string.feature_settings_sync_create_message_webdav,
                ),
            ),
            extra = null,
            confirm = Res.string.feature_settings_sync_create_confirm,
            encryptByDefault = dialog.backend.isCloud,
            encryptSummary = dialog.backend.encryptSummary(),
            failure = dialog.failure,
            backend = dialog.backend,
            working = working,
            onConfirm = callbacks.onCreate,
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.Upload -> NewSyncDataDialog(
            title = Res.string.feature_settings_sync_upload_title,
            message = stringResource(Res.string.feature_settings_sync_upload_message),
            extra = if (dialog.otherDevices.isEmpty()) {
                stringResource(Res.string.feature_settings_sync_upload_others_unknown)
            } else {
                stringResource(Res.string.feature_settings_sync_upload_others, dialog.otherDevices.joinToString())
            },
            confirm = Res.string.feature_settings_sync_upload_confirm,
            encryptByDefault = dialog.encryptByDefault,
            encryptSummary = if (dialog.encryptByDefault) backend.encryptSummary() else Res.string.feature_settings_sync_encrypt_summary,
            failure = dialog.failure,
            backend = backend,
            working = working,
            onConfirm = callbacks.onUploadAsNew,
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.Join -> PassphraseDialog(
            icon = MnemoIcons.Sync,
            title = stringResource(
                dialog.backend.pick(
                    folder = Res.string.feature_settings_sync_join_title,
                    drive = Res.string.feature_settings_sync_join_title_drive,
                    webDav = Res.string.feature_settings_sync_join_title_webdav,
                ),
            ),
            message = stringResource(
                dialog.backend.pick(
                    folder = Res.string.feature_settings_sync_join_message,
                    drive = Res.string.feature_settings_sync_join_message_drive,
                    webDav = Res.string.feature_settings_sync_join_message_webdav,
                ),
            ),
            hint = if (dialog.encrypted) stringResource(Res.string.feature_settings_sync_join_encrypted) else null,
            askForPassphrase = dialog.encrypted,
            confirm = stringResource(Res.string.feature_settings_sync_join_confirm),
            failure = dialog.failure,
            backend = dialog.backend,
            working = working,
            onConfirm = callbacks.onJoin,
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.Unlock -> PassphraseDialog(
            icon = MnemoIcons.Key,
            title = stringResource(Res.string.feature_settings_sync_unlock_title),
            message = stringResource(Res.string.feature_settings_sync_unlock_message),
            hint = null,
            askForPassphrase = true,
            confirm = stringResource(Res.string.feature_settings_sync_unlock_confirm),
            failure = dialog.failure,
            backend = backend,
            working = working,
            onConfirm = { callbacks.onUnlock(it.orEmpty()) },
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.ConfirmReplace -> Confirm(
            title = stringResource(Res.string.feature_settings_sync_replace_title),
            message = stringResource(Res.string.feature_settings_sync_replace_message),
            confirm = stringResource(Res.string.feature_settings_sync_replace_confirm),
            failure = dialog.failure,
            backend = dialog.backend,
            working = working,
            destructive = true,
            onConfirm = callbacks.onConfirmReplace,
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.ConfirmRejoin -> Confirm(
            title = stringResource(Res.string.feature_settings_sync_rejoin_title),
            message = stringResource(Res.string.feature_settings_sync_rejoin_message),
            confirm = stringResource(Res.string.feature_settings_sync_replace_confirm),
            failure = dialog.failure,
            backend = backend,
            working = working,
            destructive = true,
            onConfirm = callbacks.onConfirmRejoin,
            onDismiss = callbacks.onDismiss,
        )
        SyncDialog.ConfirmLeave -> Confirm(
            title = stringResource(Res.string.feature_settings_sync_leave_title),
            message = stringResource(Res.string.feature_settings_sync_leave_message),
            confirm = stringResource(Res.string.feature_settings_sync_leave_confirm),
            failure = null,
            backend = backend,
            working = working,
            destructive = false,
            onConfirm = callbacks.onConfirmLeave,
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.ConfirmDelete -> Confirm(
            title = stringResource(Res.string.feature_settings_sync_delete_title),
            message = stringResource(Res.string.feature_settings_sync_delete_message) +
                if (dialog.otherDevices.isEmpty()) "" else "\n\n" + stringResource(Res.string.feature_settings_sync_delete_others, dialog.otherDevices.joinToString()),
            confirm = stringResource(Res.string.feature_settings_sync_delete_confirm),
            failure = dialog.failure,
            backend = backend,
            working = working,
            destructive = true,
            onConfirm = callbacks.onConfirmDelete,
            onDismiss = callbacks.onDismiss,
        )
        SyncDialog.Leftovers -> Info(
            title = stringResource(Res.string.feature_settings_sync_leftovers_title),
            message = stringResource(Res.string.feature_settings_sync_leftovers_message),
            onDismiss = callbacks.onDismiss,
        )
        is SyncDialog.WebDavSetup -> WebDavDialog(dialog, working, callbacks)
        is SyncDialog.FolderProblem -> Info(
            title = stringResource(
                dialog.backend.pick(
                    folder = Res.string.feature_settings_sync_folder_problem_title,
                    drive = Res.string.feature_settings_sync_folder_problem_title_drive,
                    webDav = Res.string.feature_settings_sync_folder_problem_title_webdav,
                ),
            ),
            message = syncProblemText(dialog.problem, dialog.backend),
            onDismiss = callbacks.onDismiss,
        )
    }
}

/** The text that fits the kind of location: a folder, Google Drive or a WebDAV server. A location not chosen yet counts as a folder. */
private fun <T> SyncBackend?.pick(folder: T, drive: T, webDav: T): T = when (this) {
    SyncBackend.GoogleDrive -> drive
    is SyncBackend.WebDav -> webDav
    else -> folder
}

private fun SyncBackend?.encryptSummary(): StringResource = pick(
    folder = Res.string.feature_settings_sync_encrypt_summary,
    drive = Res.string.feature_settings_sync_encrypt_summary_drive,
    webDav = Res.string.feature_settings_sync_encrypt_summary_webdav,
)

@Composable
private fun Confirm(
    title: String,
    message: String,
    confirm: String,
    failure: SyncProblem?,
    backend: SyncBackend?,
    working: Boolean,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                Text(message)
                failure?.let { FailureText(it, backend) }
                if (working) Working(stringResource(Res.string.feature_settings_sync_working))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !working) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(Res.string.feature_settings_cancel)) } },
    )
}

@Composable
private fun Info(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.feature_settings_ok)) } },
    )
}

@Composable
private fun FailureText(problem: SyncProblem, backend: SyncBackend?) {
    Text(syncProblemText(problem, backend), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** Asks for one passphrase (to join or unlock encrypted sync data). Without [askForPassphrase] it is just a question. */
@Composable
private fun PassphraseDialog(
    icon: ImageVector,
    title: String,
    message: String,
    hint: String?,
    askForPassphrase: Boolean,
    confirm: String,
    failure: SyncProblem?,
    backend: SyncBackend?,
    working: Boolean,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Deliberately `remember`, not `rememberSaveable`: a passphrase must not end up in saved state.
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm)) {
                Text(message)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (askForPassphrase) PassphraseField(passphrase, { passphrase = it }, stringResource(Res.string.feature_settings_sync_passphrase), error = null)
                failure?.let { FailureText(it, backend) }
                if (working) Working(stringResource(Res.string.feature_settings_sync_working))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(if (askForPassphrase) passphrase else null) }, enabled = !working && (!askForPassphrase || passphrase.isNotEmpty())) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(Res.string.feature_settings_cancel)) } },
    )
}

/** Starting sync data (new or again): choose whether to encrypt it, and with what. */
@Composable
private fun NewSyncDataDialog(
    title: StringResource,
    message: String,
    extra: String?,
    confirm: StringResource,
    encryptByDefault: Boolean,
    encryptSummary: StringResource,
    failure: SyncProblem?,
    backend: SyncBackend?,
    working: Boolean,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var encrypt by remember { mutableStateOf(encryptByDefault) }
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val problem = if (encrypt) SyncPassphrase.check(passphrase, repeated) else null
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(MnemoIcons.Sync, null) },
        title = { Text(stringResource(title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
            ) {
                Text(message)
                extra?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                SwitchRow(
                    title = stringResource(Res.string.feature_settings_sync_encrypt),
                    summary = stringResource(encryptSummary),
                    checked = encrypt,
                    onCheckedChange = { encrypt = it },
                    enabled = !working,
                )
                if (encrypt) {
                    PassphraseField(
                        passphrase,
                        { passphrase = it },
                        stringResource(Res.string.feature_settings_sync_passphrase),
                        error = if (attempted && problem != null && problem != PassphraseProblem.Mismatch) problemText(problem) else null,
                    )
                    PassphraseField(
                        repeated,
                        { repeated = it },
                        stringResource(Res.string.feature_settings_sync_passphrase_repeat),
                        error = if (attempted && problem == PassphraseProblem.Mismatch) problemText(problem) else null,
                    )
                }
                failure?.let { FailureText(it, backend) }
                if (working) Working(stringResource(Res.string.feature_settings_sync_working))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (problem == null) onConfirm(if (encrypt) passphrase else null)
                },
                enabled = !working,
            ) { Text(stringResource(confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(Res.string.feature_settings_cancel)) } },
    )
}

@Composable
private fun problemText(problem: PassphraseProblem): String = when (problem) {
    PassphraseProblem.Empty -> stringResource(Res.string.feature_settings_sync_passphrase_empty)
    PassphraseProblem.TooShort -> stringResource(Res.string.feature_settings_sync_passphrase_short, SyncPassphrase.MIN_LENGTH)
    PassphraseProblem.Mismatch -> stringResource(Res.string.feature_settings_sync_passphrase_mismatch)
}

@Composable
private fun PassphraseField(value: String, onValueChange: (String) -> Unit, label: String, error: String?) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(stringResource(if (visible) Res.string.feature_settings_sync_hide else Res.string.feature_settings_sync_show))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The form for a WebDAV server: address, user name and app password. "Test connection" reports without keeping anything;
 * "Continue" checks, keeps the password and moves on to what the folder holds. Asked again after a refused password
 * ([SyncDialog.WebDavSetup.reconnect]) the address and user are fixed and only the password is typed.
 */
@Composable
private fun WebDavDialog(dialog: SyncDialog.WebDavSetup, working: Boolean, callbacks: SyncDialogCallbacks) {
    var url by remember { mutableStateOf(dialog.url) }
    var user by remember { mutableStateOf(dialog.username) }
    // Deliberately `remember`, not `rememberSaveable`: a password must not end up in saved state.
    var password by remember { mutableStateOf("") }
    val check = if (url.isBlank()) null else WebDavAddress.check(url)
    val urlError = when (check) {
        null, AiEndpoint.Check.Ok -> null
        AiEndpoint.Check.Invalid -> stringResource(Res.string.feature_settings_sync_webdav_url_invalid)
        is AiEndpoint.Check.Insecure -> stringResource(Res.string.feature_settings_sync_webdav_url_insecure)
    }
    val complete = check == AiEndpoint.Check.Ok && user.isNotBlank() && password.isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!working) callbacks.onDismiss() },
        icon = { Icon(MnemoIcons.Cloud, null) },
        title = {
            Text(stringResource(if (dialog.reconnect) Res.string.feature_settings_sync_webdav_reconnect_title else Res.string.feature_settings_sync_webdav_title))
        },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MnemoTheme.spacing.sm),
            ) {
                Text(stringResource(if (dialog.reconnect) Res.string.feature_settings_sync_webdav_reconnect_message else Res.string.feature_settings_sync_webdav_message))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(Res.string.feature_settings_sync_webdav_url)) },
                    placeholder = { Text(stringResource(Res.string.feature_settings_sync_webdav_url_hint)) },
                    singleLine = true,
                    readOnly = dialog.reconnect,
                    enabled = !dialog.reconnect,
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text(stringResource(Res.string.feature_settings_sync_webdav_user)) },
                    singleLine = true,
                    readOnly = dialog.reconnect,
                    enabled = !dialog.reconnect,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                PassphraseField(password, { password = it }, stringResource(Res.string.feature_settings_sync_webdav_password), error = null)
                when (val test = dialog.test) {
                    null -> Unit
                    WebDavTest.FolderFound -> Text(stringResource(Res.string.feature_settings_sync_webdav_test_found), style = MaterialTheme.typography.bodySmall)
                    WebDavTest.FolderWillBeCreated -> Text(stringResource(Res.string.feature_settings_sync_webdav_test_create), style = MaterialTheme.typography.bodySmall)
                    is WebDavTest.Failed -> Text(
                        stringResource(Res.string.feature_settings_sync_webdav_test_failed, webDavProblemText(test.problem)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                dialog.failure?.let { Text(webDavProblemText(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (working) Working(stringResource(Res.string.feature_settings_sync_working))
            }
        },
        confirmButton = {
            TextButton(onClick = { callbacks.onWebDavConnect(url, user, password) }, enabled = !working && complete) {
                Text(stringResource(Res.string.feature_settings_sync_webdav_continue))
            }
        },
        dismissButton = {
            Row {
                if (!dialog.reconnect) {
                    TextButton(onClick = { callbacks.onWebDavTest(url, user, password) }, enabled = !working && complete) {
                        Text(stringResource(Res.string.feature_settings_sync_webdav_test))
                    }
                }
                TextButton(onClick = callbacks.onDismiss, enabled = !working) { Text(stringResource(Res.string.feature_settings_cancel)) }
            }
        },
    )
}

/** What went wrong with a WebDAV form's account or folder, in its own words; everything else is the general wording. */
@Composable
private fun webDavProblemText(problem: SyncProblem): String = when (problem) {
    SyncProblem.Auth -> stringResource(Res.string.feature_settings_sync_webdav_failure_auth)
    SyncProblem.LocationGone -> stringResource(Res.string.feature_settings_sync_webdav_failure_gone)
    else -> syncProblemText(problem)
}
