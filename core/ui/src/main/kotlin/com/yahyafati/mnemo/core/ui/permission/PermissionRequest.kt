package com.yahyafati.mnemo.core.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.yahyafati.mnemo.core.designsystem.platform.LocalPlatformCapabilities
import com.yahyafati.mnemo.core.ui.permission.android.rememberAndroidPermissionRequest

/** A permission the user grants at run time. */
enum class AppPermission {
    /** Showing a notification (the reminder, the progress of a long import). */
    Notifications,

    /** Listening, for dictation. */
    Microphone,
}

/**
 * Asking for one [AppPermission]. Use [PermissionRationaleDialog] first when the user's tap
 * doesn't already explain the request.
 */
@Stable
interface PermissionRequest {
    /** Whether the app may use it now. Always true where the platform has no such permission. */
    val isGranted: Boolean

    /** Whether [launch] would show the system's prompt: it isn't granted, and the platform asks. */
    val canRequest: Boolean

    /** Shows the system's prompt and reports the answer; where nothing is to be asked, reports [isGranted] at once. */
    fun launch()
}

/**
 * A [PermissionRequest] for [permission] that reports its answer to [onResult]. On a platform
 * without run-time permissions ([com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities.runtimePermissions])
 * everything is granted and nothing is asked.
 *
 * When the modules become multiplatform (D5), the Android part becomes an `actual`.
 */
@Composable
fun rememberPermissionRequest(permission: AppPermission, onResult: (Boolean) -> Unit): PermissionRequest {
    if (!LocalPlatformCapabilities.current.runtimePermissions) {
        val latest by rememberUpdatedState(onResult)
        return remember {
            object : PermissionRequest {
                override val isGranted = true
                override val canRequest = false

                override fun launch() = latest(true)
            }
        }
    }
    return rememberAndroidPermissionRequest(permission, onResult)
}
