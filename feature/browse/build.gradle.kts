plugins {
    alias(libs.plugins.mnemo.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)

    testImplementation(libs.androidx.paging.testing)
}
