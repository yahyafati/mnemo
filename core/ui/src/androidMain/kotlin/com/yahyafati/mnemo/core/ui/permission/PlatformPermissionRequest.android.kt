package com.yahyafati.mnemo.core.ui.permission

import androidx.compose.runtime.Composable
import com.yahyafati.mnemo.core.ui.permission.android.rememberAndroidPermissionRequest

@Composable
internal actual fun rememberPlatformPermissionRequest(permission: AppPermission, onResult: (Boolean) -> Unit): PermissionRequest =
    rememberAndroidPermissionRequest(permission, onResult)
