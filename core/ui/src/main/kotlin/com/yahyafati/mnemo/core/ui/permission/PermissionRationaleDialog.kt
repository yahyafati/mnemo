package com.yahyafati.mnemo.core.ui.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.yahyafati.mnemo.core.designsystem.icon.MnemoIcons
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.ui.R

/**
 * Says why Mnemo needs a permission, in the words of what the user just tapped, before Android's
 * own prompt appears (Play's permission policy asks for that context). [onContinue] launches the
 * system request; [onNotNow] leaves it unasked.
 */
@Composable
fun PermissionRationaleDialog(
    icon: ImageVector,
    title: String,
    message: String,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    continueLabel: String = stringResource(R.string.core_ui_permission_continue),
    notNowLabel: String = stringResource(R.string.core_ui_permission_not_now),
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(icon, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onContinue) { Text(continueLabel) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(notNowLabel) } },
    )
}

@Preview
@Composable
private fun PermissionRationaleDialogPreview() {
    MnemoTheme {
        PermissionRationaleDialog(
            icon = MnemoIcons.Mic,
            title = "Allow the microphone?",
            message = "Mnemo listens only while you dictate.",
            onContinue = {},
            onNotNow = {},
        )
    }
}
