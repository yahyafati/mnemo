plugins {
    alias(libs.plugins.mnemo.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

// The OpenAI-compatible client (ARCHITECTURE §5.2, §5.3). Pure JVM; only `:core:data` uses it.
dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
