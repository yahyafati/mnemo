import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.yahyafati.mnemo.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // compileOnly: the plugins themselves are put on the classpath by the root build script,
    // so every module resolves the same plugin versions.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.mnemo.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.mnemo.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.mnemo.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.mnemo.android.feature.get().pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.mnemo.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("kmpLibrary") {
            id = libs.plugins.mnemo.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpRoom") {
            id = libs.plugins.mnemo.kmp.room.get().pluginId
            implementationClass = "KmpRoomConventionPlugin"
        }
        register("kmpCompose") {
            id = libs.plugins.mnemo.kmp.compose.get().pluginId
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = libs.plugins.mnemo.kmp.feature.get().pluginId
            implementationClass = "KmpFeatureConventionPlugin"
        }
        register("desktopApplication") {
            id = libs.plugins.mnemo.desktop.application.get().pluginId
            implementationClass = "DesktopApplicationConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.mnemo.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
