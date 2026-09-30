plugins {
    alias(libs.plugins.mnemo.kmp.library)
}

// API keys, encrypted at rest (ARCHITECTURE §10). Android Keystore on the phone, the OS keychain
// (or a key file where there is none) on the desktop. Only `:core:data` uses it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            api(projects.core.model)
            implementation(libs.koin.core)
        }
        desktopMain.dependencies {
            implementation(libs.java.keyring)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
