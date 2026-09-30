plugins {
    alias(libs.plugins.mnemo.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // The reminder test reads its notification text from this module's resources.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    api(libs.androidx.paging.common)
    implementation(projects.core.ai)
    implementation(projects.core.anki)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.ingest)
    implementation(projects.core.scheduler)
    implementation(projects.core.security)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
}

// Robolectric runs on the host JVM, where the Android zstd natives can't load. Extract the
// host's native library from the JVM artifact and put it on the test library path.
val zstdNatives: Configuration by configurations.creating { isTransitive = false }
dependencies { zstdNatives(libs.zstd.kmp.jvm) }

val hostArch: String = when (val arch = System.getProperty("os.arch")) {
    "x86_64", "amd64" -> if (System.getProperty("os.name").startsWith("Mac")) "x86_64" else "amd64"
    else -> arch
}
val extractZstdNatives by tasks.registering(Sync::class) {
    from({ zipTree(zstdNatives.singleFile) }) {
        include("jni/$hostArch/*")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("zstd-natives"))
}
tasks.withType<Test>().configureEach {
    dependsOn(extractZstdNatives)
    val natives = layout.buildDirectory.dir("zstd-natives")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.library.path=${natives.get().asFile.absolutePath}") })
}
