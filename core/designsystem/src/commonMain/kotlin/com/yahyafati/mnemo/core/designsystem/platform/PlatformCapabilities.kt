package com.yahyafati.mnemo.core.designsystem.platform

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the platform can do that a screen may want to offer. Shared UI reads these instead of
 * checking the operating system, and hides what isn't there: the desktop app has no widget, no
 * reminder notification, no dictation and no text-to-speech at first (desktop ROADMAP, "Not on
 * desktop").
 *
 * These say whether a platform has the feature at all. Whether it works right now (a speech
 * recognizer being installed, a permission being granted) is asked where it is used.
 */
@Immutable
data class PlatformCapabilities(
    /** Colors taken from the wallpaper (Material You). */
    val dynamicColor: Boolean = true,
    /** The daily study reminder notification. */
    val reminders: Boolean = true,
    /** A home-screen widget. */
    val widget: Boolean = true,
    /** Dictation into Smart Extract. */
    val dictation: Boolean = true,
    /** Reading card text aloud. */
    val textToSpeech: Boolean = true,
    /** Permissions the user grants at run time (notifications, microphone). */
    val runtimePermissions: Boolean = true,
)

/**
 * The platform's [PlatformCapabilities]; provided by the app shell around [MnemoTheme][com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme].
 * Unprovided (previews, tests), everything is on: that is what the phone app has.
 */
val LocalPlatformCapabilities = staticCompositionLocalOf { PlatformCapabilities() }
