package com.yahyafati.mnemo.desktop

import java.util.Properties

/** What the desktop launcher reads from its own resources: the version and the licenses list. */
internal object AppInfo {
    /** `mnemo.versionName`, written into `mnemo-version.properties` at build time. */
    val version: String by lazy {
        val properties = Properties()
        AppInfo::class.java.getResourceAsStream("/mnemo-version.properties")?.use(properties::load)
        properties.getProperty("version").orEmpty().takeUnless { it.contains("\${") }.orEmpty()
    }

    /** The open-source licenses JSON (Settings › About), generated from the desktop classpath. */
    fun loadLicenses(): String = checkNotNull(AppInfo::class.java.getResourceAsStream("/aboutlibraries.json")) {
        "aboutlibraries.json is not on the classpath"
    }.use { it.readBytes().decodeToString() }
}
