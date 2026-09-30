plugins {
    alias(libs.plugins.mnemo.android.library)
}

dependencies {
    api(projects.core.common)
    api(projects.core.data)
    api(projects.core.security)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.androidx.test.runner)
}
