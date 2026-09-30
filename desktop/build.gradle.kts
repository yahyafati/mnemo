plugins {
    alias(libs.plugins.mnemo.desktop.application)
}

// The desktop launcher (docs/desktop/ROADMAP.md, ADR 0010). It starts the data layer (D4): the
// collection's directory and its single-instance lock, a staged restore, and the Koin graph. The
// window still shows the sample cards of D1 until the shared UI arrives (D5–D6).
compose.desktop {
    application {
        mainClass = "com.yahyafati.mnemo.desktop.MainKt"
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.scheduler)
    implementation(libs.koin.core)

    implementation(libs.cmp.runtime)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.ui)
    implementation(libs.cmp.material3)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(projects.core.database)
    testImplementation(projects.core.security)
    testImplementation(projects.core.testing)
    testImplementation(libs.cmp.ui.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.koin.test)
    // The native libraries the desktop app needs, loaded on every OS by NativeLibrariesTest (D0's
    // open check): SQLite for Room, zstd for Anki packages, Skia for drawing.
    testImplementation(libs.androidx.sqlite)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.zstd.kmp.okio)
    testImplementation(libs.okio)
}
