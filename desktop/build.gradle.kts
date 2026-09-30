plugins {
    alias(libs.plugins.mnemo.desktop.application)
}

// The desktop launcher (docs/desktop/ROADMAP.md, ADR 0010). In D1 it only shows the shared JVM
// modules (:core:model, :core:scheduler) in a window; the rest of the app joins as the modules
// become Kotlin Multiplatform (D2–D6).
compose.desktop {
    application {
        mainClass = "com.yahyafati.mnemo.desktop.MainKt"
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.scheduler)

    implementation(libs.cmp.runtime)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.ui)
    implementation(libs.cmp.material3)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.cmp.ui.test)
    testImplementation(libs.kotlinx.coroutines.test)
    // The native libraries the desktop app needs, loaded on every OS by NativeLibrariesTest (D0's
    // open check): SQLite for Room, zstd for Anki packages, Skia for drawing.
    testImplementation(libs.androidx.sqlite)
    testImplementation(libs.androidx.sqlite.bundled)
    testImplementation(libs.zstd.kmp.okio)
    testImplementation(libs.okio)
}
