plugins {
    alias(libs.plugins.mnemo.kmp.feature)
}

roborazzi {
    // Baselines of the Robolectric screenshot tests; the tests name their own files.
    outputDir.set(layout.projectDirectory.dir("src/androidHostTest/screenshots"))
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.feature.create.resources"
}
