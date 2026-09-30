plugins {
    alias(libs.plugins.mnemo.kmp.library)
    alias(libs.plugins.mnemo.kmp.compose)
    alias(libs.plugins.roborazzi)
}

roborazzi {
    // Committed baselines: record with `recordRoborazziAndroidHostTest` / `recordRoborazziDesktop`,
    // check with `verifyRoborazziAndroidHostTest` / `verifyRoborazziDesktop`. The tests name their
    // own files (`src/androidHostTest/screenshots`, `src/desktopTest/screenshots`).
    outputDir.set(layout.projectDirectory.dir("src/androidHostTest/screenshots"))
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.cmp.foundation)
            api(libs.cmp.material3)
            api(libs.cmp.ui)
            implementation(libs.cmp.material.icons.extended)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.roborazzi.compose.desktop)
        }
    }
}

compose.resources {
    packageOfResClass = "com.yahyafati.mnemo.core.designsystem.resources"
}
