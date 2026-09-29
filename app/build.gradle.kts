plugins {
    alias(libs.plugins.mnemo.android.application)
    alias(libs.plugins.mnemo.android.compose)
    alias(libs.plugins.mnemo.hilt)
    alias(libs.plugins.roborazzi)
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
            // R8 shrinking and optimization; keep rules live in src/main/keepRules/.
            optimization {
                enable = true
            }
        }
    }
}

dependencies {
    implementation(projects.feature.analytics)
    implementation(projects.feature.browse)
    implementation(projects.feature.create)
    implementation(projects.feature.decks)
    implementation(projects.feature.settings)
    implementation(projects.feature.study)

    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)

    testImplementation(projects.core.database)
    testImplementation(projects.core.datastore)
    testImplementation(projects.core.security)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
