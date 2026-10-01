import java.util.regex.Pattern

plugins {
    alias(libs.plugins.mnemo.desktop.application)
    alias(libs.plugins.aboutlibraries.jvm)
}

// The desktop launcher (docs/desktop/ROADMAP.md, ADR 0010). It starts the data layer (D4): the
// collection's directory and its single-instance lock, a staged restore, and the Koin graph. The
// window shows the app shell (D6), the same UI as the Android app.
compose.desktop {
    application {
        mainClass = "com.yahyafati.mnemo.desktop.MainKt"
    }
}

// The portable Linux build: the app image (the launcher and its Java runtime, no installer) as a
// .tar.gz that unpacks into `Mnemo/`. The JetBrains plugin has no format for it; only Linux needs one,
// because the other systems' installers already are a single file to download (desktop ROADMAP D8).
tasks.register<Tar>("packagePortable") {
    group = "compose desktop"
    description = "Packs the Linux app image as Mnemo-<version>-linux-<arch>.tar.gz."
    val architecture = System.getProperty("os.arch").let { if (it == "amd64") "x64" else it }
    archiveBaseName = "Mnemo"
    archiveVersion = providers.gradleProperty("mnemo.versionName")
    archiveClassifier = "linux-$architecture"
    compression = Compression.GZIP
    archiveExtension = "tar.gz" // Gradle's default for GZIP is "tgz"; the release workflow and the page expect .tar.gz
    destinationDirectory = layout.buildDirectory.dir("compose/binaries/main/portable")
    dependsOn("createDistributable")
    from(layout.buildDirectory.dir("compose/binaries/main/app")) { include("Mnemo/**") }
    // A tar made on another system would hold nothing: fail loudly instead of writing an empty file.
    doFirst { check(System.getProperty("os.name").startsWith("Linux")) { "packagePortable is for Linux; build it on Linux" } }
}

// The open-source licenses screen (Settings > About) reads the JSON this plugin writes from the
// desktop runtime classpath. It is the list of `:app` (the bundled fonts and py-fsrs, the license
// texts: `app/config`) without KaTeX, which only the Android app bundles, plus what only the
// desktop bundles (`config`: the Java runtime). Offline: the build never calls the network.
val licenseConfig = layout.buildDirectory.dir("licenseConfig")
val prepareLicenseConfig = tasks.register<Sync>("prepareLicenseConfig") {
    from(rootProject.layout.projectDirectory.dir("app/config")) { exclude("libraries/katex.json") }
    from(layout.projectDirectory.dir("config"))
    into(licenseConfig)
}
val licensesOutput = layout.buildDirectory.dir("generated/licenses")
aboutLibraries {
    offlineMode = true
    collect {
        configPath = licenseConfig.get().asFile
    }
    library {
        // Not shipped: JLayer's test-only JUnit 3.8 (excluded from the runtime classpath below).
        exclusionPatterns.add(Pattern.compile("junit:junit"))
    }
    export {
        outputFile = licensesOutput.get().file("aboutlibraries.json").asFile
    }
}
tasks.named("exportLibraryDefinitions") {
    dependsOn(prepareLicenseConfig)
}
sourceSets.main {
    resources.srcDir(tasks.named("exportLibraryDefinitions").map { licensesOutput })
}
// The version the About section shows: `mnemo.versionName`, the same number as the Android app.
val versionDirectory = layout.buildDirectory.dir("generated/version")
val generateVersionProperties = tasks.register<WriteProperties>("generateVersionProperties") {
    destinationFile = versionDirectory.get().file("mnemo-version.properties")
    property("version", providers.gradleProperty("mnemo.versionName"))
}
sourceSets.main {
    resources.srcDir(generateVersionProperties.map { versionDirectory })
}

// JLayer (MP3) lists JUnit 3.8 as a dependency; it isn't used at run time.
configurations.runtimeClasspath {
    exclude(group = "junit", module = "junit")
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.ui)
    implementation(projects.shell)
    implementation(projects.feature.analytics)
    implementation(projects.feature.browse)
    implementation(projects.feature.create)
    implementation(projects.feature.decks)
    implementation(projects.feature.settings)
    implementation(projects.feature.study)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.cmp.runtime)
    implementation(libs.cmp.foundation)
    implementation(libs.cmp.ui)
    implementation(libs.cmp.material3)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
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
