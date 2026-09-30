plugins {
    alias(libs.plugins.mnemo.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.koin.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
