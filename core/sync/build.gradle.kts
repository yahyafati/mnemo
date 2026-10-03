plugins {
    alias(libs.plugins.mnemo.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// The sync format and its stores (docs/sync/ROADMAP.md S2, ADR 0013). Pure JVM like `:core:ai`: no Room, no
// Android. It knows the remote layout, the change files, encryption and where files are kept, and nothing about
// the collection; `:core:data` (S3) turns rows into changes.
dependencies {
    api(projects.core.common)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    implementation(libs.zstd.kmp.okio)
}
