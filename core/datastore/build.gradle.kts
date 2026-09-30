plugins {
    alias(libs.plugins.mnemo.android.library)
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(libs.koin.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
