plugins {
    alias(libs.plugins.mnemo.kmp.library)
}

// Use cases and scheduling glue. No Android APIs: it runs on the phone and on the desktop.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            implementation(projects.core.scheduler)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
