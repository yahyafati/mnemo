import com.yahyafati.mnemo.buildlogic.libs
import com.yahyafati.mnemo.buildlogic.library
import com.yahyafati.mnemo.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `mnemo.hilt`: Hilt through KSP.
 *
 * Pure JVM modules get `hilt-core` (annotations and `@Module`s only). Android modules also get the
 * Hilt Gradle plugin and `hilt-android`.
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("ksp"))
            dependencies {
                add("ksp", libs.library("hilt-compiler"))
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.library("hilt-core"))
                }
            }

            listOf("com.android.application", "com.android.library").forEach { id ->
                pluginManager.withPlugin(id) { configureHiltAndroid() }
            }
        }
    }

    private fun Project.configureHiltAndroid() {
        pluginManager.apply(libs.pluginId("hilt"))
        dependencies {
            add("implementation", libs.library("hilt-android"))
            add("kspTest", libs.library("hilt-compiler"))
            add("kspAndroidTest", libs.library("hilt-compiler"))
        }
    }
}
