plugins {
    alias(libs.plugins.mnemo.kmp.library)
    alias(libs.plugins.mnemo.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

roborazzi {
    // The tests name their own files (`src/desktopTest/screenshots`); see the designsystem module.
    outputDir.set(layout.projectDirectory.dir("src/desktopTest/screenshots"))
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.core.ui.resources"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.designsystem)
            api(projects.core.model)
            api(libs.kotlinx.serialization.json)
            api(libs.cmp.material3.adaptive)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.webkit)
        }
        desktopMain.dependencies {
            implementation(libs.jlatexmath)
            implementation(libs.mp3spi)
            implementation(libs.vorbisspi)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.roborazzi.compose.desktop)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            // The platform seams are tested on Robolectric, like the screens that use them.
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.robolectric)
        }
    }
}
