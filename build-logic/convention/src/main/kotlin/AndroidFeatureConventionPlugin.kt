import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `mnemo.android.feature`: library + Compose + Koin + serialization, plus the core modules every
 * feature uses (design system, shared UI, and the domain layer with its repositories). Features depend on `core` only, never on each other (ARCHITECTURE §3).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("mnemo-android-library"))
            pluginManager.apply(libs.pluginId("mnemo-android-compose"))
            pluginManager.apply(libs.pluginId("kotlin-serialization"))
            // Screenshot tests of each screen: baselines in src/test/screenshots, checked by
            // verifyRoborazziDebug, re-recorded by recordRoborazziDebug after an intended change.
            pluginManager.apply(libs.pluginId("roborazzi"))

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))
                add("implementation", libs.library("koin-core"))
                add("implementation", libs.library("koin-compose"))
                add("implementation", libs.library("koin-compose-viewmodel"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.library("kotlinx-coroutines-test"))
                add("testImplementation", libs.library("turbine"))
                // Compose UI tests run on the JVM with Robolectric, inside `testDebugUnitTest`.
                add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("roborazzi"))
                add("testImplementation", libs.library("roborazzi-compose"))
                add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            }
        }
    }
}
