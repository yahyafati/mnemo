import androidx.room.gradle.RoomExtension
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * `mnemo.kmp.room`: Room for a `mnemo.kmp.library` module, through KSP on both targets. Schemas are
 * exported to `<module>/schemas` and committed (same layout as `mnemo.android.room`), so every
 * migration can be tested against them on both targets (ARCHITECTURE §6).
 *
 * The driver is chosen where the database is built: the framework driver on Android (no new native
 * library), the bundled one on desktop (ADR 0010).
 */
class KmpRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(pluginManager.hasPlugin(libs.pluginId("kotlin-multiplatform"))) {
                "mnemo.kmp.room needs mnemo.kmp.library first"
            }
            pluginManager.apply(libs.pluginId("room"))
            pluginManager.apply(libs.pluginId("ksp"))

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }

            // Android's MigrationTestHelper reads the exported schemas as assets. Host (Robolectric)
            // tests need them there too, so migrations are checked without a device.
            extensions.getByType<KotlinMultiplatformAndroidComponentsExtension>().onVariants { variant ->
                variant.hostTests.values.forEach { hostTest ->
                    hostTest.sources.assets?.addStaticSourceDirectory("$projectDir/schemas")
                }
            }

            extensions.getByType<KotlinMultiplatformExtension>().sourceSets.apply {
                getByName("commonMain").dependencies {
                    api(libs.library("androidx-room-runtime"))
                }
                getByName("androidHostTest").dependencies {
                    implementation(libs.library("androidx-room-testing"))
                }
                getByName("desktopMain").dependencies {
                    implementation(libs.library("androidx-sqlite-bundled"))
                }
                getByName("desktopTest").dependencies {
                    implementation(libs.library("androidx-room-testing"))
                }
            }

            dependencies {
                add("kspAndroid", libs.library("androidx-room-compiler"))
                add("kspDesktop", libs.library("androidx-room-compiler"))
            }
        }
    }
}
