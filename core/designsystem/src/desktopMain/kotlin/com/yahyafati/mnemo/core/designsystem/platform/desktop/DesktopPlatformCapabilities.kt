package com.yahyafati.mnemo.core.designsystem.platform.desktop

import com.yahyafati.mnemo.core.designsystem.platform.PlatformCapabilities

/**
 * What the desktop app offers at first (desktop ROADMAP, "Not on desktop"): no wallpaper colors,
 * daily reminder, widget, dictation or text-to-speech, and no permissions to ask for at run time;
 * but a keyboard and a mouse (D7).
 */
fun desktopPlatformCapabilities() = PlatformCapabilities(
    dynamicColor = false,
    reminders = false,
    widget = false,
    dictation = false,
    textToSpeech = false,
    runtimePermissions = false,
    dynamicColorSetting = false,
    localhostHint = false,
    keyboardAndMouse = true,
)
