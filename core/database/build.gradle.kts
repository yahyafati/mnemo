plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.android.room)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.androidx.room.paging)

    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
