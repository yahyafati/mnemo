plugins {
    alias(libs.plugins.mnemo.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// Reads and writes Anki packages. Pure JVM: SQLite is reached through the androidx.sqlite driver
// API, so the app passes the Android framework driver and the tests pass the bundled one.
dependencies {
    api(projects.core.model)
    api(libs.androidx.sqlite)
    implementation(projects.core.scheduler)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    implementation(libs.zstd.kmp.okio)

    testImplementation(libs.androidx.sqlite.bundled)
}
