plugins {
    alias(libs.plugins.mnemo.kmp.library)
    alias(libs.plugins.mnemo.kmp.room)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(libs.androidx.room.paging)
            implementation(projects.core.model)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.robolectric)
        }
    }
}
