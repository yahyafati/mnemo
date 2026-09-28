plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.hilt)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.robolectric)
}
