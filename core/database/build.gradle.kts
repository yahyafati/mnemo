plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.android.room)
    alias(libs.plugins.mnemo.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
