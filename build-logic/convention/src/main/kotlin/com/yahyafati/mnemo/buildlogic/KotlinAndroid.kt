package com.yahyafati.mnemo.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal const val COMPILE_SDK = 37
internal const val TARGET_SDK = 37
internal const val MIN_SDK = 29

internal val JAVA_VERSION = JavaVersion.VERSION_11
internal val JVM_TARGET = JvmTarget.JVM_11

/** Shared Android + Kotlin options for application and library modules (AGP 9 built-in Kotlin). */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk {
            version = release(COMPILE_SDK)
        }
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
    // Robolectric reaches into JDK internals; on recent JDKs (the Gradle toolchain is 25) that
    // needs explicit access, or the sandbox fails to start.
    tasks.withType<Test>().configureEach {
        // Modules with no tests yet (Gradle 9 fails a test task that finds none).
        failOnNoDiscoveredTests.set(false)
        jvmArgs(TEST_JVM_ARGS)
    }
}

/** Options for pure Kotlin/JVM modules (`org.jetbrains.kotlin.jvm`). */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JVM_TARGET)
    }
    // Native libraries in tests (zstd, bundled SQLite in `:core:anki`) need explicit access on JDK 25.
    tasks.withType<Test>().configureEach {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

/**
 * The JVM flags tests need on recent JDKs (the Gradle toolchain is 25): Robolectric reaches into JDK
 * internals, and native libraries (zstd, bundled SQLite, Skia) need explicit access.
 */
internal val TEST_JVM_ARGS = listOf(
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--enable-native-access=ALL-UNNAMED",
)

/** `core.designsystem` → `com.yahyafati.mnemo.core.designsystem`, matching ARCHITECTURE §4.2. */
internal val Project.mnemoNamespace: String
    get() = "com.yahyafati.mnemo" + path.replace(':', '.').replace('-', '_')
