plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.hilt)
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(projects.core.common)
    implementation(projects.core.model)

    testImplementation(libs.kotlinx.coroutines.test)
}
