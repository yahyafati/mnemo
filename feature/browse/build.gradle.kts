plugins {
    alias(libs.plugins.mnemo.kmp.feature)
}

roborazzi {
    // Baselines of the Robolectric screenshot tests; the tests name their own files.
    outputDir.set(layout.projectDirectory.dir("src/androidHostTest/screenshots"))
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.feature.browse.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.paging.compose)
        }
        commonTest.dependencies {
            implementation(libs.androidx.paging.testing)
        }
    }
}
