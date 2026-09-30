plugins {
    alias(libs.plugins.mnemo.kmp.library)
}

// Fakes, rules and helpers for tests in every module (ARCHITECTURE §8). Shared tests run on
// Robolectric on Android and on plain JUnit on the desktop; `PlatformTest` picks the runner.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.core.data)
            api(projects.core.security)
            api(libs.junit)
            api(libs.kotlinx.coroutines.test)
            api(libs.turbine)
            api(projects.core.database)
            api(libs.androidx.sqlite)
        }
        androidMain.dependencies {
            api(libs.androidx.test.runner)
            api(libs.androidx.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.sqlite.framework)
        }
        desktopMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}
