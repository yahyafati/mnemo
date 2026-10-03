package com.yahyafati.mnemo.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project

/**
 * The Google OAuth clients of the Drive sync backend (docs/sync/ROADMAP.md S6, ADR 0013). They are build configuration,
 * read like the signing settings (environment variables, then `local.properties`) and never committed: a build without
 * them (a fork, F-Droid, CI without the secrets) simply doesn't offer Google Drive, and never touches the owner's quota
 * or consent screen.
 *
 * - [ANDROID_CLIENT_ID]: the Android client (bound to the package name and the signing certificate's SHA-1). It has no
 *   secret. It becomes `BuildConfig.GOOGLE_CLIENT_ID` in `:app`.
 * - [DESKTOP_CLIENT_ID] and [DESKTOP_CLIENT_SECRET]: the Desktop client. Google's token endpoint won't accept it without
 *   its secret (ADR 0013), so both are needed; they go into the desktop app's resources.
 */
internal const val ANDROID_CLIENT_ID = "MNEMO_GOOGLE_CLIENT_ID"
internal const val DESKTOP_CLIENT_ID = "MNEMO_GOOGLE_DESKTOP_CLIENT_ID"
internal const val DESKTOP_CLIENT_SECRET = "MNEMO_GOOGLE_DESKTOP_CLIENT_SECRET"

private val clientId = Regex("[A-Za-z0-9._-]+")

/** The value of [name] if it looks like a client id or secret (so it can't break a generated file), else null with a warning. */
internal fun Project.googleSetting(name: String): String? {
    val value = buildSetting(name) ?: return null
    if (!clientId.matches(value)) {
        logger.warn("$name is set but doesn't look like a Google client value: Google Drive stays off in this build.")
        return null
    }
    return value
}

/** Puts the Android client id into `BuildConfig.GOOGLE_CLIENT_ID` (empty without one). */
internal fun Project.configureGoogleClientAndroid(extension: ApplicationExtension) {
    extension.buildFeatures.buildConfig = true
    extension.defaultConfig.buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${googleSetting(ANDROID_CLIENT_ID).orEmpty()}\"")
}
