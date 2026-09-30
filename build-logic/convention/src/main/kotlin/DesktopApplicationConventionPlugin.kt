import com.yahyafati.mnemo.buildlogic.configureKotlinJvm
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
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
 * Android app (ADR 0010). The module sets `mainClass`.
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

            extensions.configure<ComposeExtension> {
                (this as ExtensionAware).extensions.configure<DesktopExtension> {
                    application {
                        // Skia and SQLite load native libraries; JDK 24+ warns unless this is set.
                        jvmArgs("--enable-native-access=ALL-UNNAMED")
                        nativeDistributions {
                            packageName = "Mnemo"
                            packageVersion = versionName
                            // On top of the defaults (java.base, java.desktop …). Enough for what the
                            // app will need: TLS for AI providers, SQL types, DNS. D8 trims and checks it.
                            modules(
                                "java.management",
                                "java.naming",
                                "java.net.http",
                                "java.sql",
                                "jdk.crypto.ec",
                                "jdk.unsupported",
                            )
                        }
                    }
                }
            }

            val packagingJdk = extensions.getByType<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(PACKAGING_JDK))
            }
            // After the JetBrains plugin has created its tasks and set their own javaHome.
            afterEvaluate {
                tasks.withType<AbstractJvmToolOperationTask>().configureEach {
                    javaHome.set(packagingJdk.map { it.metadata.installationPath.asFile.absolutePath })
                }
            }

            dependencies {
                add("testImplementation", libs.library("junit"))
                add("testImplementation", libs.library("kotlin-test"))
            }
        }
    }
}

/** The JDK that runs `jlink` and `jpackage`: a release with `jmods`, and the runtime of the installers. */
private const val PACKAGING_JDK = 21
