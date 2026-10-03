package com.yahyafati.mnemo.desktop

import com.yahyafati.mnemo.core.data.sync.GoogleClient
import java.util.Properties

/** What the desktop launcher reads from its own resources: the version and the licenses list. */
internal object AppInfo {
    /** `mnemo.versionName`, written into `mnemo-version.properties` at build time. */
    val version: String by lazy {
        val properties = Properties()
        AppInfo::class.java.getResourceAsStream("/mnemo-version.properties")?.use(properties::load)
        properties.getProperty("version").orEmpty().takeUnless { it.contains("\${") }.orEmpty()
    }

    /**
     * The Google client this build was made with (`MNEMO_GOOGLE_DESKTOP_CLIENT_ID` and `…_SECRET`, written into
     * `mnemo-google.properties` at build time), or null: then Google Drive isn't offered. Google's token endpoint
     * needs the secret with the Desktop client (ADR 0013), so a client id alone doesn't count.
     */
    val googleClient: GoogleClient? by lazy {
        val properties = Properties()
        AppInfo::class.java.getResourceAsStream("/mnemo-google.properties")?.use(properties::load)
        val id = properties.getProperty("clientId").orEmpty()
        val secret = properties.getProperty("clientSecret").orEmpty()
        if (id.isBlank() || secret.isBlank()) null else GoogleClient(id, secret)
    }

    /** The open-source licenses JSON (Settings › About), generated from the desktop classpath. */
    fun loadLicenses(): String = checkNotNull(AppInfo::class.java.getResourceAsStream("/aboutlibraries.json")) {
        "aboutlibraries.json is not on the classpath"
    }.use { it.readBytes().decodeToString() }
}
