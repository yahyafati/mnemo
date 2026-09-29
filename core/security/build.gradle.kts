plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.hilt)
}

// Android Keystore encryption for API keys (ARCHITECTURE §10). Only `:core:data` uses it.
dependencies {
    implementation(projects.core.common)

    testImplementation(libs.kotlinx.coroutines.test)
}
