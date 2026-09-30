plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)
    api(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)

    // The platform seams are tested on Robolectric, like the screens that use them.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
