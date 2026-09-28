plugins {
    alias(libs.plugins.mnemo.jvm.library)
    alias(libs.plugins.mnemo.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
}
