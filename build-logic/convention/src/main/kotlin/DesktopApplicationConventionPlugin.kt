import com.yahyafati.mnemo.buildlogic.configureKotlinJvm
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.configure
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractCheckNativeDistributionRuntime
import org.jetbrains.compose.desktop.application.tasks.AbstractJvmToolOperationTask

/**
 * `mnemo.desktop.application`: the `:desktop` launcher. A plain Kotlin/JVM module with Compose
 * Multiplatform's desktop application support; `run`, `createDistributable` and the installer
 * tasks come from the JetBrains plugin.
 *
 * `jlink` and `jpackage` run on a JDK 21 toolchain, not on the JDK Gradle itself runs on (25, see
 * `gradle-daemon-jvm.properties`): that build has no `jmods`, and `jlink` refuses it. CI's
 * `setup-java` Temurin 21 has them. The lookup is lazy, so builds that never package (`:app`,
 * F-Droid) don't need the toolchain.
 *
 * The package version is `mnemo.versionName` from `gradle.properties`, the same number as the
 * Android app (ADR 0010). The module sets `mainClass`. The installers (D8): `.dmg` on macOS, `.msi`
 * on Windows, `.deb` and `.rpm` on Linux; the tasks of the other systems' formats aren't created.
 */
class DesktopApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-jvm"))
            pluginManager.apply(libs.pluginId("compose-multiplatform"))
            pluginManager.apply(libs.pluginId("kotlin-compose"))
            configureKotlinJvm()

            val versionName = providers.gradleProperty("mnemo.versionName").orNull
                ?: error("gradle.properties: mnemo.versionName is missing")

            // LICENSE and NOTICE go into the app (`resources/` next to its jars), as the GPL asks of a binary.
            val appResources = layout.buildDirectory.dir("generated/appResources")
            val copyLicenseTexts = tasks.register<Sync>("copyLicenseTexts") {
                from(rootProject.layout.projectDirectory.file("LICENSE"), rootProject.layout.projectDirectory.file("NOTICE"))
                into(appResources.map { it.dir("common") })
            }

            extensions.configure<ComposeExtension> {
                (this as ExtensionAware).extensions.configure<DesktopExtension> {
                    application {
                        // Skia and SQLite load native libraries; JDK 24+ warns unless this is set.
                        jvmArgs("--enable-native-access=ALL-UNNAMED")
                        nativeDistributions {
                            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
                            packageName = "Mnemo"
                            packageVersion = versionName
                            description = "Local-first spaced repetition with FSRS"
                            vendor = "Yahya Fati"
                            copyright = "Copyright (C) 2026 Yahya Fati, GPL-3.0-or-later"
                            // Not `licenseFile`: the installers would make people "accept" the GPL to install, and
                            // the GPL is not an agreement to accept. The text ships as an app resource instead.
                            appResourcesRootDir.set(appResources)
                            // On top of the defaults (java.base, java.desktop …). A trimmed runtime: the
                            // JDK's own modules the app reaches, found by `:desktop:suggestRuntimeModules`
                            // (java.instrument, java.net.http, jdk.security.auth, jdk.unsupported) plus
                            // what jdeps can't see because it is loaded by name or service: TLS and the
                            // elliptic curves for AI providers and links (jdk.crypto.ec), the charsets of
                            // web pages that aren't UTF-8 (jdk.charsets), locale data for dates and
                            // numbers (jdk.localedata), and the management, naming and SQL APIs that
                            // libraries probe for. Re-run the suggestion after a dependency upgrade.
                            modules(
                                "java.instrument",
                                "java.management",
                                "java.naming",
                                "java.net.http",
                                "java.sql",
                                "jdk.charsets",
                                "jdk.crypto.ec",
                                "jdk.localedata",
                                "jdk.security.auth",
                                "jdk.unsupported",
                            )
                            // `.apkg` and `.colpkg` open in Mnemo. macOS hands the file to the running app
                            // (DesktopIntegration), Windows and Linux start it with the path as an argument.
                            fileAssociation(
                                "application/x-anki-apkg", "apkg", "Anki deck package",
                                target.file("icons/mnemo-package.png"), target.file("icons/mnemo-package.ico"), target.file("icons/mnemo-package.icns"),
                            )
                            fileAssociation(
                                "application/x-anki-colpkg", "colpkg", "Anki collection package",
                                target.file("icons/mnemo-package.png"), target.file("icons/mnemo-package.ico"), target.file("icons/mnemo-package.icns"),
                            )

                            linux {
                                iconFile.set(target.file("icons/mnemo.png"))
                                // Debian package names are lower case.
                                packageName = "mnemo"
                                debMaintainer = "Yahya Fati <yfati037@gmail.com>"
                                menuGroup = "Education"
                                appCategory = "Education"
                                rpmLicenseType = "GPL-3.0-or-later"
                                shortcut = true
                            }
                            windows {
                                iconFile.set(target.file("icons/mnemo.ico"))
                                // Per user: no administrator prompt, and the data is per user anyway. The
                                // upgrade UUID is how Windows knows a newer MSI replaces this one: it must
                                // never change, or every release would install next to the last.
                                perUserInstall = true
                                // Under %LOCALAPPDATA%\Programs, where per-user programs go. The default,
                                // %LOCALAPPDATA%\Mnemo, is where the collection lives (DesktopAppDirectories).
                                installationPath = "Programs\\Mnemo"
                                menu = true
                                shortcut = true
                                menuGroup = "Mnemo"
                                upgradeUuid = WINDOWS_UPGRADE_UUID
                            }
                            macOS {
                                iconFile.set(target.file("icons/mnemo.icns"))
                                bundleID = "com.yahyafati.mnemo.desktop"
                                dockName = "Mnemo"
                                appCategory = "public.app-category.education"
                                minimumSystemVersion = "12.0"
                            }
                        }
                    }
                }
            }

            val packagingJdk = extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(PACKAGING_JDK))
            }
            // After the JetBrains plugin has created its tasks and set their own javaHome.
            afterEvaluate {
                // The plugin copies `appResourcesRootDir` into the app with this task.
                tasks.named("prepareAppResources") { dependsOn(copyLicenseTexts) }
                tasks.withType<AbstractJvmToolOperationTask>().configureEach {
                    javaHome.set(packagingJdk.map { it.metadata.installationPath.asFile.absolutePath })
                }
                // `checkRuntime` looks for `jpackage` in the same JDK.
                tasks.withType<AbstractCheckNativeDistributionRuntime>().configureEach {
                    jdkHome.set(packagingJdk.map { it.metadata.installationPath.asFile.absolutePath })
                }
            }

            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlin-test"))
            }
        }
    }
}

/**
 * Windows' key for "this MSI replaces that one". Generated once, and it must never change: an upgrade
 * of an installed Mnemo finds its predecessor by it (desktop ROADMAP D8).
 */
private const val WINDOWS_UPGRADE_UUID = "a1a68559-dc4a-4cae-b01c-2658a81e35d0"

/** The JDK that runs `jlink` and `jpackage`: a release with `jmods`, and the runtime of the installers. */
private const val PACKAGING_JDK = 21
