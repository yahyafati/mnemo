plugins {
    alias(libs.plugins.mnemo.kmp.feature)
}

roborazzi {
    // Baselines of the Robolectric screenshot tests; the tests name their own files.
    outputDir.set(layout.projectDirectory.dir("src/androidHostTest/screenshots"))
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.feature.settings.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
        }
    }
}
