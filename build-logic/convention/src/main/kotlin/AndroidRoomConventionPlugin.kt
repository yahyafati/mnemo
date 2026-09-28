import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.LibraryExtension
import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `mnemo.android.room`: Room through KSP. Schemas are exported to `<module>/schemas` and committed,
 * so every migration can be tested with `MigrationTestHelper` (ARCHITECTURE §6).
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("room"))
            pluginManager.apply(libs.pluginId("ksp"))

            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            // MigrationTestHelper reads the exported schemas as assets. Robolectric unit tests
            // need them too, so migrations are checked by `testDebugUnitTest` without a device.
            extensions.configure<LibraryExtension> {
                sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
                testOptions.unitTests.isIncludeAndroidResources = true
            }

            dependencies {
                add("implementation", libs.library("androidx-room-runtime"))
                add("implementation", libs.library("androidx-room-ktx"))
                add("ksp", libs.library("androidx-room-compiler"))
                add("androidTestImplementation", libs.library("androidx-room-testing"))
            }
        }
    }
}
