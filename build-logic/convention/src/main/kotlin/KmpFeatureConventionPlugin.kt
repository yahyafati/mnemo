import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `mnemo.kmp.feature`: the KMP twin of `mnemo.android.feature` (D6): library + Compose
 * Multiplatform + serialization + the core modules every feature uses. Features depend on `core`
 * only, never on each other (ARCHITECTURE §3).
 *
 * Koin replaces Hilt (D2); ViewModels, navigation and lifecycle come from the JetBrains artifacts.
 * Apply it only once `:core:designsystem`, `:core:domain` and `:core:ui` are KMP modules (D4, D5):
 * their dependencies sit in `commonMain`, so they must have a desktop variant.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("mnemo-kmp-library"))
            pluginManager.apply(libs.pluginId("mnemo-kmp-compose"))
            pluginManager.apply(libs.pluginId("kotlin-serialization"))
            // Screenshot tests: verifyRoborazziAndroidHostTest / verifyRoborazziDesktop.
            pluginManager.apply(libs.pluginId("roborazzi"))

            extensions.getByType<KotlinMultiplatformExtension>().sourceSets.apply {
                getByName("commonMain").dependencies {
                    implementation(project(":core:designsystem"))
                    implementation(project(":core:domain"))
                    implementation(project(":core:ui"))

                    implementation(libs.library("jetbrains-navigation-compose"))
                    implementation(libs.library("jetbrains-lifecycle-viewmodel-compose"))
                    implementation(libs.library("koin-core"))
                    implementation(libs.library("koin-compose"))
                    implementation(libs.library("koin-compose-viewmodel"))
                    implementation(libs.library("kotlinx-serialization-json"))
                }
                getByName("commonTest").dependencies {
                    implementation(project(":core:testing"))
                    implementation(libs.library("kotlinx-coroutines-test"))
                    implementation(libs.library("turbine"))
                }
                getByName("androidHostTest").dependencies {
                    implementation(libs.library("roborazzi"))
                    implementation(libs.library("roborazzi-compose"))
                }
                getByName("desktopTest").dependencies {
                    implementation(libs.library("roborazzi-compose-desktop"))
                }
            }
        }
    }
}
