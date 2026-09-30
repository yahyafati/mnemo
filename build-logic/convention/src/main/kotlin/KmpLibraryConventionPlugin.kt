import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.yahyafati.mnemo.buildlogic.COMPILE_SDK
import com.yahyafati.mnemo.buildlogic.JVM_TARGET
import com.yahyafati.mnemo.buildlogic.MIN_SDK
import com.yahyafati.mnemo.buildlogic.TEST_JVM_ARGS
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.mnemoNamespace
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `mnemo.kmp.library`: a shared module with two targets, Android (AGP 9's
 * `com.android.kotlin.multiplatform.library`) and `desktop` (JVM), as ADR 0010 decided.
 *
 * Source sets: `commonMain`, `androidMain`, `desktopMain`; tests in `commonTest`,
 * `androidHostTest` (Robolectric, with Android resources) and `desktopTest`. The namespace follows
 * the module path (`:core:designsystem` → `com.yahyafati.mnemo.core.designsystem`). A KMP module has no `testDebugUnitTest`: its
 * tests are `testAndroidHostTest` and `desktopTest` (ADR 0010, finding 4).
 *
 * Pure JVM modules (`:core:model` …) stay JVM and can be used from `commonMain`: Kotlin treats a
 * source set shared by JVM and Android targets only as JVM code.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
            pluginManager.apply(libs.pluginId("android-kotlin-multiplatform-library"))

            val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
            (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = mnemoNamespace
                compileSdk {
                    version = release(COMPILE_SDK)
                }
                minSdk = MIN_SDK
                compilerOptions.jvmTarget.set(JVM_TARGET)
                // Robolectric tests need merged resources (strings, fonts).
                withHostTest {
                    isIncludeAndroidResources = true
                }
            }

            kotlin.apply {
                jvm("desktop") {
                    compilerOptions.jvmTarget.set(JVM_TARGET)
                }
                sourceSets.apply {
                    getByName("commonTest").dependencies {
                        implementation(libs.library("kotlin-test"))
                    }
                    getByName("androidHostTest").dependencies {
                        implementation(libs.library("junit"))
                    }
                    getByName("desktopTest").dependencies {
                        implementation(libs.library("junit"))
                    }
                }
            }

            // Roborazzi's two finalize tasks (one per target) copy their results into the same report
            // folder; run in parallel they fail with "Source file wasn't copied completely".
            val androidFinalize = tasks.matching { it.name == "finalizeTestRoborazziAndroidHostTest" }
            tasks.matching { it.name == "finalizeTestRoborazziDesktop" }.configureEach {
                mustRunAfter(androidFinalize)
            }

            tasks.withType<Test>().configureEach {
                failOnNoDiscoveredTests.set(false)
                jvmArgs(TEST_JVM_ARGS)
            }
        }
    }
}
