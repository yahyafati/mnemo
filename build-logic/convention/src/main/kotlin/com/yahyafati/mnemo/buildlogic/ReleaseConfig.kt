package com.yahyafati.mnemo.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import java.util.Properties

/**
 * `versionCode` and `versionName` come from `gradle.properties` (`mnemo.versionCode`,
 * `mnemo.versionName`), the single place to bump for a release. Play needs `versionCode` to go up
 * by one with every upload.
 */
internal fun Project.configureVersioning(extension: ApplicationExtension) {
    val code = providers.gradleProperty("mnemo.versionCode").orNull?.toIntOrNull()
        ?: error("gradle.properties: mnemo.versionCode must be an integer")
    val name = providers.gradleProperty("mnemo.versionName").orNull
        ?: error("gradle.properties: mnemo.versionName is missing")
    extension.defaultConfig.versionCode = code
    extension.defaultConfig.versionName = name
}

/** Upload-keystore settings, from environment variables first and `local.properties` second. */
private const val KEYSTORE_FILE = "MNEMO_KEYSTORE_FILE"
private const val KEYSTORE_PASSWORD = "MNEMO_KEYSTORE_PASSWORD"
private const val KEY_ALIAS = "MNEMO_KEY_ALIAS"
private const val KEY_PASSWORD = "MNEMO_KEY_PASSWORD"

/**
 * Signs the release build with the upload key when [KEYSTORE_FILE], [KEYSTORE_PASSWORD] and
 * [KEY_ALIAS] are set (environment variables, or the same names in `local.properties`;
 * [KEY_PASSWORD] defaults to the keystore password). Without them the release build is left
 * unsigned, so CI and F-Droid, which signs with its own key, can still build it.
 * The keystore and its passwords never belong in the repository (see `.gitignore`).
 */
internal fun Project.configureReleaseSigning(extension: ApplicationExtension) {
    fun setting(name: String): String? = buildSetting(name)

    val storeFile = setting(KEYSTORE_FILE)?.let(::file)
    val storePassword = setting(KEYSTORE_PASSWORD)
    val alias = setting(KEY_ALIAS)
    if (storeFile == null || storePassword == null || alias == null) return
    if (!storeFile.isFile) {
        logger.warn("$KEYSTORE_FILE points to a missing file ($storeFile): the release build stays unsigned.")
        return
    }

    val release = extension.signingConfigs.create("release") {
        this.storeFile = storeFile
        this.storePassword = storePassword
        keyAlias = alias
        keyPassword = setting(KEY_PASSWORD) ?: storePassword
    }
    extension.buildTypes.getByName("release").signingConfig = release
}

/**
 * A build setting that doesn't belong in the repository: an environment variable first, `local.properties` second, null
 * when neither has a non-blank value. Signing (above) and the Google client (`GoogleConfig.kt`) use it.
 */
internal fun Project.buildSetting(name: String): String? {
    val local = Properties().apply {
        val file = rootProject.layout.projectDirectory.file("local.properties").asFile
        if (file.isFile) file.inputStream().use(::load)
    }
    return providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
        ?: local.getProperty(name)?.takeIf { it.isNotBlank() }
}
