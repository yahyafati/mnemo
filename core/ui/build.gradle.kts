plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.mnemo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(libs.kotlinx.serialization.json)
}
