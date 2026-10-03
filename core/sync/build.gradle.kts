plugins {
    alias(libs.plugins.mnemo.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// The sync format and its stores (docs/sync/ROADMAP.md S2, ADR 0013). Pure JVM like `:core:ai`: no Room, no
// Android. It knows the remote layout, the change files, encryption and where files are kept, and nothing about
// the collection; `:core:data` (S3) turns rows into changes. Google Drive's REST store and OAuth client (S6) live
// here too, on OkHttp, with every URL a constructor parameter so tests use MockWebServer.
dependencies {
    api(projects.core.common)
    // The Google Drive store and its sign-in (S6) are the only network code here; they never follow redirects.
    api(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okio)
    implementation(libs.zstd.kmp.okio)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
