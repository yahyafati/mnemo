plugins {
    alias(libs.plugins.mnemo.android.application)
    alias(libs.plugins.mnemo.android.compose)
    alias(libs.plugins.aboutlibraries)
}

// The open-source licenses screen (Settings > About) reads the raw resource this plugin generates
// from the release dependency graph. Bundled files Gradle doesn't know about (fonts, KaTeX,
// py-fsrs) are added from config/libraries. Offline: the build never calls the network.
aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("config")
    }
}


android {
    namespace = "com.yahyafati.mnemo"

    defaultConfig {
        applicationId = "com.yahyafati.mnemo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // The PDF fixtures of :core:ingest, for the instrumented check of the page renderer (AndroidPdfPageRendererTest).
        getByName("androidTest").assets.directories.add("../core/ingest/src/commonTest/resources/pdf")
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
    // The features and the shell's navigation live in :shell; this module is the Android launcher.
    implementation(projects.feature.analytics)
    implementation(projects.feature.browse)
    implementation(projects.feature.create)
    implementation(projects.feature.decks)
    implementation(projects.feature.settings)
    implementation(projects.feature.study)
    implementation(projects.shell)

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
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.aboutlibraries.core)
    testImplementation(projects.core.database)
    testImplementation(projects.core.datastore)
    testImplementation(projects.core.security)
    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.koin.test)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(projects.core.ingest)
    androidTestImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
