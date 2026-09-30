import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `mnemo.kmp.compose`: Compose Multiplatform for a `mnemo.kmp.library` module: the JetBrains
 * plugin (for `composeResources`), the Kotlin compiler plugin with `compose_stability.conf`, and
 * the runtime, foundation and Material3 artifacts.
 *
 * Dependencies use the catalog coordinates, not the deprecated `compose.runtime` accessors.
 * Material3 is pinned to 1.9.0 (ADR 0010, finding 3). On Android the JetBrains artifacts resolve to
 * the androidx ones, and the Compose BOM keeps them at the versions `:app` uses today.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(pluginManager.hasPlugin(libs.pluginId("kotlin-multiplatform"))) {
                "mnemo.kmp.compose needs mnemo.kmp.library first"
            }
            pluginManager.apply(libs.pluginId("compose-multiplatform"))
            pluginManager.apply(libs.pluginId("kotlin-compose"))

            // Domain models from the JVM modules are immutable: see compose_stability.conf.
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))
            }

            val composeDependencies = extensions.getByType<ComposeExtension>().dependencies

            extensions.getByType<KotlinMultiplatformExtension>().sourceSets.apply {
                getByName("commonMain").dependencies {
                    implementation(libs.library("cmp-runtime"))
                    implementation(libs.library("cmp-foundation"))
                    implementation(libs.library("cmp-ui"))
                    implementation(libs.library("cmp-material3"))
                    implementation(libs.library("cmp-resources"))
                    implementation(libs.library("cmp-ui-tooling-preview"))
                }
                getByName("androidMain").dependencies {
                    implementation(project.dependencies.platform(libs.library("androidx-compose-bom")))
                }
                getByName("androidHostTest").dependencies {
                    implementation(project.dependencies.platform(libs.library("androidx-compose-bom")))
                    implementation(libs.library("androidx-compose-ui-test-junit4"))
                    implementation(libs.library("androidx-compose-ui-test-manifest"))
                    implementation(libs.library("robolectric"))
                }
                getByName("desktopTest").dependencies {
                    implementation(libs.library("cmp-ui-test"))
                    // Skia's native runtime for this OS: a library gets it from the app, tests need it.
                    implementation(composeDependencies.desktop.currentOs)
                }
            }
        }
    }
}
