plugins {
    alias(libs.plugins.mnemo.kmp.feature)
}

roborazzi {
    // Baselines of the Robolectric screenshot tests; the tests name their own files.
    outputDir.set(layout.projectDirectory.dir("src/androidHostTest/screenshots"))
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.shell.resources"
}

// The app shell (D6): the tabs, the navigation host, onboarding and the app-wide state. The one
// module that knows every feature; `:app` and `:desktop` are thin launchers around it.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.analytics)
            implementation(projects.feature.browse)
            implementation(projects.feature.create)
            implementation(projects.feature.decks)
            implementation(projects.feature.settings)
            implementation(projects.feature.study)
        }
    }
}
