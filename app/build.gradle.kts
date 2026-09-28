plugins {
    alias(libs.plugins.mnemo.android.application)
    alias(libs.plugins.mnemo.android.compose)
    alias(libs.plugins.mnemo.hilt)
}

android {
    namespace = "com.yahyafati.mnemo"

    defaultConfig {
        applicationId = "com.yahyafati.mnemo"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.yahyafati.mnemo.core.testing.HiltTestRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
    implementation(projects.feature.analytics)
    implementation(projects.feature.create)
    implementation(projects.feature.decks)
    implementation(projects.feature.settings)
    implementation(projects.feature.study)

    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
