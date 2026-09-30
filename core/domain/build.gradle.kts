plugins {
    alias(libs.plugins.mnemo.android.library)
}

// An Android library only because it depends on `:core:data`, which still is one (D4 converts both).
// Keep it free of Android APIs.
dependencies {
    api(projects.core.data)
    implementation(projects.core.scheduler)
    implementation(libs.koin.core)

    testImplementation(projects.core.testing)
}
