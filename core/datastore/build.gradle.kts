plugins {
    alias(libs.plugins.mnemo.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.preferences)
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
