plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.hilt)
}

// Android library only for Hilt's convenience (ARCHITECTURE §3). Keep it free of Android APIs.
dependencies {
    api(projects.core.data)
    implementation(projects.core.scheduler)

    testImplementation(projects.core.testing)
}
