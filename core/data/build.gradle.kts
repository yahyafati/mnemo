plugins {
    alias(libs.plugins.mnemo.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        // Notification texts and icons of the WorkManager workers.
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
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
            implementation(projects.core.sync)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.documentfile)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.androidx.work.runtime)
        }
        desktopMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okhttp.mockwebserver)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.androidx.junit)
            implementation(libs.androidx.paging.testing)
            implementation(libs.androidx.work.testing)
            implementation(libs.robolectric)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.androidx.paging.testing)
        }
    }
}

// Robolectric runs on the host JVM, where the Android zstd natives can't load. Extract the
// host's native library from the JVM artifact and put it on the Android host tests' library path.
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
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    dependsOn(extractZstdNatives)
    val natives = layout.buildDirectory.dir("zstd-natives")
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Djava.library.path=${natives.get().asFile.absolutePath}") })
}
