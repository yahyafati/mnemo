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
    /**
     * Settings lists the dynamic color option at all. A phone lists it even where it can't work
     * ([dynamicColor] off: it says why); a computer has no wallpaper to take colors from.
     */
    val dynamicColorSetting: Boolean = true,
    /**
     * The AI provider editor explains that `localhost` is the phone itself, not the computer that
     * runs the model. On a computer, `localhost` is where a local server such as Ollama runs.
     */
    val localhostHint: Boolean = true,
    /**
     * Handing a file to another app through the system share sheet (a deck's package). A computer
     * has no share sheet: it saves the file with Export instead.
     */
    val shareFiles: Boolean = true,
    /**
     * A computer's keyboard and mouse (desktop ROADMAP D7): screens show the keyboard shortcuts in
     * hints, label icon buttons with hover tooltips, and offer right-click menus and drop targets.
     * The key handlers themselves work everywhere (a tablet with a keyboard uses them too); this
     * says whether to advertise them. Off by default, unlike the flags above: the phone is the
     * baseline and has no pointer.
     */
    val keyboardAndMouse: Boolean = false,
)

/**
 * The platform's [PlatformCapabilities]; provided by the app shell around [MnemoTheme][com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme].
 * Unprovided (previews, tests), everything a phone has is on, and [PlatformCapabilities.keyboardAndMouse] is off.
 */
val LocalPlatformCapabilities = staticCompositionLocalOf { PlatformCapabilities() }
