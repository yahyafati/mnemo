package com.yahyafati.mnemo.core.ui.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

// A computer asks for nothing at run time. This is also what `rememberPermissionRequest` returns when
// the platform reports no `runtimePermissions`, so it is only reached by a caller that ignores that.
@Composable
internal actual fun rememberPlatformPermissionRequest(permission: AppPermission, onResult: (Boolean) -> Unit): PermissionRequest {
    val latest by rememberUpdatedState(onResult)
    return remember {
        object : PermissionRequest {
            override val isGranted = true
            override val canRequest = false

            override fun launch() = latest(true)
        }
    }
}
