package com.yahyafati.mnemo.core.designsystem.platform.android

import android.os.Build
import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities

/** What an Android phone or tablet offers. Only dynamic color depends on the version: Android 12 and up. */
fun androidPlatformCapabilities(sdk: Int = Build.VERSION.SDK_INT) = PlatformCapabilities(
    dynamicColor = sdk >= Build.VERSION_CODES.S,
    reminders = true,
    widget = true,
    dictation = true,
    textToSpeech = true,
    runtimePermissions = true,
    dynamicColorSetting = true,
    localhostHint = true,
    shareFiles = true,
)
