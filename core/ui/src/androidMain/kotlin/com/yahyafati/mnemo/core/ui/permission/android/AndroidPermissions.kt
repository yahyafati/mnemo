package com.yahyafati.mnemo.core.ui.permission.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yahyafati.mnemo.core.ui.permission.AppPermission
import com.yahyafati.mnemo.core.ui.permission.PermissionRequest

/**
 * Android's run-time permissions. Notifications are a permission from Android 13; before that
 * they can't be asked for, only be switched off in the system settings, which [isGranted] shows.
 */
@Composable
fun rememberAndroidPermissionRequest(permission: AppPermission, onResult: (Boolean) -> Unit): PermissionRequest {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> latest(granted) }
    return remember(context, permission, launcher) {
        object : PermissionRequest {
            private val name = manifestPermission(permission)

            override val isGranted: Boolean
                get() = when (permission) {
                    AppPermission.Notifications -> NotificationManagerCompat.from(context).areNotificationsEnabled()
                    AppPermission.Microphone -> holds(context, checkNotNull(name))
                }

            override val canRequest: Boolean get() = name != null && !holds(context, name)

            override fun launch() {
                if (name == null) latest(isGranted) else launcher.launch(name)
            }
        }
    }
}

/** The manifest permission behind [permission] on this device; null if it has none (notifications before Android 13). */
private fun manifestPermission(permission: AppPermission): String? = when (permission) {
    AppPermission.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    AppPermission.Microphone -> Manifest.permission.RECORD_AUDIO
}

private fun holds(context: Context, name: String) =
    ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
